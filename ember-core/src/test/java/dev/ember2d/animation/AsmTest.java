package dev.ember2d.animation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.core.Phase;
import dev.ember2d.core.Time;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.render.Sprite;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The animation state machine (F-007, I-135): validation, conditions, evaluation, reload. No window. */
class AsmTest {

    static final String HERO = """
            { "version": 1, "clipSet": "set",
              "parameters": { "grounded": "bool", "speedX": "float", "velY": "float", "attack": "trigger", "hits": "int" },
              "initial": "idle",
              "states": {
                "idle": { "clip": "idle" },
                "run": { "clip": "run", "speed": 0.5, "speedParam": "speedX" },
                "jump": { "clip": "jump" },
                "fall": { "clip": "fall" },
                "attack": { "clip": "once3", "exitOnFinish": true },
                "spin": { "clip": "run", "speedParam": "velY" } },
              "transitions": [
                { "from": "*", "to": "attack", "when": "attack", "priority": 10 },
                { "from": "idle", "to": "run", "when": "grounded && speedX > 0.1" },
                { "from": "run", "to": "idle", "when": "grounded && speedX <= 0.1" },
                { "from": "*", "to": "jump", "when": "!grounded && velY > 0" },
                { "from": "*", "to": "fall", "when": "!grounded && velY <= 0" },
                { "from": "fall", "to": "idle", "when": "grounded" },
                { "from": "idle", "to": "jump", "when": "grounded && hits >= 3", "priority": 5 },
                { "from": "idle", "to": "fall", "when": "grounded && hits >= 3", "priority": 5 },
                { "from": "run", "to": "idle", "when": "hits == 9", "minTime": 0.5 },
                { "from": "attack", "to": "idle", "when": "hits == 7", "exitTime": 0.5 },
                { "from": "*", "to": "spin", "when": "hits == 1" } ] }
            """;

    final Map<String, JsonValue> files = new HashMap<>();
    int generation;
    final World world = new World(false);
    final DefaultEventBus bus = new DefaultEventBus(false);
    final AnimationLibrary clips = new AnimationLibrary(assets());
    final AsmLibrary library = new AsmLibrary(assets(), clips);
    final List<String> events = new ArrayList<>();
    float delta;

    final Time time = (Time) Proxy.newProxyInstance(Time.class.getClassLoader(), new Class<?>[] {Time.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "delta", "unscaledDelta" -> delta;
                case "paused" -> false;
                case "fixedDelta" -> 1f / 60f;
                case "timeScale" -> 1f;
                default -> method.getReturnType() == double.class ? 0.0 : method.getReturnType() == long.class
                        ? 0L : method.getReturnType() == float.class ? 0f : null;
            });

    AsmTest() {
        Map<String, Clip> set = new LinkedHashMap<>();
        set.put("idle", clip("idle", PlayMode.LOOP, 0.1f));
        set.put("run", clip("run", PlayMode.LOOP, 0.1f, 0.1f));
        set.put("jump", clip("jump", PlayMode.LOOP, 0.1f));
        set.put("fall", clip("fall", PlayMode.LOOP, 0.1f));
        set.put("once3", clip("once3", PlayMode.ONCE, 0.1f, 0.1f, 0.1f));
        clips.put(new ClipSet("set", set));
        files.put("asm.hero", new JsonReader().parse(HERO));
        world.addSystem(new AsmSystem(world, library, bus));
        world.addSystem(new AnimationSystem(world, clips, bus));
        bus.onGlobal(AnimationStateEntered.class, e -> events.add("+" + e.state()));
        bus.onGlobal(AnimationStateExited.class, e -> events.add("-" + e.state()));
    }

    static Clip clip(String name, PlayMode mode, float... durations) {
        TextureRegion[] frames = new TextureRegion[durations.length];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new TextureRegion();
        }
        return new Clip(name, frames, durations, new String[durations.length], mode);
    }

    int hero() {
        int e = world.create();
        world.add(e, new Sprite());
        world.add(e, new Animator());
        AnimationStateMachine m = world.add(e, new AnimationStateMachine());
        m.definition = "asm.hero";
        m.setBool("grounded", true);   // standing: otherwise "* -> fall" fires in the first update
        return e;
    }

    AnimationStateMachine asm(int e) {
        return world.get(e, AnimationStateMachine.class);
    }

    String step(int e, float seconds) {
        delta = seconds;
        world.runPhase(Phase.UPDATE, time);
        bus.flush();
        return asm(e).currentState();
    }

    @Test
    void startsInInitialAndPlaysItsClip() {
        int e = hero();
        assertThat(step(e, 0f)).isEqualTo("idle");
        assertThat(events).containsExactly("+idle");
        assertThat(asm(e).lastTransition()).isEqualTo("(start) -> idle (initial)");
        Animator a = world.get(e, Animator.class);
        assertThat(a.clipSet).isEqualTo("set");
        assertThat(a.clip).isEqualTo("idle");
    }

    @Test
    void boolAndFloatConditionsAndSpeedParam() {
        int e = hero();
        step(e, 0f);
        asm(e).setBool("grounded", true);
        asm(e).setFloat("speedX", 3f);
        assertThat(step(e, 0.1f)).isEqualTo("run");
        assertThat(world.get(e, Animator.class).clip).isEqualTo("run");
        assertThat(world.get(e, Animator.class).speed).as("0.5 x 3").isEqualTo(1.5f);
        asm(e).setFloat("speedX", 0f);
        assertThat(step(e, 0.1f)).isEqualTo("idle");
        assertThat(world.get(e, Animator.class).speed).isEqualTo(1f);

        asm(e).setInt("hits", 1);
        asm(e).setFloat("velY", -2f);
        assertThat(step(e, 0.1f)).isEqualTo("spin");
        assertThat(world.get(e, Animator.class).speed).as("speedParam uses the absolute value").isEqualTo(2f);
    }

    @Test
    void anyStateTransitionsNeverReenterTheCurrentState() {
        int e = hero();
        step(e, 0f);
        asm(e).setBool("grounded", false);
        asm(e).setFloat("velY", -1f);   // !grounded && velY <= 0
        assertThat(step(e, 0.1f)).isEqualTo("fall");
        events.clear();
        for (int i = 0; i < 5; i++) {
            assertThat(step(e, 0.1f)).isEqualTo("fall");
        }
        assertThat(events).as("no restarts").isEmpty();
        assertThat(asm(e).timeInState()).isGreaterThan(0.45f);
        asm(e).setBool("grounded", true);
        assertThat(step(e, 0.1f)).isEqualTo("idle");
    }

    @Test
    void priorityWinsAndTiesGoToTheFirstInTheFile() {
        int e = hero();
        step(e, 0f);
        asm(e).setBool("grounded", true);
        asm(e).setInt("hits", 3);
        assertThat(step(e, 0.1f)).as("jump (index 6) before fall (index 7) at priority 5").isEqualTo("jump");
        assertThat(asm(e).lastTransition()).isEqualTo("idle -> jump (transitions[6])");

        asm(e).setInt("hits", 0);
        asm(e).setBool("grounded", false);
        asm(e).setFloat("velY", 1f);
        asm(e).trigger("attack");
        assertThat(step(e, 0.1f)).as("attack, priority 10").isEqualTo("attack");
    }

    @Test
    void triggersLastOneEvaluation() {
        int e = hero();
        step(e, 0f);
        asm(e).setBool("grounded", true);
        asm(e).trigger("attack");
        assertThat(step(e, 0.1f)).isEqualTo("attack");
        assertThat(asm(e).values.get("attack", -1f)).as("cleared").isZero();
        asm(e).trigger("attack");   // in attack: * -> attack does not re-enter, so it goes unused
        step(e, 0.1f);
        for (int i = 0; i < 5; i++) {
            step(e, 0.1f);
        }
        assertThat(asm(e).currentState()).as("back to idle; the unused trigger did not attack again")
                .isEqualTo("idle");
    }

    @Test
    void exitOnFinishReturnsToInitialAndReevaluatesTheSameUpdate() {
        int e = hero();
        step(e, 0f);
        asm(e).trigger("attack");
        step(e, 0f);      // enters attack; the clip restarts
        step(e, 0.1f);    // clip frame 1
        step(e, 0.1f);    // frame 2
        assertThat(step(e, 0.1f)).as("the clip just finished").isEqualTo("attack");
        asm(e).setBool("grounded", true);
        asm(e).setFloat("speedX", 2f);
        events.clear();
        assertThat(step(e, 0.1f)).as("attack -> idle (finished) -> run in one update").isEqualTo("run");
        assertThat(events).containsExactly("-attack", "+idle", "-idle", "+run");
    }

    @Test
    void minTimeAndExitTime() {
        int e = hero();
        step(e, 0f);
        asm(e).setBool("grounded", true);
        asm(e).setFloat("speedX", 2f);
        step(e, 0.1f);    // run
        asm(e).setInt("hits", 9);
        assertThat(step(e, 0.3f)).as("0.3 s in run < minTime 0.5").isEqualTo("run");
        assertThat(step(e, 0.3f)).isEqualTo("idle");

        asm(e).setInt("hits", 7);
        asm(e).setFloat("speedX", 0f);
        asm(e).trigger("attack");
        step(e, 0f);      // enters attack
        assertThat(step(e, 0.1f)).as("clip time 0 when evaluated").isEqualTo("attack");
        assertThat(step(e, 0.1f)).as("0.1 / 0.3 < 0.5").isEqualTo("attack");
        assertThat(step(e, 0.1f)).as("0.2 / 0.3 >= 0.5").isEqualTo("idle");
    }

    @Test
    void parameterMisuseIsReported() {
        int e = hero();
        asm(e).setFloat("grounded", 1f);   // before the definition is loaded: checked on the first update
        assertThatThrownBy(() -> step(e, 0f)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\"grounded\"").hasMessageContaining("is a bool, not a float");

        int ok = hero();
        world.destroy(e);
        step(ok, 0f);
        assertThatThrownBy(() -> asm(ok).setBool("flying", true)).hasMessageContaining("has no parameter \"flying\"");
        assertThatThrownBy(() -> asm(ok).setInt("speedX", 1)).hasMessageContaining("is a float, not a int");
    }

    @Test
    void reloadKeepsStateAndParametersThatStillFit() {
        int e = hero();
        step(e, 0f);
        asm(e).setBool("grounded", true);
        asm(e).setFloat("speedX", 2f);
        assertThat(step(e, 0.1f)).isEqualTo("run");

        library.reload("asm.hero", new JsonReader().parse(HERO.replace("\"run\": { \"clip\": \"run\", \"speed\": 0.5, "
                + "\"speedParam\": \"speedX\" },", "").replace("{ \"from\": \"idle\", \"to\": \"run\", "
                + "\"when\": \"grounded && speedX > 0.1\" },", "").replace("{ \"from\": \"run\", \"to\": \"idle\", "
                + "\"when\": \"grounded && speedX <= 0.1\" },", "").replace("{ \"from\": \"run\", \"to\": \"idle\", "
                + "\"when\": \"hits == 9\", \"minTime\": 0.5 },", "")));
        assertThat(step(e, 0.1f)).as("run is gone: back to initial").isEqualTo("idle");
        assertThat(asm(e).getBool("grounded")).as("kept").isTrue();

        assertThat(catchThrowableOfType(AnimationException.class,
                () -> library.reload("asm.hero", new JsonReader().parse("{ \"version\": 1 }")))).isNotNull();
        assertThat(library.definition("asm.hero").stateNames()).as("a bad reload keeps the old one")
                .doesNotContain("run").contains("idle");
    }

    @Test
    void invalidDefinitionsNameFileStateAndTransition() {
        files.put("asm.bad", new JsonReader().parse("""
                { "version": 1, "clipSet": "set", "extra": 1,
                  "parameters": { "on": "bool", "n": "float", "t": "trigger", "x": "colour" },
                  "initial": "nowhere",
                  "states": { "a": { "clip": "idle", "speedParam": "on", "sped": 1 }, "b": { "clip": "walk" },
                              "c": { "clip": "idle", "speed": -1, "exitOnFinish": "yes" } },
                  "transitions": [
                    { "from": "a", "to": "zz", "when": "on && n > 1" },
                    { "from": "q", "to": "b", "when": "on > 1 && !t && n && missing" },
                    { "from": "a", "to": "b", "when": "n >> 2", "priority": 1.5, "minTime": -1 } ] }
                """));

        AnimationException e = catchThrowableOfType(AnimationException.class, () -> library.definition("asm.bad"));

        String f = "asm/asm.bad.json: ";
        assertThat(e.problems()).containsExactlyInAnyOrder(
                f + "extra: unknown field (expected version, clipSet, parameters, initial, states, transitions)",
                f + "parameters.x: type must be \"bool\", \"float\", \"int\" or \"trigger\"",
                f + "states.a.sped: unknown field (expected clip, speed, speedParam, exitOnFinish)",
                f + "states.a.speedParam: \"on\" must be a float or int parameter",
                f + "states.b.clip: unknown clip \"walk\" in clip set \"set\" [idle, run, jump, fall, once3]",
                f + "states.c.speed: must be a number >= 0",
                f + "states.c.exitOnFinish: must be true or false",
                f + "initial: unknown state \"nowhere\"",
                f + "transitions[0].to: unknown state \"zz\"",
                f + "transitions[1].from: unknown state \"q\"",
                f + "transitions[1].when: \"on\" is a bool and cannot be compared; write \"on\" or \"!on\"",
                f + "transitions[1].when: a trigger cannot be negated (\"!t\")",
                f + "transitions[1].when: \"n\" is a number; compare it, for example \"n > 0\"",
                f + "transitions[1].when: unknown parameter \"missing\"",
                f + "transitions[2].when: cannot read \"n >> 2\" (expected name, !name or name OP number, "
                        + "joined by &&)",
                f + "transitions[2].priority: must be a whole number",
                f + "transitions[2].minTime: must be a number >= 0");
    }

    @Test
    void aHotReloadedDefinitionIsPickedUpAndABrokenOneKeepsTheOld() {
        AsmDefinition before = library.definition("asm.hero");
        files.put("asm.hero", new JsonReader().parse(HERO.replace("\"idle\": { \"clip\": \"idle\" },",
                "\"idle\": { \"clip\": \"idle\" }, \"extra\": { \"clip\": \"idle\" },")));
        generation++;

        AsmDefinition after = library.definition("asm.hero");
        assertThat(after).isNotSameAs(before);
        assertThat(after.stateNames()).contains("extra");

        files.put("asm.hero", new JsonReader().parse("{ \"version\": 1 }"));
        generation++;
        assertThat(library.definition("asm.hero")).as("broken: the old one stays").isSameAs(after);
    }

    private Assets assets() {
        return new Assets() {
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
                return "asm/" + id + ".json";
            }

            @Override
            public int generation() {
                return generation;
            }
        };
    }
}
