package dev.ember2d.ecs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.core.LogConfig;
import dev.ember2d.core.LogLevel;
import dev.ember2d.core.LogSink;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorldTest {

    static final class Velocity {
        float x;
        float y;
    }

    static final class Hidden {
    }

    static final class Unregistered {
    }

    private final World world = new World(true);
    private final List<String> warnings = new ArrayList<>();
    private final LogSink sink = (level, category, message, error) -> {
        if (level == LogLevel.WARN) {
            warnings.add(message);
        }
    };

    @BeforeEach
    void setUp() {
        world.registerComponent(Velocity.class, Velocity::new);
        world.registerComponent(Hidden.class, Hidden::new);
        LogConfig.resetToDefaults();
        LogConfig.removeSink(LogConfig.consoleSink());
        LogConfig.addSink(sink);
    }

    @AfterEach
    void resetLog() {
        LogConfig.resetToDefaults();
    }

    @Test
    void addGetHasRemoveOutsideSystemsAreImmediate() {
        int e = world.create();

        Transform t = world.add(e, Transform.class);
        t.position.set(4, 2);
        Velocity v = world.add(e, new Velocity());

        assertThat(world.get(e, Transform.class)).isSameAs(t);
        assertThat(world.get(e, Velocity.class)).isSameAs(v);
        assertThat(world.has(e, Velocity.class)).isTrue();
        world.remove(e, Velocity.class);
        assertThat(world.has(e, Velocity.class)).isFalse();
        assertThat(world.get(e, Velocity.class)).isNull();
    }

    @Test
    void addingAnInstanceReplacesTheComponentOfThatType() {
        int e = world.create();
        Velocity first = world.add(e, new Velocity());
        Velocity second = world.add(e, new Velocity());

        assertThat(world.get(e, Velocity.class)).isSameAs(second).isNotSameAs(first);
    }

    @Test
    void unregisteredComponentTypeExplainsTheFix() {
        int e = world.create();

        assertThatThrownBy(() -> world.add(e, Unregistered.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Unregistered.class.getName())
                .hasMessageContaining("registerComponent(Unregistered.class, Unregistered::new)");
        world.add(e, new Unregistered());   // instances always work
        assertThat(world.has(e, Unregistered.class)).isTrue();
    }

    @Test
    void familiesTrackMembershipIncludingExclusions() {
        Family moving = world.family(Transform.class, Velocity.class);
        Family visibleMoving = moving.exclude(Hidden.class);
        int a = world.create();
        world.add(a, Transform.class);
        world.add(a, Velocity.class);
        int b = world.create();
        world.add(b, Transform.class);
        world.add(b, Velocity.class);
        world.add(b, Hidden.class);
        int c = world.create();
        world.add(c, Transform.class);

        assertThat(moving.entities().toArray()).containsExactly(a, b);
        assertThat(visibleMoving.entities().toArray()).containsExactly(a);

        world.remove(b, Hidden.class);
        world.remove(a, Velocity.class);
        assertThat(moving.entities().toArray()).containsExactly(b);
        assertThat(visibleMoving.entities().toArray()).containsExactly(b);
    }

    @Test
    void familiesAreCachedIgnoringTypeOrderAndIncludeExistingEntities() {
        int e = world.create();
        world.add(e, Transform.class);
        world.add(e, Velocity.class);

        Family family = world.family(Velocity.class, Transform.class);

        assertThat(world.family(Transform.class, Velocity.class)).isSameAs(family);
        assertThat(family.exclude(Hidden.class)).isSameAs(world.family(Transform.class, Velocity.class).exclude(Hidden.class));
        assertThat(family.size()).isEqualTo(1);
    }

    @Test
    void staleRefAndHandleAfterSlotReuse() {
        int first = world.create();
        EntityRef ref = world.ref(first);
        world.destroy(first);

        int reused = world.create();

        assertThat(reused & World.INDEX_MASK).as("slot reused").isEqualTo(first & World.INDEX_MASK);
        assertThat(reused).isNotEqualTo(first);
        assertThat(world.isAlive(first)).isFalse();
        assertThat(ref.isAlive(world)).isFalse();
        assertThat(ref.entity(world)).isEqualTo(World.NONE);
        assertThat(world.ref(reused).entity(world)).isEqualTo(reused);
        assertThat(world.isAlive(World.NONE)).isFalse();
    }

    @Test
    void getOnDeadEntityReturnsNullAndWarnsOnceInDevMode() {
        int e = world.create();
        world.add(e, Transform.class);
        world.destroy(e);

        assertThat(world.get(e, Transform.class)).isNull();
        assertThat(world.get(e, Transform.class)).isNull();

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("dead entity");
    }

    @Test
    void destroyRemovesComponentsFamiliesNamesAndTags() {
        Family family = world.family(Transform.class);
        int e = world.create();
        world.add(e, Transform.class);
        world.setName(e, "player");
        world.tag(e, "hero");

        world.destroy(e);

        assertThat(family.size()).isZero();
        assertThat(world.findByName("player")).isEqualTo(World.NONE);
        IntArray heroes = new IntArray();
        world.findByTag("hero", heroes);
        assertThat(heroes.size).isZero();
        assertThat(world.entityCount()).isZero();
    }

    @Test
    void namesAndTags() {
        int a = world.create();
        int b = world.create();
        world.setName(a, "door");
        world.tag(a, "enemy-spawn");
        world.tag(b, "enemy-spawn");
        world.tag(b, "enemy-spawn");

        assertThat(world.findByName("door")).isEqualTo(a);
        assertThat(world.name(a)).isEqualTo("door");
        assertThat(world.findByName("missing")).isEqualTo(World.NONE);
        IntArray spawns = new IntArray();
        world.findByTag("enemy-spawn", spawns);
        assertThat(spawns.toArray()).containsExactly(a, b);
        assertThat(world.hasTag(b, "enemy-spawn")).isTrue();

        world.untag(b, "enemy-spawn");
        world.setName(a, null);
        assertThat(world.hasTag(b, "enemy-spawn")).isFalse();
        assertThat(world.findByName("door")).isEqualTo(World.NONE);
    }

    @Test
    void operationsOnDeadEntitiesFailClearly() {
        int e = world.create();
        world.destroy(e);

        assertThatThrownBy(() -> world.add(e, new Velocity())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dead or unknown entity");
        assertThatThrownBy(() -> world.ref(e)).isInstanceOf(IllegalArgumentException.class);
        world.destroy(e);   // no-op
        world.remove(e, Velocity.class);   // no-op
    }

    @Test
    void clearLeavesNoEntitiesOrSystemsAndOldHandlesStayStale() {
        Family family = world.family(Transform.class);
        int e = world.create();
        world.add(e, Transform.class);
        world.addSystem(new EntitySystem(dev.ember2d.core.Phase.UPDATE, family) {
            @Override
            protected void process(int entity, float delta) {
            }
        });

        world.clear();

        assertThat(world.entityCount()).isZero();
        assertThat(family.size()).isZero();
        assertThat(world.isAlive(e)).isFalse();
        int fresh = world.create();
        assertThat(fresh).isNotEqualTo(e);
    }

    @Test
    void worldsDoNotShareEntities() {
        World other = new World(false);
        int here = world.create();
        world.add(here, Transform.class);

        assertThat(other.entityCount()).isZero();
        assertThat(other.family(Transform.class).size()).isZero();
        assertThat(world.family(Transform.class).size()).isEqualTo(1);
    }

    @Test
    void familyNeedsARequiredType() {
        assertThatThrownBy(world::family).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handlesNeverCollideWithNoneAcrossGenerationWrap() {
        int index = World.INDEX_MASK & world.create();
        for (int i = 0; i < 5000; i++) {   // more reuses than the 12-bit generation holds
            int e = world.create();
            assertThat(e).isNotEqualTo(World.NONE);
            world.destroy(e);
        }
        assertThat(index).isZero();
    }
}
