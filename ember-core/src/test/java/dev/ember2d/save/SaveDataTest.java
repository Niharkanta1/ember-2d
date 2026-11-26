package dev.ember2d.save;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The save tree (F-013, I-230): values, JSON round trip, codecs and parse errors. */
class SaveDataTest {

    record Hero(String name, int level, float hp) {
    }

    static final SaveCodec<Hero> HERO = new SaveCodec<>() {
        @Override
        public void write(Hero h, SaveData out) {
            out.set("name", h.name()).set("level", h.level()).set("hp", h.hp());
        }

        @Override
        public Hero read(SaveData in) {
            return new Hero(in.getString("name", "?"), in.getInt("level", 1), in.getFloat("hp", 0f));
        }
    };

    @Test
    void everyKindOfValueSurvivesJson() {
        SaveData d = new SaveData().set("on", true).set("coins", 42).set("speed", 1.5f).set("whole", 3f)
                .set("name", "Frög \"the\" \\ hero\n");
        d.child("stats").set("deaths", 7).child("deep").set("x", -2);
        d.list("items").add(new SaveData().set("id", "key"));
        d.list("items").add(new SaveData());
        d.list("empty");
        d.put("hero", new Hero("Ann", 3, 12.5f), HERO);

        SaveData back = SaveData.parse(d.toJson());
        assertThat(back.toJson()).isEqualTo(d.toJson());
        assertThat(back.getBool("on", false)).isTrue();
        assertThat(back.getInt("coins", 0)).isEqualTo(42);
        assertThat(back.getFloat("speed", 0f)).isEqualTo(1.5f);
        assertThat(back.getFloat("whole", 0f)).as("3.0 stays a float").isEqualTo(3f);
        assertThat(back.getString("name", "")).isEqualTo("Frög \"the\" \\ hero\n");
        assertThat(back.child("stats").child("deep").getInt("x", 0)).isEqualTo(-2);
        assertThat(back.list("items")).hasSize(2);
        assertThat(back.list("items").get(0).getString("id", "")).isEqualTo("key");
        assertThat(back.get("hero", HERO, null)).isEqualTo(new Hero("Ann", 3, 12.5f));
        assertThat(back.keys()).containsExactly("on", "coins", "speed", "whole", "name", "stats", "items", "empty",
                "hero");
    }

    @Test
    void gettersFallBackAndConvertNumbers() {
        SaveData d = new SaveData().set("i", 2).set("f", 2.6f).set("s", "x");
        assertThat(d.getFloat("i", 0f)).isEqualTo(2f);
        assertThat(d.getInt("f", 0)).isEqualTo(3);
        assertThat(d.getInt("s", -1)).isEqualTo(-1);
        assertThat(d.getBool("missing", true)).isTrue();
        assertThat(d.getString("i", "def")).isEqualTo("def");
        assertThat(d.get("s", HERO, null)).isNull();
        assertThat(d.remove("s")).isTrue();
        assertThat(d.has("s")).isFalse();
        assertThatThrownBy(() -> d.child("i")).isInstanceOf(IllegalStateException.class)
                .hasMessage("i holds a number, not an object");
        assertThatThrownBy(() -> d.set("nan", Float.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> d.set("", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setWithATreeStoresACopy() {
        SaveData inner = new SaveData().set("a", 1);
        SaveData d = new SaveData().set("inner", inner);
        inner.set("a", 2);
        assertThat(d.child("inner").getInt("a", 0)).isEqualTo(1);
    }

    @Test
    void badJsonIsReportedWithTheReason() {
        assertThatThrownBy(() -> SaveData.parse("{\"a\": ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("not valid JSON");
        assertThatThrownBy(() -> SaveData.parse("[1]")).hasMessage("the root must be a JSON object");
        assertThatThrownBy(() -> SaveData.parse("{\"l\": [1]}")).hasMessage("l[0]: lists hold objects only");
        assertThatThrownBy(() -> SaveData.parse("{\"o\": {\"n\": null}}")).hasMessage("o.n: null is not supported");
        assertThat(SaveData.parse("{\"big\": 5000000000}").getFloat("big", 0f)).isEqualTo(5.0e9f);
    }
}
