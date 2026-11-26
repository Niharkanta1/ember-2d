package dev.ember2d.save.internal;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/** Where a game's saves go (F-013): Android's private {@code saves} folder, or {@code ~/.ember2d/<name>} on desktop. */
public final class SaveFolders {

    private SaveFolders() {
    }

    /** Files in the game's save folder, or in memory when {@code folder} is null or no libGDX backend runs. */
    public static SaveFiles forGame(String folder) {
        if (folder == null || Gdx.files == null || Gdx.app == null) {
            return new MemorySaveFiles();
        }
        boolean android = Gdx.app.getType() == Application.ApplicationType.Android;
        FileHandle dir = android ? Gdx.files.local("saves") : Gdx.files.external(".ember2d/" + folder);
        return new DirSaveFiles(dir.file());
    }
}
