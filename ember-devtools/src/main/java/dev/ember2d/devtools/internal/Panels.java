package dev.ember2d.devtools.internal;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.animation.AnimationStateMachine;
import dev.ember2d.animation.Animator;
import dev.ember2d.assets.Assets;
import dev.ember2d.core.Time;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.FieldInfo;
import dev.ember2d.debug.DebugOverlay;
import dev.ember2d.ecs.World;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The dev tools panels (F-018, I-160), drawn with Dear ImGui after the game's frame. Render thread only. */
final class Panels {

    private final DevState state;
    private final ImString entityFilter = new ImString(64);
    private final ImString eventFilter = new ImString(64);
    private final Map<String, ImString> textBuffers = new HashMap<>();
    private final Map<Class<?>, Set<String>> settable = new HashMap<>();
    private final IntArray ids = new IntArray();
    private final List<Object> components = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();
    private final ImInt intValue = new ImInt();
    private final ImDouble doubleValue = new ImDouble();
    private final float[] f1 = new float[1];
    private final float[] f2 = new float[2];
    private final float[] f4 = new float[4];
    private int bufferedEntity = World.NONE;

    Panels(DevState state) {
        this.state = state;
    }

    void draw() {
        menuBar();
        if (state.entitiesOpen.get()) {
            entities();
        }
        if (state.inspectorOpen.get()) {
            inspector();
        }
        if (state.animationOpen.get()) {
            animation();
        }
        if (state.timeOpen.get()) {
            time();
        }
        if (state.frameOpen.get()) {
            frame();
        }
        if (state.debugOpen.get()) {
            debugDraw();
        }
        if (state.eventsOpen.get()) {
            events();
        }
        if (state.assetsOpen.get()) {
            assets();
        }
    }

    private void menuBar() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("Panels")) {
                ImGui.menuItem("Entities", "", state.entitiesOpen);
                ImGui.menuItem("Inspector", "", state.inspectorOpen);
                ImGui.menuItem("Animation", "", state.animationOpen);
                ImGui.menuItem("Time", "", state.timeOpen);
                ImGui.menuItem("Frame", "", state.frameOpen);
                ImGui.menuItem("Debug draw", "", state.debugOpen);
                ImGui.menuItem("Events", "", state.eventsOpen);
                ImGui.menuItem("Assets", "", state.assetsOpen);
                ImGui.endMenu();
            }
            ImGui.textDisabled("F1 hides the dev tools");
            ImGui.endMainMenuBar();
        }
    }

    // ---- Entities ----

    private void entities() {
        ImGui.setNextWindowPos(10, 30, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(300, 360, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Entities", state.entitiesOpen)) {
            World world = state.world();
            if (world == null) {
                ImGui.textDisabled("No scene");
            } else {
                ImGui.inputTextWithHint("##filter", "filter: name, tag or component", entityFilter);
                String filter = entityFilter.get().trim().toLowerCase(Locale.ROOT);
                ids.clear();
                world.entities(ids);
                ImGui.text(ids.size + " entities");
                ImGui.separator();
                if (ImGui.beginChild("list")) {
                    for (int i = 0; i < ids.size; i++) {
                        int e = ids.get(i);
                        String label = describe(world, e);
                        if (!filter.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(filter)) {
                            continue;
                        }
                        if (ImGui.selectable(label + "##" + e, e == state.selected)) {
                            state.selected = e;
                        }
                    }
                }
                ImGui.endChild();
            }
        }
        ImGui.end();
    }

    private String describe(World world, int e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e & 0xFFFFF);
        String name = world.name(e);
        if (name != null) {
            sb.append(' ').append(name);
        }
        tags.clear();
        world.tags(e, tags);
        for (String t : tags) {
            sb.append(" #").append(t);
        }
        components.clear();
        world.components(e, components);
        sb.append("  [");
        for (int i = 0; i < components.size(); i++) {
            sb.append(i == 0 ? "" : ", ").append(components.get(i).getClass().getSimpleName());
        }
        return sb.append(']').toString();
    }

    // ---- Inspector ----

    private void inspector() {
        ImGui.setNextWindowPos(320, 30, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(380, 460, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Inspector", state.inspectorOpen)) {
            int e = state.selection();
            World world = state.world();
            if (e == World.NONE) {
                ImGui.textDisabled("Select an entity in the Entities panel");
            } else {
                if (e != bufferedEntity) {
                    textBuffers.clear();
                    bufferedEntity = e;
                }
                String name = world.name(e);
                ImGui.text("Entity " + (e & 0xFFFFF) + (name != null ? " \"" + name + "\"" : ""));
                if (ImGui.button("Copy as prefab JSON")) {
                    Gdx.app.getClipboard().setContents(PrefabJson.of(world, e, registry()));
                }
                ImGui.separator();
                components.clear();
                world.components(e, components);
                List<Object> snapshot = new ArrayList<>(components);
                for (int c = 0; c < snapshot.size(); c++) {
                    Object component = snapshot.get(c);
                    ImGui.pushID(c);
                    if (ImGui.collapsingHeader(component.getClass().getSimpleName())) {
                        for (FieldAccess.FieldView field : FieldAccess.fields(component, settable(component))) {
                            field(component, field);
                        }
                    }
                    ImGui.popID();
                }
            }
        }
        ImGui.end();
    }

    private ComponentRegistry registry() {
        return state.context.prefabs().registry();
    }

    /** Names the registry can set for this component's class, or null when it is not registered. */
    private Set<String> settable(Object component) {
        return settable.computeIfAbsent(component.getClass(), type -> {
            ComponentRegistry registry = registry();
            List<Class<?>> types = registry.types();
            List<String> names = new ArrayList<>(registry.names());
            int i = types.indexOf(type);
            if (i < 0) {
                return null;
            }
            Set<String> out = new HashSet<>();
            for (FieldInfo info : registry.fields(names.get(i))) {
                out.add(info.name());
            }
            return out;
        });
    }

    private void field(Object component, FieldAccess.FieldView field) {
        Object value = field.get(component);
        String label = field.name();
        if (!field.editable()) {
            ImGui.textDisabled(label + ": " + value);
            return;
        }
        switch (field.kind()) {
            case INT -> {
                intValue.set((Integer) value);
                if (ImGui.inputInt(label, intValue)) {
                    field.set(component, intValue.get());
                }
            }
            case FLOAT -> {
                f1[0] = (Float) value;
                if (ImGui.dragFloat(label, f1, 0.05f)) {
                    field.set(component, f1[0]);
                }
            }
            case DOUBLE -> {
                doubleValue.set((Double) value);
                if (ImGui.inputDouble(label, doubleValue)) {
                    field.set(component, doubleValue.get());
                }
            }
            case BOOLEAN -> {
                boolean b = (Boolean) value;
                if (ImGui.checkbox(label, b)) {
                    field.set(component, !b);
                }
            }
            case LONG, STRING -> textField(component, field, value);
            case ENUM -> {
                Object[] constants = field.field().getType().getEnumConstants();
                String[] names = new String[constants.length];
                int current = 0;
                for (int i = 0; i < constants.length; i++) {
                    names[i] = ((Enum<?>) constants[i]).name();
                    if (constants[i] == value) {
                        current = i;
                    }
                }
                intValue.set(current);
                if (ImGui.combo(label, intValue, names)) {
                    field.set(component, constants[intValue.get()]);
                }
            }
            case COLOR -> {
                Color c = (Color) value;
                f4[0] = c.r;
                f4[1] = c.g;
                f4[2] = c.b;
                f4[3] = c.a;
                if (ImGui.colorEdit4(label, f4)) {
                    c.set(f4[0], f4[1], f4[2], f4[3]);
                }
            }
            case VECTOR2 -> {
                Vector2 v = (Vector2) value;
                f2[0] = v.x;
                f2[1] = v.y;
                if (ImGui.dragFloat2(label, f2, 0.05f)) {
                    v.set(f2[0], f2[1]);
                }
            }
            case RECTANGLE -> {
                Rectangle r = (Rectangle) value;
                f4[0] = r.x;
                f4[1] = r.y;
                f4[2] = r.width;
                f4[3] = r.height;
                if (ImGui.dragFloat4(label, f4, 0.05f)) {
                    r.set(f4[0], f4[1], f4[2], f4[3]);
                }
            }
            default -> ImGui.textDisabled(label + ": " + value);
        }
    }

    /** Text and long fields: typed into a kept buffer, applied with Enter. */
    private void textField(Object component, FieldAccess.FieldView field, Object value) {
        String key = System.identityHashCode(component) + "/" + field.name();
        ImString buffer = textBuffers.computeIfAbsent(key, k -> new ImString(String.valueOf(value), 256));
        if (ImGui.inputText(field.name(), buffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
            String text = buffer.get();
            if (field.kind() == FieldAccess.Kind.STRING) {
                field.set(component, text.equals("null") ? null : text);
            } else {
                try {
                    field.set(component, Long.parseLong(text.trim()));
                } catch (NumberFormatException ex) {
                    buffer.set(String.valueOf(value));
                }
            }
        } else if (!ImGui.isItemActive()) {
            buffer.set(String.valueOf(value));   // follow changes made by the game while not typing
        }
    }

    // ---- Animation ----

    private void animation() {
        ImGui.setNextWindowPos(710, 30, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(300, 300, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Animation", state.animationOpen)) {
            int e = state.selection();
            World world = state.world();
            AnimationStateMachine asm = e == World.NONE ? null : world.get(e, AnimationStateMachine.class);
            Animator animator = e == World.NONE ? null : world.get(e, Animator.class);
            if (asm == null && animator == null) {
                ImGui.textDisabled("Select an entity with an Animator or AnimationStateMachine");
            }
            if (animator != null) {
                ImGui.text("Clip: " + animator.clipSet + " / " + animator.clip + "  frame " + animator.frame());
            }
            if (asm != null) {
                ImGui.text("Definition: " + asm.definition);
                ImGui.text("State: " + asm.currentState() + String.format(Locale.ROOT, "  (%.2f s)", asm.timeInState()));
                ImGui.textWrapped("Last transition: " + asm.lastTransition());
                ImGui.separatorText("Parameters");
                for (Map.Entry<String, String> p : asm.parameterTypes().entrySet()) {
                    String n = p.getKey();
                    switch (p.getValue()) {
                        case "bool" -> {
                            boolean b = asm.getBool(n);
                            if (ImGui.checkbox(n, b)) {
                                asm.setBool(n, !b);
                            }
                        }
                        case "float" -> {
                            f1[0] = asm.getFloat(n);
                            if (ImGui.dragFloat(n, f1, 0.05f)) {
                                asm.setFloat(n, f1[0]);
                            }
                        }
                        case "int" -> {
                            intValue.set(asm.getInt(n));
                            if (ImGui.inputInt(n, intValue)) {
                                asm.setInt(n, intValue.get());
                            }
                        }
                        default -> {
                            if (ImGui.button("Fire " + n)) {
                                asm.trigger(n);
                            }
                        }
                    }
                }
            }
        }
        ImGui.end();
    }

    // ---- Time ----

    private void time() {
        ImGui.setNextWindowPos(10, 400, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(300, 170, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Time", state.timeOpen)) {
            Time time = state.context.time();
            boolean paused = time.paused();
            if (ImGui.checkbox("Paused", paused)) {
                time.setPaused(!paused);
            }
            ImGui.sameLine();
            if (!paused) {
                ImGui.beginDisabled();
            }
            if (ImGui.button("Step one tick")) {
                time.step();
            }
            if (!paused) {
                ImGui.endDisabled();
            }
            f1[0] = time.timeScale();
            if (ImGui.sliderFloat("Time scale", f1, 0f, 4f, "%.2f")) {
                time.setTimeScale(f1[0]);
            }
            ImGui.text("Fixed tick: " + time.fixedTick());
            ImGui.text("Dropped fixed steps: " + time.droppedFixedSteps());
            ImGui.text(String.format(Locale.ROOT, "Game time: %.2f s", time.elapsed()));
        }
        ImGui.end();
    }

    // ---- Frame ----

    private void frame() {
        ImGui.setNextWindowPos(320, 500, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(380, 200, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Frame", state.frameOpen)) {
            FrameStats stats = state.frames;
            float avg = stats.averageFrame();
            ImGui.text(String.format(Locale.ROOT, "FPS %d   frame %.2f ms (max %.2f)   engine %.2f ms",
                    Gdx.graphics.getFramesPerSecond(), avg, stats.maxFrame(), stats.averageEngine()));
            World world = state.world();
            ImGui.text("Entities: " + (world == null ? 0 : world.entityCount()));
            ImGui.plotLines("##frames", stats.frameTimes(), stats.size(), 0, "frame ms", 0f,
                    Math.max(20f, stats.maxFrame()), ImGui.getContentRegionAvailX(), 100f);
        }
        ImGui.end();
    }

    // ---- Debug draw ----

    private void debugDraw() {
        ImGui.setNextWindowPos(710, 340, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(300, 90, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Debug draw", state.debugOpen)) {
            DebugOverlay debug = state.context.debug();
            if (!debug.isAvailable()) {
                ImGui.textDisabled("The debug overlay is off in this build");
            } else {
                boolean visible = debug.isVisible();
                if (ImGui.checkbox("Colliders, triggers and frame panel (F3)", visible)) {
                    debug.setVisible(!visible);
                }
            }
        }
        ImGui.end();
    }

    // ---- Events ----

    private void events() {
        ImGui.setNextWindowPos(710, 440, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(460, 260, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Events", state.eventsOpen)) {
            EventLog log = state.events;
            ImGui.inputTextWithHint("##eventFilter", "filter by type", eventFilter);
            ImGui.sameLine();
            boolean paused = log.isPaused();
            if (ImGui.checkbox("Pause log", paused)) {
                log.setPaused(!paused);
            }
            ImGui.sameLine();
            if (ImGui.button("Clear")) {
                log.clear();
            }
            if (ImGui.beginChild("events")) {
                for (EventLog.Entry entry : log.entries(eventFilter.get().trim())) {
                    ImGui.text(entry.frame() + "  " + entry.type() + "  " + entry.text());
                }
            }
            ImGui.endChild();
        }
        ImGui.end();
    }

    // ---- Assets ----

    private void assets() {
        ImGui.setNextWindowPos(1020, 30, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(440, 400, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Assets", state.assetsOpen)) {
            ImGui.separatorText("Hot reload");
            if (state.reloads == null) {
                ImGui.textDisabled("Off (no watcher: run with gradlew run)");
            }
            if (state.lastReloadError != null) {
                ImGui.textColored(1f, 0.4f, 0.4f, 1f, "Last error:");
                ImGui.textWrapped(state.lastReloadError);
            }
            for (String line : state.reloadHistory) {
                ImGui.text(line);
            }
            ImGui.separatorText("Manifest");
            Assets assets = state.context.assets();
            int flags = ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.ScrollY
                    | ImGuiTableFlags.Resizable;
            if (ImGui.beginTable("assets", 4, flags)) {
                ImGui.tableSetupColumn("ID");
                ImGui.tableSetupColumn("Path");
                ImGui.tableSetupColumn("Loaded");
                ImGui.tableSetupColumn("");
                ImGui.tableHeadersRow();
                for (String id : assets.ids()) {
                    boolean loaded = assets.isLoaded(id);
                    ImGui.tableNextRow();
                    ImGui.tableNextColumn();
                    ImGui.text(id);
                    ImGui.tableNextColumn();
                    ImGui.text(assets.path(id));
                    ImGui.tableNextColumn();
                    ImGui.text(loaded ? "yes" : "no");
                    ImGui.tableNextColumn();
                    if (loaded && state.reloads != null && ImGui.button("Reload##" + id)) {
                        state.reloads.request(assets.path(id));
                    }
                }
                ImGui.endTable();
            }
        }
        ImGui.end();
    }
}
