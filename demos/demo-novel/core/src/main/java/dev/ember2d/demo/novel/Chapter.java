package dev.ember2d.demo.novel;

import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.app.EngineContext;
import dev.ember2d.core.Log;
import dev.ember2d.kit.common.DialogueRunner;
import dev.ember2d.kit.novel.NovelCharacters;
import dev.ember2d.kit.novel.NovelDirector;
import dev.ember2d.kit.novel.NovelMemory;
import dev.ember2d.kit.novel.StageListener;
import dev.ember2d.save.LoadResult;
import dev.ember2d.save.SaveData;
import dev.ember2d.save.SaveStore;
import dev.ember2d.save.SlotInfo;
import dev.ember2d.save.SlotState;

/** The chapter's story, cast and save slot, and what the reader has seen (endings, CGs, read lines). */
final class Chapter {

    static final int SLOT = 0;
    static final String[] ENDINGS = {"together", "alone"};
    static final String[] CGS = {"cg.sunset"};
    private static final String ENDING_KEY = "novel.ending.";
    private static final Log LOG = Log.get("novel-demo");

    private Chapter() {
    }

    /** A director for the chapter from the start. */
    static NovelDirector director(EngineContext context, StageListener listener) {
        return new NovelDirector(DialogueRunner.fromAsset(context.assets(), "ink.chapter1"), cast(context), listener,
                memory(context), context.config().devMode);
    }

    static NovelCharacters cast(EngineContext context) {
        return NovelCharacters.parse(context.assets().get("cast", JsonValue.class), context.assets().path("cast"));
    }

    /** Read lines and unlocked CGs, kept in the global save data (shared by all slots). */
    static NovelMemory memory(EngineContext context) {
        return new NovelMemory(context.saves().global());
    }

    static boolean hasSave(SaveStore saves) {
        if (saves.slots().size() <= SLOT) {
            return false;
        }
        SlotInfo info = saves.slots().get(SLOT);
        return info.state() == SlotState.OK || info.backupAvailable();
    }

    /** The saved story, or null when there is none that can be read (logged). */
    static SaveData loadSave(SaveStore saves) {
        LoadResult r = saves.load(SLOT);
        if (!r.ok() && r.info() != null && r.info().backupAvailable()) {
            LOG.warn("Save slot: " + r.info().problem() + "; using the backup");
            r = saves.loadBackup(SLOT);
        }
        return r.ok() ? r.data() : null;
    }

    static void recordEnding(SaveStore saves, String ending) {
        saves.global().set(ENDING_KEY + ending, true);
        saves.flush();
    }

    static int endingsSeen(SaveStore saves) {
        int seen = 0;
        for (String ending : ENDINGS) {
            seen += saves.global().getBool(ENDING_KEY + ending, false) ? 1 : 0;
        }
        return seen;
    }
}
