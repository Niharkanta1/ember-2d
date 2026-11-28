package dev.ember2d.ui;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import dev.ember2d.core.PublicApi;
import dev.ember2d.input.Input;
import dev.ember2d.input.InputDevice;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The "press a key" rebinding screen (F-005, I-535): a full-screen panel listing actions and axis directions with the
 * current inputs of one device (Keyboard or Gamepad). Activating a row listens for the next key or pad input on that
 * device for {@value #LISTEN_SECONDS} seconds; the new input replaces that device's inputs of the row and keeps the
 * rest (the other device, touch, mouse). Inputs shared with another row are reported, not removed. Changes apply at
 * once; saving them is the game's job in {@code onDone} ({@code context.saveSettings()}).
 */
@PublicApi
public final class RebindMenu {

    /** How long a row waits for an input. */
    public static final float LISTEN_SECONDS = 5f;

    /** One row: an action, or one direction of an axis. Make it with {@link #action} or {@link #axis}. */
    public record Entry(String action, String axis, String direction, String label) {

        public Entry {
            Objects.requireNonNull(label, "label");
            if ((action == null) == (axis == null) || (axis != null && direction == null)) {
                throw new IllegalArgumentException("An entry is an action or an axis direction: " + label);
            }
        }
    }

    /** A row for an action, such as {@code action("jump", "Jump")}. */
    public static Entry action(String action, String label) {
        return new Entry(Objects.requireNonNull(action, "action"), null, null, label);
    }

    /** A row for an axis direction, such as {@code axis("move", "left", "Move left")}. */
    public static Entry axis(String axis, String direction, String label) {
        return new Entry(null, Objects.requireNonNull(axis, "axis"), Objects.requireNonNull(direction, "direction"),
                label);
    }

    private static final Map<String, String> PAD_LABELS = Map.ofEntries(
            Map.entry("PAD_A", "A"), Map.entry("PAD_B", "B"), Map.entry("PAD_X", "X"), Map.entry("PAD_Y", "Y"),
            Map.entry("PAD_BACK", "View"), Map.entry("PAD_START", "Start"),
            Map.entry("PAD_L1", "LB"), Map.entry("PAD_R1", "RB"), Map.entry("PAD_L2", "LT"), Map.entry("PAD_R2", "RT"),
            Map.entry("PAD_L3", "LS click"), Map.entry("PAD_R3", "RS click"),
            Map.entry("PAD_UP", "D-pad up"), Map.entry("PAD_DOWN", "D-pad down"),
            Map.entry("PAD_LEFT", "D-pad left"), Map.entry("PAD_RIGHT", "D-pad right"),
            Map.entry("PAD_LSTICK_LEFT", "L-stick left"), Map.entry("PAD_LSTICK_RIGHT", "L-stick right"),
            Map.entry("PAD_LSTICK_UP", "L-stick up"), Map.entry("PAD_LSTICK_DOWN", "L-stick down"),
            Map.entry("PAD_RSTICK_LEFT", "R-stick left"), Map.entry("PAD_RSTICK_RIGHT", "R-stick right"),
            Map.entry("PAD_RSTICK_UP", "R-stick up"), Map.entry("PAD_RSTICK_DOWN", "R-stick down"));

    /** A readable name for a binding name: {@code SPACE} → "Space", {@code L_SHIFT} → "L Shift", {@code PAD_A} → "A". */
    public static String label(String name) {
        String pad = PAD_LABELS.get(name);
        if (pad != null) {
            return pad;
        }
        StringBuilder out = new StringBuilder();
        for (String word : name.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private final Ui ui;
    private final Input input;
    private final List<Entry> entries;
    private final Runnable onDone;
    private final Table overlay;
    private final MenuList menu;
    private final ScrollPane scroll;
    private final TextButton deviceButton;
    private final List<TextButton> rows = new ArrayList<>();
    private final Label status;
    private final Rectangle focusBounds = new Rectangle();
    private InputDevice device;
    private int listeningRow = -1;
    private float listenTime;
    private int lastFocused = -1;

    private RebindMenu(Ui ui, List<Entry> entries, Runnable onDone) {
        this.ui = ui;
        this.input = ui.input();
        this.entries = List.copyOf(entries);
        this.onDone = Objects.requireNonNull(onDone, "onDone");
        this.device = input.gamepadConnected() ? InputDevice.GAMEPAD : InputDevice.KEYBOARD;

        overlay = new Table(ui.skin()) {
            @Override
            public void act(float delta) {
                super.act(delta);
                tick(delta);
            }
        };
        overlay.setFillParent(true);
        overlay.setTouchable(Touchable.enabled);   // the scene below gets no clicks
        overlay.setBackground(ui.skin().newDrawable("white", 0f, 0f, 0f, 0.6f));   // dims what is below
        Table panel = new Table(ui.skin());
        panel.setBackground("panel");
        panel.pad(4f, 8f, 4f, 8f);
        panel.add(new Label("Controls", ui.skin(), "title")).padBottom(2f).row();

        menu = new MenuList(ui);
        deviceButton = menu.add("", this::switchDevice);
        for (int i = 0; i < this.entries.size(); i++) {
            int row = i;
            rows.add(menu.add("", () -> listen(row)));
        }
        menu.add("Reset all", this::resetAll);
        menu.add("Done", this::close);
        menu.onBack(this::close);
        scroll = new ScrollPane(menu);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        panel.add(scroll).growX().height(Math.max(48f, ui.height() - 64f)).row();
        status = new Label("", ui.skin(), "dim");
        panel.add(status).padTop(2f);
        overlay.add(panel).minWidth(Math.min(260f, ui.width() - 8f));
        ui.stage().addActor(overlay);
        refresh();
        status.setText("Choose a control to change it");
    }

    /**
     * Shows the rebinding screen above everything else on the UI stage.
     *
     * @param entries the rows, in order
     * @param onDone called after Done or {@code ui_back}, when the screen has closed; save the settings here
     */
    public static RebindMenu open(Ui ui, List<Entry> entries, Runnable onDone) {
        Objects.requireNonNull(ui, "ui");
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("RebindMenu needs at least one entry");
        }
        return new RebindMenu(ui, entries, onDone);
    }

    /** The device whose inputs are shown and rebound. */
    public InputDevice device() {
        return device;
    }

    /** True while a row waits for an input. */
    public boolean isListening() {
        return listeningRow >= 0;
    }

    /** The status line (the last message). */
    public String status() {
        return status.getText().toString();
    }

    /** The text of row {@code i} (tests and screenshots). */
    String rowText(int i) {
        return rows.get(i).getText().toString();
    }

    MenuList menu() {
        return menu;
    }

    private void switchDevice() {
        cancelListening();
        device = device == InputDevice.KEYBOARD ? InputDevice.GAMEPAD : InputDevice.KEYBOARD;
        refresh();
        status.setText(device == InputDevice.GAMEPAD && !input.gamepadConnected()
                ? "No gamepad connected" : "Choose a control to change it");
    }

    private void listen(int row) {
        listeningRow = row;
        listenTime = 0f;
        refresh();
        String what = device == InputDevice.GAMEPAD ? "a button" : "a key";
        status.setText("Press " + what + " for " + entries.get(row).label() + " (Esc cancels)");
        input.listenForInput(EnumSet.of(device), name -> assign(row, name), () -> {
            listeningRow = -1;
            refresh();
            status.setText("Unchanged");
        });
    }

    private void cancelListening() {
        if (listeningRow >= 0) {
            input.stopListening();
            listeningRow = -1;
        }
    }

    /** The new input replaces this device's inputs of the row; other devices' inputs stay. */
    void assign(int row, String name) {
        listeningRow = -1;
        Entry entry = entries.get(row);
        List<String> next = new ArrayList<>();
        for (String existing : inputs(entry)) {
            if (InputDevice.of(existing) != device) {
                next.add(existing);
            }
        }
        if (!next.contains(name)) {
            next.add(name);
        }
        if (entry.action() != null) {
            input.rebindAction(entry.action(), next);
        } else {
            input.rebindAxis(entry.axis(), entry.direction(), next);
        }
        List<String> others = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (i != row && inputs(entries.get(i)).contains(name)) {
                others.add(entries.get(i).label());
            }
        }
        refresh();
        status.setText(others.isEmpty() ? entry.label() + ": " + label(name)
                : label(name) + " is also: " + String.join(", ", others));
    }

    private void resetAll() {
        cancelListening();
        input.resetAllBindings();
        refresh();
        status.setText("All controls reset");
    }

    private void close() {
        cancelListening();
        overlay.remove();
        onDone.run();
    }

    private void tick(float delta) {
        if (listeningRow >= 0) {
            listenTime += delta;
            if (listenTime >= LISTEN_SECONDS) {
                cancelListening();
                refresh();
                status.setText("No input: unchanged");
            }
        }
        int focused = menu.focused();
        if (focused != lastFocused && focused >= 0) {   // keep the focused row in view
            lastFocused = focused;
            TextButton button = (TextButton) menu.getChildren().get(focused);
            focusBounds.set(button.getX(), button.getY(), button.getWidth(), button.getHeight());
            scroll.scrollTo(focusBounds.x, focusBounds.y, focusBounds.width, focusBounds.height);
        }
    }

    private List<String> inputs(Entry e) {
        return e.action() != null ? input.actionInputs(e.action()) : input.axisInputs(e.axis(), e.direction());
    }

    private void refresh() {
        deviceButton.setText("Device: " + (device == InputDevice.GAMEPAD ? "Gamepad" : "Keyboard"));
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            String value;
            if (i == listeningRow) {
                value = device == InputDevice.GAMEPAD ? "press a button..." : "press a key...";
            } else {
                List<String> names = new ArrayList<>();
                for (String name : inputs(e)) {
                    if (InputDevice.of(name) == device) {
                        names.add(label(name));
                    }
                }
                value = names.isEmpty() ? "-" : String.join(", ", names);
            }
            rows.get(i).setText(e.label() + ":  " + value);
        }
    }
}
