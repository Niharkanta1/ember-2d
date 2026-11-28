package dev.ember2d.demo.gold;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.kit.bullets.BulletType;
import dev.ember2d.kit.bullets.BulletTypes;
import dev.ember2d.kit.topdown.shooter.WeaponDef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Gold in Shadows' data matches the original game's values (F-029). Runs from {@code core/}, reading {@code assets/}. */
class GoldDataTest {

    static final Path ASSETS = Path.of("assets");

    static JsonValue json(String path) throws IOException {
        return new JsonReader().parse(Files.readString(ASSETS.resolve(path), StandardCharsets.UTF_8));
    }

    @Test
    void theRifleIsTheOriginals() throws IOException {
        BulletTypes types = BulletTypes.parse(json("bullets.json"), "bullets.json");
        WeaponDef rifle = WeaponDef.parse("weapon.rifle", json("weapons/rifle.json"), "rifle.json", types);
        assertThat(rifle.fireRate).as("600 rounds a minute").isEqualTo(10f);
        assertThat(rifle.auto).isTrue();
        assertThat(rifle.magazine).isEqualTo(30);
        assertThat(rifle.reload).isEqualTo(2f);
        BulletType bullet = types.get(types.index("rifle"));
        assertThat(bullet.damage).isEqualTo(1);
        assertThat(bullet.walls).isTrue();
        assertThat(bullet.fogged).as("the player's own shots are always seen").isFalse();
        assertThat(types.get(types.index("bolt")).fogged).isTrue();
        assertThat(types.get(types.index("spit")).fogged).isTrue();
    }

    @Test
    void thePlayerMovesAndHurtsLikeTheOriginal() throws IOException {
        JsonValue c = json("prefabs/player.json").get("components");
        assertThat(c.get("TopDownBody").getFloat("maxSpeed")).as("50 px/s").isEqualTo(50f / 16f);
        assertThat(c.get("Health").getFloat("invulnerableSeconds")).isEqualTo(0.8f);
        assertThat(c.get("Collider").get("box").getFloat("width")).as("the 6 px radius").isEqualTo(12f / 16f);
    }

    @Test
    void difficultyMatchesTheOriginalInQuarterHearts() {
        assertThat(Difficulty.EASY.maxHealth / 4).isEqualTo(6);
        assertThat(Difficulty.NORMAL.maxHealth / 4).isEqualTo(5);
        assertThat(Difficulty.HARD.maxHealth / 4).isEqualTo(4);
        // one point of damage: a quarter heart (x0.5), half a heart (x1), three quarters (x1.5)
        assertThat(Difficulty.EASY.damageMultiplier).isEqualTo(1);
        assertThat(Difficulty.NORMAL.damageMultiplier).isEqualTo(2);
        assertThat(Difficulty.HARD.damageMultiplier).isEqualTo(3);
        assertThat(Difficulty.HARD.next()).isEqualTo(Difficulty.EASY);
    }

    @Test
    void heartsShowFullHalfAndEmpty() {
        assertThat(Hud.hearts(20, 20)).isEqualTo("{{{{{");
        assertThat(Hud.hearts(18, 20)).isEqualTo("{{{{~");
        assertThat(Hud.hearts(17, 20)).as("a quarter left of the last heart shows empty").isEqualTo("{{{{}");
        assertThat(Hud.hearts(0, 16)).isEqualTo("}}}}");
    }

    @Test
    void theHandsFollowTheOriginalTracks() {
        assertThat(HeroSystem.hands("idle", 0f)).containsExactly(-4f, 3f, 5f, 3f);
        assertThat(HeroSystem.hands("idle", 0.15f)[1]).isEqualTo(3.5f);
        assertThat(HeroSystem.hands("walk", 0.05f)).containsExactly(-5f, 3f, 6f, 3f);
        assertThat(HeroSystem.hands("walk", 0.25f)).containsExactly(-3f, 3.5f, 4f, 3.5f);
    }

    @Test
    void everyPrefabUsesRegisteredComponents() throws IOException {
        ComponentRegistry registry = new ComponentRegistry();
        new GoldComponents().register(registry);
        Set<String> known = new HashSet<>(registry.names());
        known.addAll(List.of("Transform", "Sprite", "Animator", "Collider", "Actor", "Solid"));   // engine built-ins
        try (Stream<Path> files = Files.walk(ASSETS.resolve("prefabs"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonValue components = new JsonReader().parse(Files.readString(file)).get("components");
                for (JsonValue c = components.child; c != null; c = c.next) {
                    assertThat(known).as(file + ": " + c.name).contains(c.name);
                }
            }
        }
    }

    @Test
    void creaturesDropTheOriginalsGold() throws IOException {
        assertThat(json("prefabs/enemy/rat.json").get("components").get("Creature").getInt("gold")).isEqualTo(5);
        assertThat(json("prefabs/enemy/slug.json").get("components").get("Creature").getInt("gold")).isEqualTo(15);
        assertThat(json("prefabs/enemy/ghoul.json").get("components").get("Creature").getInt("gold")).isEqualTo(20);
        assertThat(json("prefabs/enemy/cultist.json").get("components").get("Creature").getInt("gold")).isEqualTo(25);
    }
}
