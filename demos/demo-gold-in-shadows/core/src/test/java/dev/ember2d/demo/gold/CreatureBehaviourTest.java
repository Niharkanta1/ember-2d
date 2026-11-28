package dev.ember2d.demo.gold;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.Assets;
import dev.ember2d.audio.Audio;
import dev.ember2d.collision.Collider;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.CollisionSystem;
import dev.ember2d.collision.Solid;
import dev.ember2d.collision.TileGrid;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.PrefabLibrary;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.Input;
import dev.ember2d.kit.bullets.BulletPatterns;
import dev.ember2d.kit.bullets.BulletSystem;
import dev.ember2d.kit.bullets.BulletTypes;
import dev.ember2d.kit.bullets.BulletWorld;
import dev.ember2d.kit.common.Health;
import dev.ember2d.kit.common.HealthSystem;
import dev.ember2d.kit.topdown.TopDownSystem;
import dev.ember2d.kit.topdown.ai.AiSystem;
import dev.ember2d.kit.topdown.ai.PathPlanner;
import dev.ember2d.kit.topdown.shooter.ShotEvents;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The creatures' behaviour (F-029, I-630) in a headless world with the real systems: the original's rules for spotting,
 * biting, hearing shots, shooting and dying. A 30 x 20 tile open room; the player stands still.
 */
class CreatureBehaviourTest {

    static final float DT = 1f / 60f;
    static final Path ASSETS = Path.of("assets");

    final World world = new World(false);
    final DefaultEventBus events = new DefaultEventBus(false);
    final TimeState time = new TimeState(DT);
    final Collision collision = new Collision(world, events);
    final HealthSystem health = new HealthSystem(world, events);
    final BulletTypes types = BulletTypes.parse(json("bullets.json"), "bullets.json");
    final BulletWorld bulletWorld = new BulletWorld(256, types);
    final Assets assets = (Assets) Proxy.newProxyInstance(Assets.class.getClassLoader(), new Class<?>[] {Assets.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "get" -> json("prefabs/" + args[0] + ".json");
                case "contains", "isLoaded" -> true;
                case "path" -> "prefabs/" + args[0] + ".json";
                case "generation" -> 0;
                default -> null;
            });
    final Audio audio = (Audio) Proxy.newProxyInstance(Audio.class.getClassLoader(), new Class<?>[] {Audio.class},
            (proxy, method, args) -> method.getReturnType() == float.class ? 0f
                    : method.getReturnType() == boolean.class ? false
                    : method.getReturnType() == long.class ? 0L : null);
    final Input input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> method.getName().equals("axis2") ? ((Vector2) args[1]).setZero()
                    : method.getReturnType() == boolean.class ? false
                    : method.getReturnType() == float.class ? 0f
                    : method.getReturnType() == int.class ? 0 : null);
    final PrefabLibrary prefabs;
    final TileGrid open = new TileGrid(30, 20, 1f, 1f);
    int player;

    static JsonValue json(String path) {
        try {
            return new JsonReader().parse(Files.readString(ASSETS.resolve(path)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    CreatureBehaviourTest() {
        ComponentRegistry registry = new ComponentRegistry();
        new GoldComponents().register(registry);
        prefabs = new PrefabLibrary(registry, assets, events);
        PathPlanner planner = new PathPlanner(open);
        TopDownSystem movement = new TopDownSystem(world, collision, input, events);
        BulletSystem bullets = new BulletSystem(world, bulletWorld, new BulletPatterns(assets, types), events, health,
                time);
        bullets.setWallMask(Walls.MOVE_MASK, collision);
        world.addSystem(new CreatureSystem(world, new CreatureCatalog(json("creatures.json")), collision, health,
                bulletWorld, movement, prefabs, planner, events, audio));
        world.addSystem(new AiSystem(world, planner));
        world.addSystem(movement);
        world.addSystem(health);
        world.addSystem(bullets);
        world.addSystem(new CollisionSystem(world, collision));
        player = world.create();
        world.setName(player, "player");
        world.add(player, new Transform()).teleport(5f, 10f);
        Health h = world.add(player, new Health());
        h.max = 20;
        h.current = 20;
        h.invulnerableSeconds = 0.8f;
        h.damageMultiplier = 2;
        world.add(player, new dev.ember2d.kit.common.Team()).id = 1;
        world.add(player, new dev.ember2d.kit.bullets.BulletTarget()).radius = 0.3f;
    }

    int spawn(String kind, float x, float y) {
        return prefabs.spawn(world, "enemy/" + kind, x, y);
    }

    void wall(float x, float y, float w, float h) {
        int e = world.create();
        world.add(e, new Transform()).teleport(x, y);
        Collider c = world.add(e, new Collider());
        c.box.set(0f, 0f, w, h);
        c.layer = Walls.SIGHT_LAYER;
        world.add(e, new Solid());
    }

    void seconds(float s) {
        for (int i = 0; i < Math.round(s / DT); i++) {
            world.runPhase(Phase.FIXED, time);
            events.flush();
        }
    }

    @Test
    void aRatSpotsChasesAndBites() {
        int rat = spawn("rat", 9f, 10f);   // 64 px away: inside its 90 px sight
        seconds(0.05f);
        Creature c = world.get(rat, Creature.class);
        assertThat(c.hasTarget).isTrue();
        assertThat(c.alertLeft).as("shows !").isPositive();
        seconds(4f);
        assertThat(world.get(player, Health.class).current).as("bitten: each bite is half a heart on Normal")
                .isLessThan(20);
        assertThat(world.get(rat, Transform.class).position.x).as("it came to the player").isLessThan(7f);
    }

    @Test
    void wallsHideThePlayerButShotsAreHeard() {
        wall(7f, 8f, 1f, 5f);
        int rat = spawn("rat", 9f, 10f);
        seconds(0.5f);
        Creature c = world.get(rat, Creature.class);
        assertThat(c.hasTarget).as("no line of sight").isFalse();
        ShotEvents.Fired shot = new ShotEvents.Fired();
        shot.x = 5f;
        shot.y = 10f;
        events.publish(shot);
        assertThat(c.investigating).as("a shot within 110 px").isTrue();
    }

    @Test
    void aCultistFiresAFanOfThreeBolts() {
        int cultist = spawn("cultist", 10f, 10f);   // 80 px: inside its 100 px range
        seconds(0.05f);
        assertThat(world.get(cultist, Creature.class).hasTarget).isTrue();
        int most = 0;
        for (int i = 0; i < 90; i++) {
            seconds(DT);
            most = Math.max(most, bulletWorld.size());
        }
        assertThat(most).as("three bolts in one volley").isGreaterThanOrEqualTo(3);
    }

    @Test
    void aDeadCreatureFadesAndDropsItsGold() {
        int slug = spawn("slug", 20f, 10f);   // far away: idle
        seconds(0.05f);
        health.damage(slug, 8, player, 0f, 0f);
        events.flush();
        assertThat(world.get(slug, Creature.class).state).isEqualTo(Creature.State.DEAD);
        int pouches = 0;
        for (int e : world.family(Pickup.class).entities().toArray()) {
            Pickup p = world.get(e, Pickup.class);
            if (p.kind.equals("pouch")) {
                pouches++;
                assertThat(p.amount).as("the slug's 15 gold").isEqualTo(15);
            }
        }
        assertThat(pouches).isEqualTo(1);
        seconds(0.4f);
        assertThat(world.isAlive(slug)).as("faded out after 0.25 s").isFalse();
    }

    @Test
    void theCatalogHasTheOriginalsValues() {
        CreatureCatalog catalog = new CreatureCatalog(json("creatures.json"));
        assertThat(catalog.size()).isEqualTo(4);
        assertThat(catalog.get("rat").speed).isEqualTo(35f / 16f);
        assertThat(catalog.get("ghoul").attack).isEqualTo("dash");
        assertThat(catalog.get("ghoul").damage).isEqualTo(2);
        assertThat(catalog.get("cultist").count).isEqualTo(3);
        assertThat(catalog.get("slug").range).isEqualTo(72f / 16f);
        assertThat(CreatureSystem.parseRoute("1,2;3,4.5")).containsExactly(1f, 2f, 3f, 4.5f);
        assertThat(CreatureSystem.parseRoute("")).isNull();
    }
}
