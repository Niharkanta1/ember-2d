package dev.ember2d.save;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ember2d.app.EngineConfig;
import dev.ember2d.app.EngineContext;
import dev.ember2d.event.internal.DefaultEventBus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** World flags (F-013, I-430). */
class WorldFlagsTest {

    final DefaultEventBus events = new DefaultEventBus(false);
    final WorldFlags flags = new WorldFlags(events);
    final List<String> changed = new ArrayList<>();

    WorldFlagsTest() {
        events.onGlobal(FlagChanged.class, e -> changed.add(e.key()));
    }

    @Test
    void typedValuesWithDefaultsAndChangeEvents() {
        flags.set("chest.town.opened", true);
        flags.set("npc.elder.talked", 2);
        flags.set("door.cellar", "open");
        assertThat(flags.getBool("chest.town.opened", false)).isTrue();
        assertThat(flags.getInt("npc.elder.talked", 0)).isEqualTo(2);
        assertThat(flags.getString("door.cellar", "")).isEqualTo("open");
        assertThat(flags.getInt("chest.town.opened", -1)).as("another type: the default").isEqualTo(-1);
        assertThat(flags.getBool("missing", true)).isTrue();

        flags.set("chest.town.opened", true);   // same value: no event
        assertThat(flags.toggle("lever")).isTrue();
        assertThat(flags.add("npc.elder.talked", 3)).isEqualTo(5);
        assertThat(flags.remove("door.cellar")).isTrue();
        assertThat(flags.remove("door.cellar")).isFalse();
        assertThat(changed).containsExactly("chest.town.opened", "npc.elder.talked", "door.cellar", "lever",
                "npc.elder.talked", "door.cellar");
        assertThat(flags.keys()).containsExactly("chest.town.opened", "npc.elder.talked", "lever");
        assertThatThrownBy(() -> flags.set(" ", true)).hasMessage("A flag needs a non-empty key");
        assertThatThrownBy(() -> flags.set("x", (String) null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flagsRoundTripThroughASaveSlotAndLoadReplacesThem() {
        flags.set("a", true);
        flags.set("b", false);
        flags.set("c", 7);
        flags.set("d", "text");
        SaveData slot = new SaveData();
        flags.save(slot);

        WorldFlags back = new WorldFlags(null);
        back.set("stale", 1);
        back.load(SaveData.parse(slot.toJson()));
        assertThat(back.keys()).containsExactly("a", "b", "c", "d");
        assertThat(back.getBool("a", false)).isTrue();
        assertThat(back.getBool("b", true)).isFalse();
        assertThat(back.getInt("c", 0)).isEqualTo(7);
        assertThat(back.getString("d", "")).isEqualTo("text");

        back.load(new SaveData());
        assertThat(back.keys()).as("a slot without flags clears them").isEmpty();
        changed.clear();
        flags.clear();
        assertThat(changed).as("a new game publishes nothing").isEmpty();
    }

    @Test
    void theContextHasOneSetOfFlags() {
        EngineContext.Builder builder = new EngineContext.Builder(new EngineConfig());
        builder.flags().set("preset", true);
        EngineContext context = builder.build();
        assertThat(context.flags()).isSameAs(builder.flags());
        assertThat(context.flags().getBool("preset", false)).isTrue();
    }
}
