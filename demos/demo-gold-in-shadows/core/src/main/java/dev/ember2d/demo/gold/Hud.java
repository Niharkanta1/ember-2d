package dev.ember2d.demo.gold;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.ember2d.assets.Assets;
import dev.ember2d.ui.Ui;
import dev.ember2d.ui.UiSkins;

/**
 * The in-game HUD (F-029), laid out like the original's {@code HudRenderer} in its pixel font and palette: hearts top
 * left (maroon) with the key below, gold top right (tan), creatures left bottom left, ammunition bottom right, and the
 * white hit flash (35%, fading over 0.2 s). The icons are characters of the original font: dollar = coin, opening
 * brace = full heart, tilde = half heart, closing brace = empty heart, hash = skull, bar = bullet, ampersand = key.
 */
final class Hud {

    static final Color DARK = Color.valueOf("181830");
    static final Color MAROON = Color.valueOf("8b3f4f");
    static final Color TAN = Color.valueOf("e4a06d");
    static final Color CREAM = Color.valueOf("f7e6c3");
    static final float FLASH_SECONDS = 0.2f;
    static final float FLASH_OPACITY = 0.35f;

    private final Label hearts;
    private final Label key;
    private final Label gold;
    private final Label creatures;
    private final Label ammo;
    private final Label hint;
    private final Image flash;
    private float sinceHit = FLASH_SECONDS;

    Hud(Ui ui, Assets assets) {
        BitmapFont font = assets.get("font.pixel", BitmapFont.class);
        ui.setSkin(UiSkins.pixel(font), true);
        flash = new Image(ui.skin().getDrawable("white"));
        flash.setFillParent(true);
        flash.setColor(1f, 1f, 1f, 0f);
        flash.setVisible(false);
        flash.setTouchable(Touchable.disabled);
        ui.stage().addActor(flash);

        hearts = label(ui, MAROON);
        key = label(ui, CREAM);
        gold = label(ui, TAN);
        creatures = label(ui, TAN);
        ammo = label(ui, CREAM);
        hint = label(ui, CREAM);
        Table root = ui.root();
        root.top().pad(3f, 4f, 3f, 4f);
        Table top = new Table();
        top.add(hearts).left().expandX();
        top.add(gold).right();
        root.add(top).growX().row();
        root.add(key).left().row();
        root.add().expand().row();
        root.add(hint).padBottom(2f).row();
        Table bottom = new Table();
        bottom.add(creatures).left().expandX();
        bottom.add(ammo).right();
        root.add(bottom).growX();
        root.setTouchable(Touchable.disabled);
    }

    private static Label label(Ui ui, Color color) {
        Label label = new Label("", ui.skin());
        label.setColor(color);
        return label;
    }

    /** Health in quarter hearts: each heart is 4. */
    static String hearts(int current, int max) {
        StringBuilder out = new StringBuilder();
        int count = (max + 3) / 4;
        for (int i = 0; i < count; i++) {
            int left = current - i * 4;
            out.append(left >= 4 ? '{' : left >= 2 ? '~' : '}');
        }
        return out.toString();
    }

    /** Sets everything shown; call once per frame. */
    void update(float delta, int health, int maxHealth, int goldCount, int keys, int creaturesLeft, int ammoLeft,
            int magazine, boolean reloading) {
        hearts.setText(hearts(health, maxHealth));
        key.setText(keys > 0 ? "& x" + keys : "");
        gold.setText("$ " + goldCount);
        creatures.setText("# " + creaturesLeft);
        ammo.setText(reloading ? "| ..." : "| " + ammoLeft + "/" + magazine);
        ammo.setColor(!reloading && ammoLeft == 0 ? MAROON : CREAM);
        if (sinceHit < FLASH_SECONDS) {
            sinceHit += delta;
            flash.setVisible(true);
            flash.setColor(1f, 1f, 1f, FLASH_OPACITY * Math.max(0f, 1f - sinceHit / FLASH_SECONDS));
        } else {
            flash.setVisible(false);
        }
    }

    /** A short line above the bottom row (for example "E: open"); empty hides it. */
    void hint(String text) {
        hint.setText(text);
    }

    /** Starts the white hit flash. */
    void flash() {
        sinceHit = 0f;
    }
}
