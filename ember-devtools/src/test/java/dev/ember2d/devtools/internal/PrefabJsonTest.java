package dev.ember2d.devtools.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.collision.Collider;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.PrefabLibrary;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.render.Sprite;
import dev.ember2d.render.Text;
import dev.ember2d.render.TextAlign;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** "Copy as prefab JSON" (F-018, I-160): the output spawns an equal entity through the prefab library (F-010). */
class PrefabJsonTest {

    private final Map<String, JsonValue> files = new HashMap<>();
    private final ComponentRegistry registry = new ComponentRegistry();
    private final PrefabLibrary prefabs = new PrefabLibrary(registry, new Assets() {
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
    }, new DefaultEventBus(false));

    @Test
    void theCopiedJsonSpawnsAnEqualEntity() {
        World world = new World(false);
        int e = world.create();
        Transform t = world.add(e, Transform.class);
        t.teleport(5f, 6f);
        t.rotation = 30f;
        t.scale.set(2f, 3f);
        Sprite sprite = world.add(e, new Sprite());
        sprite.regionId = "tex.hero";
        sprite.tint.set(1f, 0.5f, 0f, 1f);
        sprite.origin.set(8f, 0f);
        sprite.z = 2f;
        sprite.flipX = true;
        Collider collider = world.add(e, new Collider());
        collider.box.set(-0.5f, 0f, 1f, 1.5f);
        collider.trigger = true;
        Text text = world.add(e, new Text());
        text.font = "font.pixel";
        text.text = "hello";
        text.align = TextAlign.CENTER;
        text.offset.set(0f, 1.25f);

        String json = PrefabJson.of(world, e, registry);
        files.put("copy", new JsonReader().parse(json));
        int copy = prefabs.spawn(world, "copy", 0f, 0f);

        assertThat(json).doesNotContain("position").doesNotContain("previous").as("only changed fields")
                .doesNotContain("flipY");
        Transform ct = world.get(copy, Transform.class);
        assertThat(ct.rotation).isEqualTo(30f);
        assertThat(ct.scale).isEqualTo(t.scale);
        Sprite cs = world.get(copy, Sprite.class);
        assertThat(cs.regionId).isEqualTo("tex.hero");
        assertThat(cs.tint).isEqualTo(sprite.tint);
        assertThat(cs.origin).isEqualTo(sprite.origin);
        assertThat(cs.z).isEqualTo(2f);
        assertThat(cs.flipX).isTrue();
        Collider cc = world.get(copy, Collider.class);
        assertThat(cc.box).isEqualTo(collider.box);
        assertThat(cc.trigger).isTrue();
        assertThat(cc.mask).isEqualTo(collider.mask);
        Text ctext = world.get(copy, Text.class);
        assertThat(ctext.font).isEqualTo("font.pixel");
        assertThat(ctext.text).isEqualTo("hello");
        assertThat(ctext.align).isEqualTo(TextAlign.CENTER);
        assertThat(ctext.offset).isEqualTo(text.offset);
    }

    @Test
    void unregisteredComponentsAreLeftOut() {
        World world = new World(false);
        int e = world.create();
        world.add(e, Transform.class);
        world.add(e, new StringBuilder("not a component"));

        String json = PrefabJson.of(world, e, registry);

        JsonValue components = new JsonReader().parse(json).get("components");
        assertThat(components.size).isEqualTo(1);
        assertThat(components.child.name).isEqualTo("Transform");
    }
}
