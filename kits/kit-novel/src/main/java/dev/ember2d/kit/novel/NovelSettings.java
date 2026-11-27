package dev.ember2d.kit.novel;

import dev.ember2d.core.PublicApi;
import dev.ember2d.save.SaveData;

/** Reader settings (F-023), kept in the settings store under {@code novel.*}. */
@PublicApi
public final class NovelSettings {

    /** Characters per second of the typewriter; 0 shows lines at once. */
    public float textSpeed = 40f;
    /** Auto mode: seconds after a line is fully shown. */
    public float autoDelay = 1.5f;
    /** Skip mode also skips lines not read before. */
    public boolean skipUnread;

    /** Reads the settings ({@code context.saves().settings()}); missing values keep their defaults. */
    public NovelSettings load(SaveData settings) {
        textSpeed = Math.max(0f, settings.getFloat("novel.textSpeed", textSpeed));
        autoDelay = Math.max(0f, settings.getFloat("novel.autoDelay", autoDelay));
        skipUnread = settings.getBool("novel.skipUnread", skipUnread);
        return this;
    }

    /** Writes the settings; flush the store to keep them. */
    public void save(SaveData settings) {
        settings.set("novel.textSpeed", textSpeed).set("novel.autoDelay", autoDelay).set("novel.skipUnread", skipUnread);
    }
}
