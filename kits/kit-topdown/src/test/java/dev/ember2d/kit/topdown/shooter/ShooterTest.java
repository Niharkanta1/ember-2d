package dev.ember2d.kit.topdown.shooter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.Input;
import dev.ember2d.kit.bullets.BulletPatterns;
import dev.ember2d.kit.bullets.BulletSystem;
import dev.ember2d.kit.bullets.BulletTypes;
import dev.ember2d.kit.bullets.BulletWorld;
import dev.ember2d.kit.common.Team;
import dev.ember2d.kit.topdown.TopDownBody;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The shooter package (F-016, I-320): weapons, ammo and reload, firing through kit-bullets, aiming. */
class ShooterTest {

    static final class FakeAssets implements Assets {
        final Map<String, JsonValue> files = new HashMap<>();

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
            return true;
        }

        @Override
        public boolean contains(String id) {
            return files.containsKey(id);
        }
    }

    boolean fire;
    boolean reloadKey;
    final Vector2 aimAxis = new Vector2();
    final Vector2 pointer = new Vector2();
    final Input input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "pressed" -> "fire".equals(args[0]) && fire;
                case "justPressed" -> "reload".equals(args[0]) && reloadKey;
                case "axis2" -> ((Vector2) args[1]).set(aimAxis);
                case "pointerWorld" -> ((Vector2) args[args.length - 1]).set(pointer);
                default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == float.class
                        ? 0f : null;
            });

    final BulletTypes types = BulletTypes.parse(new JsonReader().parse(
            "{ \"version\": 1, \"types\": { \"pellet\": { \"region\": \"tex.p\", \"radius\": 0.1, \"lifetime\": 30 } } }"),
            "bullets.json");
    final FakeAssets assets = new FakeAssets();
    final World world = new World(false);
    final DefaultEventBus events = new DefaultEventBus(false);
    final TimeState time = new TimeState(1f / 60f);
    final BulletWorld bullets = new BulletWorld(8192, types);
    final BulletSystem bulletSystem = new BulletSystem(world, bullets, new BulletPatterns(assets, types), events, null,
            time);
    final WeaponLibrary weapons = new WeaponLibrary(assets, types);
    final WeaponSystem system = new WeaponSystem(world, bulletSystem, weapons, events, input);
    final List<String> seen = new ArrayList<>();

    ShooterTest() {
        world.addSystem(new AimSystem(world, input));
        world.addSystem(system);
        world.addSystem(bulletSystem);
        weapon("gun.smg", "{ \"version\": 1, \"fireRate\": 10, \"auto\": true, \"magazine\": 5, \"reload\": 0.5, "
                + "\"muzzle\": 1, \"emit\": { \"type\": \"arc\", \"bullet\": \"pellet\", \"count\": 3, "
                + "\"spread\": 20, \"speed\": 10 } }");
        weapon("gun.pistol", "{ \"version\": 1, \"fireRate\": 30, \"emit\": { \"type\": \"single\", "
                + "\"bullet\": \"pellet\", \"speed\": 10 } }");
    }

    /** Records the shot events (the allocation test does not, since recording allocates). */
    void record() {
        events.onGlobal(ShotEvents.Fired.class, e -> seen.add("fired " + e.ammo));
        events.onGlobal(ShotEvents.ReloadStarted.class, e -> seen.add("reload " + e.seconds));
        events.onGlobal(ShotEvents.Reloaded.class, e -> seen.add("reloaded " + e.ammo));
    }

    void weapon(String id, String json) {
        assets.files.put(id, new JsonReader().parse(json));
    }

    int shooter(String weapon) {
        int e = world.create();
        world.add(e, new Transform()).teleport(2f, 3f);
        Weapon w = world.add(e, new Weapon());
        w.weapon = weapon;
        w.fireAction = "fire";
        w.reloadAction = "reload";
        world.add(e, new Team()).id = 1;
        return e;
    }

    void steps(int n) {
        for (int i = 0; i < n; i++) {
            world.runPhase(Phase.UPDATE, time);
            world.runPhase(Phase.FIXED, time);
            events.flush();
        }
    }

    long shots() {
        return seen.stream().filter(s -> s.startsWith("fired")).count();
    }

    @Test
    void automaticFireKeepsItsRateAndReloadsByItself() {
        record();
        int e = shooter("gun.smg");
        fire = true;
        steps(30);   // 0.5 s: shots at 0, 0.1, 0.2, 0.3, 0.4 empty the magazine, then a reload starts
        assertThat(shots()).isEqualTo(5);
        assertThat(seen).contains("reload 0.5");
        Weapon w = world.get(e, Weapon.class);
        assertThat(w.ammo).isZero();
        assertThat(w.reloading).isTrue();
        steps(30);
        assertThat(seen).contains("reloaded 5");
        assertThat(shots()).as("firing resumes after the reload").isGreaterThan(5);
        assertThat(bullets.size()).as("3 pellets a shot").isEqualTo((int) shots() * 3);
        assertThat(bullets.team(0)).isEqualTo(1);
    }

    @Test
    void singleShotsNeedANewPullAndManualReloadWorks() {
        record();
        int e = shooter("gun.pistol");
        fire = true;
        steps(20);
        assertThat(shots()).isEqualTo(1);
        fire = false;
        steps(1);
        fire = true;
        steps(1);
        assertThat(shots()).isEqualTo(2);
        assertThat(world.get(e, Weapon.class).ammo).as("unlimited").isZero();
        assertThat(system.reload(e)).as("nothing to reload").isFalse();

        int smg = shooter("gun.smg");
        world.get(e, Weapon.class).fireAction = "";
        fire = true;
        steps(3);
        fire = false;
        reloadKey = true;
        steps(1);
        reloadKey = false;
        assertThat(world.get(smg, Weapon.class).reloading).isTrue();
        assertThat(system.reload(smg)).as("already reloading").isFalse();
    }

    @Test
    void shotsLeaveTheMuzzleAlongTheAim() {
        shooter("gun.pistol");
        int aimer = shooter("gun.pistol");
        world.destroy(aimer);
        int e = shooter("gun.pistol");
        Aim aim = world.add(e, new Aim());
        aim.mouse = true;
        pointer.set(2f, 10f);   // straight up from (2, 3)
        fire = true;
        steps(1);
        int newest = bullets.size() - 1;
        assertThat(bullets.x(newest)).isCloseTo(2f, within(0.2f));
        assertThat(bullets.vy(newest)).isCloseTo(10f, within(0.01f));
        assertThat(aim.angle).isCloseTo(90f, within(1e-3f));

        aimAxis.set(-1f, 0f);   // the aim axis wins over the mouse
        steps(1);
        assertThat(aim.angle).isCloseTo(180f, within(1e-3f));
    }

    @Test
    void withoutAnAimItShootsAlongTheFacing() {
        int e = shooter("gun.pistol");
        TopDownBody body = world.add(e, new TopDownBody());
        body.facingX = 0f;
        body.facingY = -1f;
        fire = true;
        steps(1);
        assertThat(bullets.vy(0)).isCloseTo(-10f, within(0.01f));
        assertThat(bullets.y(0)).as("muzzle 0.5 below").isLessThan(3f);
    }

    @Test
    void shootingDoesNotAllocateAfterWarmUp() {
        int e = shooter("gun.smg");
        Weapon w = world.get(e, Weapon.class);
        w.fireAction = "";   // a proxy-based fake input allocates on every call: pull the trigger directly
        w.reloadAction = "";
        w.trigger = true;
        steps(120);
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        long before = mx.getThreadAllocatedBytes(tid);
        for (int i = 0; i < 600; i++) {
            world.runPhase(Phase.FIXED, time);
        }
        long allocated = mx.getThreadAllocatedBytes(tid) - before;
        assertThat(allocated).as("bytes over 600 steps of firing").isLessThan(16 * 1024);
    }

    @Test
    void badWeaponFilesAreReported() {
        weapon("bad", "{ \"version\": 1, \"fireRate\": 0, \"magazine\": -1, \"barrel\": 2, "
                + "\"emit\": { \"type\": \"ring\", \"bullet\": \"rocket\" } }");
        assertThatThrownBy(() -> weapons.get("bad")).hasMessageContaining("\"fireRate\" is required")
                .hasMessageContaining("unknown field \"barrel\"").hasMessageContaining("\"magazine\" and \"reload\"")
                .hasMessageContaining("unknown bullet \"rocket\"");
        assertThatThrownBy(() -> weapons.get("none")).hasMessageContaining("not in the asset manifest");
    }
}
