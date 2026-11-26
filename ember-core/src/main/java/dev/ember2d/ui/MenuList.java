package dev.ember2d.ui;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import dev.ember2d.core.PublicApi;
import dev.ember2d.input.Input;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A vertical menu of buttons with a focused item (F-012, I-220), driven by input actions so bindings and contexts
 * apply: {@code ui_nav} up/down moves the focus (wrapping, skipping disabled items; held keys repeat), left/right
 * changes a value item, {@code ui_confirm} activates, {@code ui_back} runs the back action. The mouse focuses the item
 * under it; a click or tap activates. Only the menu shown last on the stage reads input (a dialog above a menu).
 */
@PublicApi
public final class MenuList extends Table {

    /** Seconds a direction is held before it repeats, and the repeat interval. */
    static final float REPEAT_DELAY = 0.35f;
    static final float REPEAT_INTERVAL = 0.1f;

    private static final class Item {
        final TextButton button;
        final Runnable action;
        final String label;
        final IntSupplier get;
        final IntConsumer set;
        final int min;
        final int max;
        final int step;

        Item(TextButton button, Runnable action, String label, IntSupplier get, IntConsumer set, int min, int max,
                int step) {
            this.button = button;
            this.action = action;
            this.label = label;
            this.get = get;
            this.set = set;
            this.min = min;
            this.max = max;
            this.step = step;
        }
    }

    private final Ui ui;
    private final Input input;
    private final Array<Item> items = new Array<>();
    private final Vector2 nav = new Vector2();
    private final StringBuilder text = new StringBuilder();
    private int focused = -1;
    private Runnable back;
    private int heldY;
    private int heldX;
    private float heldTime;
    private float nextRepeat;

    public MenuList(Ui ui) {
        super(ui.skin());
        this.ui = ui;
        this.input = ui.input();
        defaults().growX().minHeight(24f).pad(2f, 0f, 2f, 0f);
    }

    /** Adds a button running {@code action} when activated; returns it. */
    public TextButton add(String label, Runnable action) {
        return addItem(new TextButton(label, getSkin()), Objects.requireNonNull(action, "action"), label, null, null,
                0, 0, 0);
    }

    /**
     * Adds a value item shown as "label value": left/right change it by {@code step} within min..max, a click steps
     * it up and wraps.
     */
    public TextButton addValue(String label, IntSupplier get, IntConsumer set, int min, int max, int step) {
        if (min >= max || step <= 0) {
            throw new IllegalArgumentException("Value item \"" + label + "\" needs min < max and step > 0");
        }
        TextButton button = new TextButton("", getSkin());
        Item item = addItem0(button, null, label, Objects.requireNonNull(get), Objects.requireNonNull(set), min, max,
                step);
        refresh(item);
        return button;
    }

    private TextButton addItem(TextButton button, Runnable action, String label, IntSupplier get, IntConsumer set,
            int min, int max, int step) {
        addItem0(button, action, label, get, set, min, max, step);
        return button;
    }

    private Item addItem0(TextButton button, Runnable action, String label, IntSupplier get, IntConsumer set, int min,
            int max, int step) {
        Item item = new Item(button, action, label, get, set, min, max, step);
        int index = items.size;
        items.add(item);
        button.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (pointer == -1 && !button.isDisabled()) {
                    focus(index);   // mouse hover
                }
            }
        });
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!button.isDisabled()) {
                    focus(index);
                    if (item.get != null) {
                        int v = item.get.getAsInt() + item.step;
                        change(item, v > item.max ? item.min : v);
                    } else {
                        activate(index);
                    }
                }
            }
        });
        add(button).row();
        if (focused < 0 && !button.isDisabled()) {
            focus(index);
        }
        return item;
    }

    /** Runs the back action on {@code ui_back} (closing the menu, for example). */
    public void onBack(Runnable action) {
        this.back = action;
    }

    public void setDisabled(int index, boolean disabled) {
        items.get(index).button.setDisabled(disabled);
        if (disabled && focused == index) {
            move(1);
        }
    }

    public void focus(int index) {
        if (index == focused || index < 0 || index >= items.size) {
            return;
        }
        if (focused >= 0) {
            items.get(focused).button.setStyle(getSkin().get(TextButton.TextButtonStyle.class));
        }
        focused = index;
        items.get(index).button.setStyle(getSkin().get("focused", TextButton.TextButtonStyle.class));
    }

    /** The focused item's index, -1 when the menu is empty. */
    public int focused() {
        return focused;
    }

    public int size() {
        return items.size;
    }

    /** Activates an item as if confirmed. */
    public void activate(int index) {
        Item item = items.get(index);
        if (item.button.isDisabled()) {
            return;
        }
        if (item.action != null) {
            item.action.run();
        }
    }

    @Override
    protected void setStage(Stage stage) {
        super.setStage(stage);
        if (stage != null) {
            ui.menuShown(this);
        } else {
            ui.menuHidden(this);
        }
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (ui.activeMenu() != this || items.isEmpty()) {
            return;
        }
        if (input.hasAction("ui_nav")) {
            input.axis2("ui_nav", nav);
            int y = nav.y > 0.5f ? -1 : nav.y < -0.5f ? 1 : 0;   // up = previous item
            int x = nav.x > 0.5f ? 1 : nav.x < -0.5f ? -1 : 0;
            if (y != heldY || x != heldX) {
                heldY = y;
                heldX = x;
                heldTime = 0f;
                nextRepeat = REPEAT_DELAY;
                step(x, y);
            } else if (x != 0 || y != 0) {
                heldTime += delta;
                if (heldTime >= nextRepeat) {
                    nextRepeat += REPEAT_INTERVAL;
                    step(x, y);
                }
            }
        }
        if (input.hasAction("ui_confirm") && input.justPressed("ui_confirm") && focused >= 0) {
            Item item = items.get(focused);
            if (item.get != null) {
                int v = item.get.getAsInt() + item.step;
                change(item, v > item.max ? item.min : v);
            } else {
                activate(focused);
            }
        } else if (input.hasAction("ui_back") && input.justPressed("ui_back") && back != null) {
            back.run();
        }
    }

    private void step(int x, int y) {
        if (y != 0) {
            move(y);
        } else if (x != 0 && focused >= 0) {
            Item item = items.get(focused);
            if (item.get != null && !item.button.isDisabled()) {
                change(item, MathUtils.clamp(item.get.getAsInt() + x * item.step, item.min, item.max));
            }
        }
    }

    private void move(int direction) {
        for (int i = 1; i <= items.size; i++) {
            int next = Math.floorMod((focused < 0 ? 0 : focused) + direction * i, items.size);
            if (!items.get(next).button.isDisabled()) {
                focus(next);
                return;
            }
        }
    }

    private void change(Item item, int value) {
        item.set.accept(value);
        refresh(item);
    }

    private void refresh(Item item) {
        text.setLength(0);
        text.append(item.label).append("  ").append(item.get.getAsInt());
        item.button.setText(text.toString());
    }
}
