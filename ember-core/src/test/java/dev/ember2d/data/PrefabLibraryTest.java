package dev.ember2d.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.data.TestComponents.Health;
import dev.ember2d.data.TestComponents.Kitchen;
import dev.ember2d.data.TestComponents.Mode;
import dev.ember2d.ecs.EntityRef;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.render.Sprite;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Component registry and JSON prefabs (F-010), with an in-memory asset service. */
class PrefabLibraryTest {

    /** Prefab JSON by ID; path = "prefabs/" + id + ".json". */
    static final class FakeAssets implements Assets {
        final Map<String, JsonValue> files = new HashMap<>();

        void put(String id, String json) {
            files.put(id, new JsonReader().parse(json));
        }

        @Override
        public <T> T get(String id, Class<T> type) {
            return type.cast(files.get(id));
        }

        @Override
        public TextureRegion region(String id) {
            return null;
        }

        @Override
        public AsyncLoad loadGroup(String group) {
            return null;
        }

        @Override
        public void unloadGroup(String group) {
        }

        @Override
        public boolean isLoaded(String id) {
            return files.containsKey(id);
        }

        @Override
        public boolean contains(String id) {
            return files.containsKey(id);
        }

        @Override
        public String path(String id) {
            return "prefabs/" + id + ".json";
        }
    }

    final ComponentRegistry registry = new ComponentRegistry();
    final FakeAssets assets = new FakeAssets();
    final DefaultEventBus events = new DefaultEventBus(false);
    final PrefabLibrary prefabs = new PrefabLibrary(registry, assets, events);
    final World world = new World(false);
    final List<EntitySpawned> spawned = new ArrayList<>();

    PrefabLibraryTest() {
        new TestComponents.Setup().register(registry);
        events.onGlobal(EntitySpawned.class, spawned::add);
    }

    @Test
    void registryListsComponentsAndTheirSettableFields() {
        assertThat(registry.names()).containsExactly("Transform", "Sprite", "Collider", "Actor", "Solid", "Kitchen",
                "Walker", "Mover", "Health", "Coin");
        assertThat(registry.types()).contains(Transform.class, Sprite.class, Kitchen.class);
        assertThat(registry.fields("Kitchen")).extracting(FieldInfo::name)
                .containsExactly("i", "l", "f", "d", "b", "s", "mode", "color", "tint", "v", "r", "path", "ref");
        assertThat(registry.fields("Kitchen").get(0)).isEqualTo(new FieldInfo("i", int.class, 1));
        assertThat(registry.fields("Sprite")).extracting(FieldInfo::name).doesNotContain("region")
                .contains("regionId", "tint", "origin", "layer", "z", "flipX");
        assertThatThrownBy(() -> registry.register(Health.class, Health::new))
                .hasMessageContaining("already registered");
        assertThatThrownBy(() -> registry.register("Other", Health.class, Health::new))
                .hasMessageContaining("already registered as \"Health\"");
        assertThatThrownBy(() -> registry.fields("Nope")).hasMessageContaining("Unknown component \"Nope\"");
    }

    @Test
    void spawnsEveryFieldTypeFromJson() {
        assets.put("kitchen", """
                { "version": 1, "components": {
                  "Kitchen": { "i": 7, "l": 9000000000, "f": 1.5, "d": 2.25, "b": true, "s": "hi", "mode": "ANGRY",
                    "color": "#ff000080", "tint": { "r": 0, "g": 1, "b": 0 }, "v": [3, 4],
                    "r": { "x": 1, "y": 2, "width": 3, "height": 4 }, "path": [0, 1, 2, 3] },
                  "Sprite": { "regionId": "tex.slime", "origin": { "x": 8, "y": 0 }, "tint": "#99E699", "z": 2 },
                  "Transform": { "rotation": 30, "scale": [2, 3] } } }
                """);

        int e = prefabs.spawn(world, "kitchen", 5f, 6f);

        Kitchen k = world.get(e, Kitchen.class);
        assertThat(k.i).isEqualTo(7);
        assertThat(k.l).isEqualTo(9_000_000_000L);
        assertThat(k.f).isEqualTo(1.5f);
        assertThat(k.d).isEqualTo(2.25);
        assertThat(k.b).isTrue();
        assertThat(k.s).isEqualTo("hi");
        assertThat(k.mode).isEqualTo(Mode.ANGRY);
        assertThat(k.color).isEqualTo(new Color(1f, 0f, 0f, 128 / 255f));
        assertThat(k.tint).isEqualTo(new Color(0f, 1f, 0f, 1f));
        assertThat(k.v).isEqualTo(new Vector2(3f, 4f));
        assertThat(k.r).isEqualTo(new Rectangle(1f, 2f, 3f, 4f));
        assertThat(k.path).containsExactly(0f, 1f, 2f, 3f);
        Sprite sprite = world.get(e, Sprite.class);
        assertThat(sprite.regionId).isEqualTo("tex.slime");
        assertThat(sprite.origin).isEqualTo(new Vector2(8f, 0f));
        assertThat(sprite.tint.toString()).isEqualTo("99e699ff");
        Transform t = world.get(e, Transform.class);
        assertThat(t.position).isEqualTo(new Vector2(5f, 6f));
        assertThat(t.previous).isEqualTo(t.position);
        assertThat(t.rotation).isEqualTo(30f);
        assertThat(t.previousRotation).isEqualTo(30f);
        assertThat(t.scale).isEqualTo(new Vector2(2f, 3f));
        assertThat(spawned).containsExactly(new EntitySpawned(e, "kitchen"));
    }

    @Test
    void everySpawnGetsItsOwnComponentsAndTheCacheIsUsed() {
        assets.put("hero", """
                { "version": 1, "components": { "Health": { "max": 3 } } }
                """);
        int a = prefabs.spawn(world, "hero", 0f, 0f);
        assets.put("hero", "{ \"version\": 1, \"components\": { \"Health\": { \"max\": 99 } } }");   // not re-read
        int b = prefabs.spawn(world, "hero", 1f, 0f);

        world.get(a, Health.class).max = 50;
        assertThat(world.get(b, Health.class).max).isEqualTo(3);
        assertThat(world.get(b, Transform.class).position.x).as("Transform added when the prefab has none")
                .isEqualTo(1f);
        assertThat(prefabs.contains("hero")).isTrue();
        assertThat(prefabs.contains("villain")).isFalse();
    }

    @Test
    void overridesByComponentFieldOrBareField() {
        assets.put("walker", "{ \"version\": 1, \"components\": { \"Walker\": {}, \"Health\": {} } }");
        EntityRef ref = world.ref(world.create());
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("Walker.speed", 3);         // Integer into float
        overrides.put("max", 7);                  // bare, unique
        overrides.put("target", ref);
        overrides.put("home", new Vector2(1f, 2f));

        int e = prefabs.spawn(world, "walker", 0f, 0f, overrides);

        assertThat(world.get(e, TestComponents.Walker.class).speed).isEqualTo(3f);
        assertThat(world.get(e, Health.class).max).isEqualTo(7);
        assertThat(world.get(e, TestComponents.Walker.class).target).isSameAs(ref);
        assertThat(world.get(e, TestComponents.Walker.class).home).isEqualTo(new Vector2(1f, 2f));
    }

    @Test
    void badOverridesFailWithoutLeavingAnEntity() {
        assets.put("double", "{ \"version\": 1, \"components\": { \"Walker\": {}, \"Mover\": {} } }");
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("speed", 1f);
        overrides.put("Health.max", 1);
        overrides.put("Walker.speeed", 1f);
        overrides.put("Walker.speed", "fast");
        overrides.put("Mover.speed", 0.5);

        PrefabException e = catchThrowableOfType(PrefabException.class,
                () -> prefabs.spawn(world, "double", 0f, 0f, overrides));

        assertThat(e.problems()).containsExactly(
                "override \"speed\": field \"speed\" is ambiguous; write one of [Walker.speed, Mover.speed]",
                "override \"Health.max\": prefab \"double\" has no component \"Health\" (it has [Walker, Mover, "
                        + "Transform])",
                "override \"Walker.speeed\": unknown field \"speeed\" of Walker (fields: [speed, target, path, home, "
                        + "zone])",
                "override \"Walker.speed\": Walker.speed: expected a number for float, got string \"fast\"");
        assertThat(world.entityCount()).isZero();
        assertThat(spawned).isEmpty();
    }

    @Test
    void invalidPrefabFilesNameTheFileComponentAndField() {
        assets.put("broken", """
                { "version": 1, "extra": 1, "components": {
                  "Ghost": {},
                  "Kitchen": { "i": 1.5, "nope": 1, "fixed": 4, "anything": 1, "mode": "SLEEPY", "color": "red",
                               "v": [1], "ref": 3, "tint": null },
                  "Health": 3 } }
                """);

        PrefabException e = catchThrowableOfType(PrefabException.class, () -> prefabs.spawn(world, "broken", 0f, 0f));

        assertThat(e).hasMessageStartingWith("Invalid prefab \"broken\"");
        assertThat(e.problems()).containsExactly(
                "prefabs/broken.json: unknown field \"extra\" (expected version, components)",
                "prefabs/broken.json: unknown component \"Ghost\" (registered: [Transform, Sprite, Collider, Actor, "
                        + "Solid, Kitchen, Walker, Mover, Health, Coin])",
                "prefabs/broken.json: Kitchen.i: expected a whole number for int, got 1.5",
                "prefabs/broken.json: Kitchen: unknown field \"nope\" of Kitchen (fields: [i, l, f, d, b, s, mode, "
                        + "color, tint, v, r, path, ref])",
                "prefabs/broken.json: Kitchen: field \"fixed\" of Kitchen has type int (final), which cannot be set "
                        + "from data",
                "prefabs/broken.json: Kitchen: field \"anything\" of Kitchen has type Object, which cannot be set "
                        + "from data",
                "prefabs/broken.json: Kitchen.mode: \"SLEEPY\" is not a Mode (one of CALM, ANGRY)",
                "prefabs/broken.json: Kitchen.color: \"red\" is not a colour (expected \"#RRGGBB\" or \"#RRGGBBAA\")",
                "prefabs/broken.json: Kitchen.v: expected a vector ([x, y] or {x, y}) for Vector2, got a list",
                "prefabs/broken.json: Kitchen.ref: expected an entity reference (a Tiled object reference, or code) "
                        + "for EntityRef, got number 3",
                "prefabs/broken.json: Kitchen.tint: cannot be null (Color)",
                "prefabs/broken.json: Health: must be an object of field values");
        assertThat(world.entityCount()).isZero();
        assertThatThrownBy(() -> prefabs.spawn(world, "villain", 0f, 0f)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown prefab \"villain\": not in the asset manifest");
    }

    @Test
    void versionsAreCheckedAndMigratedStepByStep() {
        assets.put("v1", "{ \"version\": 1, \"components\": { \"Health\": { \"hp\": 4 } } }");
        assets.put("v3", "{ \"version\": 3, \"components\": {} }");
        assets.put("none", "{ \"components\": {} }");
        prefabs.setVersion(2);

        assertThat(catchThrowableOfType(PrefabException.class, () -> prefabs.spawn(world, "v1", 0f, 0f)).problems())
                .containsExactly("prefabs/v1.json: no migration from version 1 to 2");

        prefabs.addMigration(1, json -> {   // v2 renamed Health.hp to Health.max
            JsonValue health = json.get("components").get("Health");
            health.get("hp").name = "max";
            return json;
        });
        int e = prefabs.spawn(world, "v1", 0f, 0f);
        assertThat(world.get(e, Health.class).max).isEqualTo(4);
        assertThat(assets.files.get("v1").get("components").get("Health").has("hp")).as("loaded asset untouched")
                .isTrue();
        assertThat(catchThrowableOfType(PrefabException.class, () -> prefabs.spawn(world, "v3", 0f, 0f)).problems())
                .containsExactly("prefabs/v3.json: version 3 is newer than the game's prefab version 2");
        assertThat(catchThrowableOfType(PrefabException.class, () -> prefabs.spawn(world, "none", 0f, 0f)).problems())
                .containsExactly("prefabs/none.json: \"version\" is required (a whole number; current is 2)");
    }

    @Test
    void keepRulesCoverEveryComponentAndEnum() {
        String rules = KeepRules.rules(registry, "TestComponents.Setup");

        assertThat(rules).contains("-keep class dev.ember2d.ecs.Transform {\n    <init>();\n    <fields>;\n}")
                .contains("-keep class dev.ember2d.data.TestComponents$Kitchen {")
                .contains("-keep class dev.ember2d.data.TestComponents$Coin {")
                .contains("-keep enum dev.ember2d.data.TestComponents$Mode { *; }")
                .contains("-keepnames class * extends dev.ember2d.ecs.EntitySystem\n")
                .contains("-keepnames class * extends dev.ember2d.app.Scene\n");
        assertThat(rules.split("-keep class", -1)).hasSize(registry.names().size() + 1);
    }

    @Test
    void keepRulesMainWritesTheFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.io.File out = dir.resolve("gen/rules.pro").toFile();

        KeepRules.main(new String[] {TestComponents.Setup.class.getName(), out.getPath()});

        assertThat(out).content().contains("-keep class dev.ember2d.data.TestComponents$Walker");
        assertThatThrownBy(() -> KeepRules.main(new String[] {"java.lang.String", out.getPath()}))
                .hasMessageContaining("does not implement");
    }

    @Test
    void floatTolerance() {
        assets.put("f", "{ \"version\": 1, \"components\": { \"Kitchen\": { \"f\": 0.1 } } }");
        assertThat(world.get(prefabs.spawn(world, "f", 0f, 0f), Kitchen.class).f).isCloseTo(0.1f, within(1e-7f));
    }
}
