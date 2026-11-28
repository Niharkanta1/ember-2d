package dev.ember2d.demo.gold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.Assets;
import dev.ember2d.audio.Audio;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.PrefabLibrary;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.Input;
import dev.ember2d.kit.common.Health;
import dev.ember2d.kit.common.HealthSystem;
import dev.ember2d.save.SaveData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The world's rules (F-029, I-640) in a headless world with the real systems and prefabs: collecting gold, medkits and
 * keys, opening chests, spike traps, the exit door, and the campaign's score and progress.
 */
class WorldRulesTest {

    static final float DT = 1f / 60f;
    static final float PX = 1f / 16f;
    static final Path ASSETS = Path.of("assets");

    final World world = new World(false);
    final DefaultEventBus events = new DefaultEventBus(false);
    final TimeState time = new TimeState(DT);
    final HealthSystem health = new HealthSystem(world, events);
    final Campaign campaign = Campaign.fresh();
    final List<String> sounds = new ArrayList<>();
    final List<Object> cleared = new ArrayList<>();
    boolean interact;
    final Assets assets = (Assets) Proxy.newProxyInstance(Assets.class.getClassLoader(), new Class<?>[] {Assets.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "get" -> args[0].toString().startsWith("tex.") ? null : json("prefabs/" + args[0] + ".json");
                case "contains", "isLoaded" -> true;
                case "path" -> "prefabs/" + args[0] + ".json";
                case "generation" -> 0;
                default -> null;
            });
    final Audio audio = (Audio) Proxy.newProxyInstance(Audio.class.getClassLoader(), new Class<?>[] {Audio.class},
            (proxy, method, args) -> {
                if (method.getName().startsWith("play") && args != null && args[0] instanceof String id) {
                    sounds.add(id);
                }
                return method.getReturnType() == float.class ? 0f
                        : method.getReturnType() == boolean.class ? false
                        : method.getReturnType() == long.class ? 0L : null;
            });
    final Input input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> method.getName().equals("axis2") ? ((Vector2) args[1]).setZero()
                    : method.getName().equals("justPressed") ? interact && "interact".equals(args[0])
                    : method.getReturnType() == boolean.class ? false
                    : method.getReturnType() == float.class ? 0f
                    : method.getReturnType() == int.class ? 0 : null);
    final PrefabLibrary prefabs;
    final FloatingTexts texts = new FloatingTexts(assets, Effects.LAYER);
    final int player;

    static JsonValue json(String path) {
        try {
            return new JsonReader().parse(Files.readString(ASSETS.resolve(path)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    WorldRulesTest() {
        ComponentRegistry registry = new ComponentRegistry();
        new GoldComponents().register(registry);
        prefabs = new PrefabLibrary(registry, assets, events);
        world.addSystem(new PickupSystem(world, campaign, input, prefabs, audio, texts));
        world.addSystem(new SpikeSystem(world, health, assets, audio));
        world.addSystem(new ExitSystem(world, events, assets, audio, texts));
        world.addSystem(health);
        events.on(ExitSystem.LevelCleared.class, cleared::add);
        player = world.create();
        world.setName(player, "player");
        world.add(player, new Transform()).teleport(5f, 5f);
        Health h = world.add(player, new Health());
        h.max = 20;
        h.current = 20;
        h.invulnerableSeconds = 0.8f;
        h.damageMultiplier = 2;
        world.add(player, new Hero());
    }

    void seconds(float s) {
        for (int i = 0; i < Math.round(s / DT); i++) {
            world.runPhase(Phase.FIXED, time);
            events.flush();
        }
    }

    Vector2 playerAt() {
        return world.get(player, Transform.class).position;
    }

    @Test
    void goldNearbyIsPulledInAndAddsToTheScore() {
        prefabs.spawn(world, "pickup/coin", 5f + 20f * PX, 5f);   // inside the 28 px magnet
        int far = prefabs.spawn(world, "pickup/coin", 5f + 40f * PX, 5f);
        seconds(1f);
        assertThat(campaign.gold).isEqualTo(10);
        assertThat(world.isAlive(far)).as("out of the magnet's reach").isTrue();
        assertThat(sounds).contains("sfx.coin");
        assertThat(texts.size()).as("+10 rises").isEqualTo(1);
    }

    @Test
    void aMedkitIsLeftWhileHealthIsFull() {
        int kit = prefabs.spawn(world, "pickup/medkit", 5f, 5f);
        seconds(0.1f);
        assertThat(world.isAlive(kit)).isTrue();
        world.get(player, Health.class).current = 6;
        seconds(0.1f);
        assertThat(world.isAlive(kit)).isFalse();
        assertThat(world.get(player, Health.class).current).as("two hearts").isEqualTo(14);
    }

    @Test
    void aChestOpensWithInteractAndSpillsCoinsAndAPouch() {
        int chest = prefabs.spawn(world, "chest", 5f, 4.5f);   // its centre is where the player stands
        seconds(0.1f);
        assertThat(world.get(chest, Chest.class).open).isFalse();
        interact = true;
        seconds(DT);
        interact = false;
        assertThat(world.get(chest, Chest.class).open).isTrue();
        assertThat(world.family(Pickup.class).size()).isEqualTo(5);
        seconds(3f);   // they fly out, wait 0.45 s, then the magnet brings them in
        assertThat(campaign.gold).isEqualTo(4 * 10 + 20);
    }

    @Test
    void spikesHurtOnlyWhileUp() {
        assertThat(SpikeSystem.column(0.5f)).isEqualTo(6);
        assertThat(SpikeSystem.column(1.5f)).isEqualTo(7);
        assertThat(SpikeSystem.column(1.8f)).isEqualTo(8);
        assertThat(SpikeSystem.column(2.0f)).isEqualTo(9);
        assertThat(SpikeSystem.column(2.9f)).isEqualTo(8);
        int trap = world.create();
        world.add(trap, new Transform()).teleport(5f, 5f - 0.5f);   // the player stands on the trap's cell
        world.add(trap, new SpikeTrap());
        seconds(1.8f);
        assertThat(world.get(player, Health.class).current).as("still down").isEqualTo(20);
        seconds(0.3f);
        assertThat(world.get(trap, SpikeTrap.class).up).isTrue();
        assertThat(world.get(player, Health.class).current).as("1 damage, doubled on Normal").isEqualTo(18);
        assertThat(sounds).contains("sfx.spikes");
    }

    int door(boolean locked) {
        int leaf = world.create();
        world.add(leaf, new Transform()).teleport(5f, 7f);
        world.tag(leaf, Walls.DOOR_TAG);
        int door = world.create();
        world.add(door, new Transform()).teleport(5f, 6f);   // bottom centre of the doorway
        world.add(door, new ExitDoor()).locked = locked;
        world.get(player, Transform.class).teleport(5f, 6f - 10f * PX);
        return leaf;
    }

    @Test
    void theLockedDoorNeedsTheKeyAndWalkingThroughEndsTheLevel() {
        int leaf = door(true);
        seconds(0.1f);
        assertThat(sounds).containsOnlyOnce("sfx.locked");
        assertThat(world.isAlive(leaf)).isTrue();
        seconds(1f);
        assertThat(sounds).as("at most every 2 s").containsOnlyOnce("sfx.locked");

        world.get(player, Hero.class).keys = 1;
        seconds(0.1f);
        assertThat(world.get(player, Hero.class).keys).isZero();
        assertThat(world.isAlive(leaf)).as("the leaf no longer blocks").isFalse();
        assertThat(sounds).contains("sfx.door");
        assertThat(cleared).isEmpty();

        world.get(player, Transform.class).teleport(5f, 7.5f);   // in the doorway
        seconds(0.1f);
        assertThat(cleared).hasSize(1);
    }

    @Test
    void anUnlockedDoorOpensWithoutAKey() {
        int leaf = door(false);
        seconds(0.1f);
        assertThat(world.isAlive(leaf)).isFalse();
    }

    @Test
    void theCampaignCarriesGoldAndSavesProgress() {
        SaveData saved = new SaveData();
        campaign.gold = 50;
        campaign.startLevel();
        campaign.gold = 80;
        campaign.kills = 3;
        assertThat(campaign.levelGold()).isEqualTo(30);
        campaign.retryLevel();
        assertThat(campaign.gold).as("death: back to the level's start").isEqualTo(50);
        assertThat(campaign.kills).isZero();

        campaign.gold = 90;
        assertThat(campaign.advance(saved)).isTrue();
        assertThat(campaign.level).isEqualTo(2);
        assertThat(Campaign.canResume(saved)).isTrue();
        Campaign resumed = Campaign.resume(saved);
        assertThat(resumed.level).isEqualTo(2);
        assertThat(resumed.gold).isEqualTo(90);

        campaign.level = 3;
        campaign.gold = 200;
        assertThat(campaign.advance(saved)).as("the last level").isFalse();
        assertThat(Campaign.best(saved)).isEqualTo(200);
        assertThat(Campaign.canResume(saved)).as("finished: starts over").isFalse();
    }

    @Test
    void floatingTextsRiseAndFade() {
        assertThat(FloatingTexts.rise(0f)).isZero();
        assertThat(FloatingTexts.rise(FloatingTexts.LIFETIME)).isCloseTo(12f * PX, within(1e-6f));
        assertThat(FloatingTexts.alpha(0.5f)).isEqualTo(1f);
        assertThat(FloatingTexts.alpha(FloatingTexts.LIFETIME)).isCloseTo(0f, within(1e-5f));
        texts.spawn("KEY!", 0f, 0f, Hud.CREAM);
        texts.update(1.2f);
        assertThat(texts.size()).isZero();
    }
}
