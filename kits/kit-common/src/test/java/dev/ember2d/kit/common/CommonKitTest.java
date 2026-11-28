package dev.ember2d.kit.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.collision.Actor;
import dev.ember2d.collision.Collider;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.CollisionSystem;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.PrefabLibrary;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The common kit (F-021, I-300): damage, hits, triggers, lifetime, spawners and pools. */
class CommonKitTest {

    static final float DT = 1f / 60f;

    /** Prefabs served from memory. */
    static final class FakeAssets implements Assets {
        final Map<String, JsonValue> files = new HashMap<>();

        void prefab(String id, String json) {
            files.put(id, new JsonReader().parse(json));
        }

        @Override
        public <T> T get(String id, Class<T> type) {
            return type.cast(files.get(id));
        }

        @Override
        public TextureRegion region(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AsyncLoad loadGroup(String group) {
            throw new UnsupportedOperationException();
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
    }

    final World world = new World(false);
    final DefaultEventBus events = new DefaultEventBus(false);
    final TimeState time = new TimeState(DT);
    final FakeAssets assets = new FakeAssets();
    final ComponentRegistry registry = new ComponentRegistry();
    final PrefabLibrary prefabs;
    final HealthSystem health = new HealthSystem(world, events);
    final List<Object> seen = new ArrayList<>();
    final GameLoop loop = new GameLoop(() -> 16_666_668L, time, phase -> {
        world.runPhase(phase, time);
        if (phase == Phase.FIXED || phase == Phase.UPDATE) {
            events.flush();
        }
    }, 5);

    CommonKitTest() {
        CommonKit.register(registry);
        prefabs = new PrefabLibrary(registry, assets, events);
        world.addSystem(health);
        for (Class<?> type : List.of(Damaged.class, Died.class, ZoneEntered.class, ZoneExited.class, Spawned.class,
                SpawnerFinished.class)) {
            events.onGlobal(type, seen::add);
        }
    }

    void frames(int n) {
        for (int i = 0; i < n; i++) {
            loop.frame();
        }
    }

    int body(float x, float y) {
        int e = world.create();
        world.add(e, new Transform()).teleport(x, y);
        return e;
    }

    int target(float x, float y, int team) {
        int e = body(x, y);
        Health h = world.add(e, new Health());
        h.max = 3;
        h.invulnerableSeconds = 0.5f;
        world.add(e, new Hurtbox()).box.set(-0.5f, 0f, 1f, 1f);
        world.add(e, new Team()).id = team;
        return e;
    }

    long count(Class<?> type) {
        return seen.stream().filter(type::isInstance).count();
    }

    // ---- damage and hits ----

    @Test
    void aHitDamagesOncePerActivationAndInvulnerabilityBlocksRepeats() {
        world.addSystem(new HitSystem(world, health));
        int victim = target(0f, 0f, 2);
        int sword = body(0f, 0f);
        Hitbox hb = world.add(sword, new Hitbox());
        hb.damage = 1;
        hb.knockback = 4f;
        world.add(sword, new Team()).id = 1;
        frames(60);   // one second of overlap
        assertThat(world.get(victim, Health.class).current).as("once per activation").isEqualTo(2);
        Damaged d = (Damaged) seen.get(0);
        assertThat(d.target()).isEqualTo(victim);
        assertThat(d.source()).isEqualTo(sword);
        assertThat(d.healthLeft()).isEqualTo(2);

        assertThat(health.isInvulnerable(victim)).as("the window ended within the second").isFalse();
        hb.restart();   // a new activation hits again
        frames(1);
        assertThat(world.get(victim, Health.class).current).isEqualTo(1);
        assertThat(health.isInvulnerable(victim)).isTrue();

        hb.active = false;
        frames(1);
        hb.active = true;   // turning it on again is a new activation, but the target is still invulnerable
        frames(1);
        assertThat(world.get(victim, Health.class).current).as("blocked while invulnerable").isEqualTo(1);
        assertThat(health.damage(victim, 1, World.NONE, 0f, 0f)).isFalse();
        frames(40);
        assertThat(world.get(victim, Health.class).current).as("that activation already tried it").isEqualTo(1);
    }

    @Test
    void theDamageMultiplierScalesEveryHit() {
        int victim = target(0f, 0f, 2);
        Health h = world.get(victim, Health.class);
        h.max = 20;   // five hearts counted in quarters
        h.current = 20;
        h.invulnerableSeconds = 0f;
        h.damageMultiplier = 2;
        assertThat(health.damage(victim, 1, World.NONE, 0f, 0f)).isTrue();
        assertThat(h.current).isEqualTo(18);
        assertThat(((Damaged) seen.get(0)).amount()).as("the event reports what was taken").isEqualTo(2);
        h.damageMultiplier = 0;
        assertThat(health.damage(victim, 1, World.NONE, 0f, 0f)).as("immune").isFalse();
        assertThat(h.current).isEqualTo(18);
    }

    @Test
    void deathFiresOnceAndCanDespawn() {
        int victim = target(5f, 0f, 2);
        world.get(victim, Health.class).invulnerableSeconds = 0f;
        world.get(victim, Health.class).destroyOnDeath = true;
        assertThat(health.damage(victim, 2, World.NONE, 0f, 0f)).isTrue();
        assertThat(health.damage(victim, 5, World.NONE, 0f, 0f)).isTrue();
        assertThat(health.damage(victim, 5, World.NONE, 0f, 0f)).isFalse();
        health.kill(victim, World.NONE);
        assertThat(count(Died.class)).isEqualTo(1);
        assertThat(world.isAlive(victim)).isFalse();
        assertThat(((Damaged) seen.get(1)).healthLeft()).isZero();

        int other = target(9f, 0f, 2);
        health.damage(other, 1, World.NONE, 0f, 0f);
        health.heal(other, 10);
        assertThat(world.get(other, Health.class).current).isEqualTo(3);
        assertThat(health.damage(other, 0, World.NONE, 0f, 0f)).isFalse();
    }

    @Test
    void teamsWindowsAndDestroyOnHit() {
        world.addSystem(new HitSystem(world, health));
        int friend = target(0f, 0f, 1);
        int foe = target(0.6f, 0f, 2);
        int bullet = body(0.3f, 0f);
        Hitbox hb = world.add(bullet, new Hitbox());
        hb.box.set(-0.1f, 0f, 0.2f, 0.2f);
        hb.destroyOnHit = true;
        hb.knockback = 2f;
        world.add(bullet, new Team()).id = 1;
        frames(1);
        assertThat(world.get(friend, Health.class).current).as("own team").isEqualTo(3);
        assertThat(world.get(foe, Health.class).current).isEqualTo(2);
        assertThat(world.isAlive(bullet)).as("destroyOnHit").isFalse();
        Damaged d = (Damaged) seen.get(0);
        assertThat(d.knockbackX()).as("away from the hitbox").isGreaterThan(0f);
        assertThat(Math.hypot(d.knockbackX(), d.knockbackY())).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-4));

        int swing = body(20f, 0f);
        Hitbox s = world.add(swing, new Hitbox());
        s.activeSeconds = 0.1f;
        frames(10);
        assertThat(s.active).as("the window closed").isFalse();
    }

    @Test
    void anEmptyHurtboxUsesTheCollider() {
        world.addSystem(new HitSystem(world, health));
        int victim = body(0f, 0f);
        world.add(victim, new Health());
        world.add(victim, new Hurtbox());
        world.add(victim, new Collider()).box.set(-0.5f, 0f, 1f, 2f);
        int spike = body(0f, 1.5f);
        world.add(spike, new Hitbox()).box.set(-0.1f, 0f, 0.2f, 0.2f);
        world.add(spike, new Team()).id = 3;
        frames(1);
        assertThat(world.get(victim, Health.class).current).isEqualTo(2);
    }

    // ---- triggers ----

    @Test
    void zonesFilterByTagAndTeamAndFireOnce() {
        Collision collision = new Collision(world, events);
        world.addSystem(new TriggerZoneSystem(world, events));
        world.addSystem(new CollisionSystem(world, collision));
        int coin = body(0f, 0f);
        world.setName(coin, "coin");
        Collider c = world.add(coin, new Collider());
        c.box.set(-0.5f, 0f, 1f, 1f);
        c.trigger = true;
        TriggerZone zone = world.add(coin, new TriggerZone());
        zone.tag = "player";
        zone.once = true;

        int enemy = body(5f, 0f);
        world.add(enemy, new Actor());
        world.add(enemy, new Collider()).box.set(-0.4f, 0f, 0.8f, 1f);
        int player = body(9f, 0f);
        world.add(player, new Actor());
        world.add(player, new Collider()).box.set(-0.4f, 0f, 0.8f, 1f);
        world.tag(player, "player");

        world.get(enemy, Transform.class).teleport(0f, 0f);
        frames(2);
        assertThat(count(ZoneEntered.class)).as("not tagged").isZero();
        world.get(player, Transform.class).teleport(0.2f, 0f);
        frames(2);
        world.get(player, Transform.class).teleport(9f, 0f);
        frames(2);
        world.get(player, Transform.class).teleport(0.2f, 0f);
        frames(2);
        assertThat(seen).filteredOn(ZoneEntered.class::isInstance)
                .containsExactly(new ZoneEntered(coin, player, "coin"));
        assertThat(zone.fired).isTrue();

        zone.once = false;
        zone.fired = false;
        zone.tag = "";
        zone.team = 4;
        world.add(player, new Team()).id = 4;
        world.get(player, Transform.class).teleport(9f, 0f);
        frames(2);
        assertThat(seen).filteredOn(ZoneExited.class::isInstance).hasSize(1);
        world.get(enemy, Transform.class).teleport(9f, 0f);
        frames(2);
        assertThat(seen).as("team 0 is filtered out").filteredOn(ZoneExited.class::isInstance).hasSize(1);
    }

    // ---- lifetime, spawners, pools ----

    @Test
    void lifetimeDespawnsAndPooledEntitiesGoBack() {
        world.addSystem(new LifetimeSystem(world));
        assets.prefab("fx/spark", "{ \"version\": 1, \"components\": { \"Lifetime\": { \"seconds\": 0.5 } } }");
        EntityPool pool = EntityPool.ofPrefab(world, prefabs, "fx/spark", 2);
        int plain = body(0f, 0f);
        world.add(plain, new Lifetime()).seconds = 0.25f;
        int spark = pool.obtain(3f, 4f);
        assertThat(world.get(spark, Transform.class).position.x).isEqualTo(3f);
        frames(20);
        assertThat(world.isAlive(plain)).isFalse();
        assertThat(world.isAlive(spark)).isTrue();
        frames(15);
        assertThat(world.isAlive(spark)).as("pooled: kept alive").isTrue();
        assertThat(world.has(spark, Lifetime.class)).as("but out of play").isFalse();
        assertThat(pool.freeCount()).isEqualTo(2);
        assertThat(pool.inUse()).isZero();

        int again = pool.obtain(1f, 1f);
        assertThat(world.get(again, Lifetime.class).left).as("reset to the prefab").isEqualTo(-1f);
        Despawn.despawn(world, again);
        Despawn.despawn(world, again);   // twice is harmless
        assertThat(pool.freeCount()).isEqualTo(2);
        assertThatThrownBy(() -> pool.free(again)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPoolReusesEntitiesAndComponentsAfterWarmUp() {
        assets.prefab("fx/boom", "{ \"version\": 1, \"components\": { \"Health\": { \"max\": 7 }, \"Team\": { \"id\": 2 } } }");
        EntityPool pool = EntityPool.ofPrefab(world, prefabs, "fx/boom", 3);
        int before = world.entityCount();
        List<Integer> ids = new ArrayList<>();
        List<Object> healths = new ArrayList<>();
        for (int round = 0; round < 5; round++) {
            int a = pool.obtain(round, 0f);
            int b = pool.obtain(round, 1f);
            Health h = world.get(a, Health.class);
            assertThat(h.max).isEqualTo(7);
            assertThat(h.current).as("reset").isEqualTo(-1);
            h.current = 1;   // changed during play
            world.get(a, Team.class).id = 9;
            ids.add(a);
            ids.add(b);
            healths.add(h);
            Despawn.despawn(world, a);
            Despawn.despawn(world, b);
        }
        assertThat(world.entityCount()).as("no new entities").isEqualTo(before);
        assertThat(ids.stream().distinct().count()).isLessThanOrEqualTo(3);
        assertThat(healths.stream().distinct().count()).as("no new component instances").isLessThanOrEqualTo(3);
        int c = pool.obtain(0f, 0f);
        assertThat(world.get(c, Team.class).id).isEqualTo(2);
        assertThat(pool.owns(c)).isTrue();
        assertThat(pool.owns(body(0f, 0f))).isFalse();
    }

    @Test
    void spawnersKeepIntervalBurstTotalAndCap() {
        world.addSystem(new SpawnerSystem(world, prefabs, events));
        assets.prefab("enemy", "{ \"version\": 1, \"components\": { \"Health\": {} } }");
        int spawner = body(10f, 0f);
        Spawner s = world.add(spawner, new Spawner());
        s.prefab = "enemy";
        s.interval = 1f;
        s.burst = 2;
        s.maxAlive = 3;
        s.total = 5;
        s.offsetY = 2f;
        frames(1);
        assertThat(count(Spawned.class)).as("no delay: at once").isEqualTo(2);
        Spawned first = (Spawned) seen.get(0);
        assertThat(world.get(first.entity(), Transform.class).position.y).isEqualTo(2f);
        frames(62);
        assertThat(count(Spawned.class)).as("capped at 3 alive").isEqualTo(3);
        frames(120);
        assertThat(count(Spawned.class)).isEqualTo(3);
        seen.stream().filter(Spawned.class::isInstance).map(e -> ((Spawned) e).entity()).toList()
                .forEach(world::destroy);
        frames(1);
        assertThat(count(Spawned.class)).isEqualTo(5);
        assertThat(count(SpawnerFinished.class)).isEqualTo(1);
        frames(120);
        assertThat(count(Spawned.class)).as("total reached").isEqualTo(5);
    }

    @Test
    void aSpawnerCanWaitForAZone() {
        world.addSystem(new SpawnerSystem(world, prefabs, events));
        assets.prefab("enemy", "{ \"version\": 1, \"components\": {} }");
        Spawner s = world.add(body(0f, 0f), new Spawner());
        s.prefab = "enemy";
        s.waitFor = "gate";
        s.delay = 0.5f;
        frames(60);
        assertThat(count(Spawned.class)).isZero();
        events.publish(new ZoneEntered(World.NONE, World.NONE, "other"));
        frames(60);
        assertThat(count(Spawned.class)).isZero();
        events.publish(new ZoneEntered(World.NONE, World.NONE, "gate"));
        frames(20);
        assertThat(count(Spawned.class)).as("after the delay").isZero();
        frames(20);
        assertThat(count(Spawned.class)).isEqualTo(1);
    }

    @Test
    void theComponentsAreUsableFromData() {
        assertThat(registry.names()).contains("Health", "Team", "Hitbox", "Hurtbox", "Lifetime", "TriggerZone",
                "Spawner");
        assets.prefab("p", "{ \"version\": 1, \"components\": { \"Hitbox\": { \"box\": { \"x\": 0, \"y\": 0, "
                + "\"width\": 2, \"height\": 1 }, \"damage\": 3 }, \"Spawner\": { \"prefab\": \"q\", \"waitFor\": \"z\" } } }");
        int e = prefabs.spawn(world, "p", 0f, 0f);
        assertThat(world.get(e, Hitbox.class).box.width).isEqualTo(2f);
        assertThat(world.get(e, Spawner.class).waitFor).isEqualTo("z");
    }

    @Test
    void aHitFlashesTheSpriteForItsTime() {
        world.addSystem(new HitFlashSystem(world, events));
        int e = target(0f, 0f, 2);
        dev.ember2d.render.Sprite sprite = world.add(e, new dev.ember2d.render.Sprite());
        HitFlash flash = world.add(e, new HitFlash());
        flash.seconds = 0.1f;
        flash.color.set(com.badlogic.gdx.graphics.Color.RED);
        frames(1);
        assertThat(sprite.flash).isZero();
        health.damage(e, 1, World.NONE, 0f, 0f);
        assertThat(sprite.flash).as("on at once").isEqualTo(1f);
        assertThat(sprite.flashColor).isEqualTo(com.badlogic.gdx.graphics.Color.RED);
        frames(3);   // 0.05 s
        assertThat(sprite.flash).isEqualTo(1f);
        frames(4);   // past 0.1 s
        assertThat(sprite.flash).as("off after its time").isZero();
    }
}
