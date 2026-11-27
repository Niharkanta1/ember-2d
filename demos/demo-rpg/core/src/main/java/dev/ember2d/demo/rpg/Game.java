package dev.ember2d.demo.rpg;

import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.app.EngineContext;
import dev.ember2d.core.Log;
import dev.ember2d.kit.common.DialogueRunner;
import dev.ember2d.kit.topdown.rpg.Inventory;
import dev.ember2d.kit.topdown.rpg.ItemCatalog;
import dev.ember2d.save.LoadResult;
import dev.ember2d.save.SaveData;
import dev.ember2d.save.SaveResult;
import dev.ember2d.save.SaveStore;
import dev.ember2d.save.SlotInfo;
import dev.ember2d.save.SlotMeta;
import dev.ember2d.save.SlotState;
import dev.ember2d.save.WorldFlags;

/**
 * One play-through of the RPG demo: where the knight is, what they carry, the world flags (doors, chests, the
 * quest) and the town story's state. It lives across scene changes and is written to save slot 0.
 */
final class Game {

    static final int SLOT = 0;
    private static final Log LOG = Log.get("rpg");

    final ItemCatalog items;
    final Inventory inventory = new Inventory();
    final WorldFlags flags;
    final DialogueRunner story;
    /** The area the knight is in ({@code town} or {@code cellar}) and the marker to start at. */
    String area = "town";
    String spawn = "start";
    /** A saved position, used instead of the marker after loading. */
    boolean hasPosition;
    float x;
    float y;
    float playSeconds;
    /** Bumped when the inventory changes, so the HUD can redraw. */
    int itemsVersion;

    Game(ItemCatalog items, WorldFlags flags, DialogueRunner story) {
        this.items = items;
        this.flags = flags;
        this.story = story;
        bind();
    }

    /** A new play-through: the flags are cleared. */
    static Game start(EngineContext context) {
        context.flags().clear();
        return new Game(ItemCatalog.parse(context.assets().get("items", JsonValue.class), context.assets().path("items")),
                context.flags(), DialogueRunner.fromAsset(context.assets(), "ink.town"));
    }

    /** Whether slot 0 holds a save (or a damaged one with a backup). */
    static boolean hasSave(SaveStore saves) {
        if (saves.slots().size() <= SLOT) {
            return false;
        }
        SlotInfo info = saves.slots().get(SLOT);
        return info.state() == SlotState.OK || info.backupAvailable();
    }

    /** The play-through in slot 0 (or its backup); null if there is none or it cannot be read. */
    static Game continueSaved(EngineContext context) {
        LoadResult r = context.saves().load(SLOT);
        if (!r.ok() && r.info() != null && r.info().backupAvailable()) {
            LOG.warn("Save slot: " + r.info().problem() + "; using the backup");
            r = context.saves().loadBackup(SLOT);
        }
        if (!r.ok()) {
            LOG.warn("No readable save" + (r.info() != null && r.info().problem() != null ? ": " + r.info().problem()
                    : ""));
            return null;
        }
        Game game = start(context);
        game.read(r.data());
        return game;
    }

    /** The Ink functions the stories call. */
    private void bind() {
        story.bind("has", args -> inventory.has((String) args[0], 1));
        story.bind("give", args -> {
            int added = inventory.add(items, (String) args[0], ((Number) args[1]).intValue());
            itemsVersion++;
            return added > 0;
        });
        story.bind("take", args -> {
            boolean taken = inventory.remove((String) args[0], ((Number) args[1]).intValue());
            itemsVersion++;
            return taken;
        });
        story.bind("flag", args -> flags.getBool((String) args[0], false));
        story.bind("set_flag", args -> {
            flags.set((String) args[0], true);
            return true;
        });
    }

    /** Writes the play-through: area, position, inventory, flags and the story. */
    SaveData write() {
        SaveData data = new SaveData().set("area", area).set("x", x).set("y", y).set("playSeconds", playSeconds);
        SaveData inv = new SaveData();
        inventory.save(inv);
        data.set("inventory", inv);
        SaveData f = new SaveData();
        flags.save(f);
        data.set("flags", f);
        SaveData s = new SaveData();
        story.save(s);
        data.set("story", s);
        return data;
    }

    void read(SaveData data) {
        area = data.getString("area", "town");
        spawn = "start";
        x = data.getFloat("x", 0f);
        y = data.getFloat("y", 0f);
        hasPosition = data.has("x");
        playSeconds = data.getFloat("playSeconds", 0f);
        inventory.load(data.has("inventory") ? data.child("inventory") : new SaveData(), items);
        flags.load(data.has("flags") ? data.child("flags") : new SaveData());
        if (data.has("story")) {
            story.load(data.child("story"));
        }
        itemsVersion++;
    }

    SaveResult save(SaveStore saves) {
        String place = "cellar".equals(area) ? "The cellar" : "Millbrook";
        SaveResult result = saves.save(SLOT, new SlotMeta(area, playSeconds, place), write());
        if (!result.ok()) {
            LOG.error("Saving failed: " + result.error());
        }
        return result;
    }
}
