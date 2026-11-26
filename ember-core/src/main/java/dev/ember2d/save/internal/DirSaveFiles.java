package dev.ember2d.save.internal;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Saves in a folder (F-013) with plain {@code java.io} (Android-safe; {@code java.nio.file} is not). A write syncs
 * {@code name.tmp} to disk, moves the old file to {@code name.bak} and renames the tmp file into place, so a crash
 * at any point leaves the previous save as the file or as its backup.
 */
public final class DirSaveFiles implements SaveFiles {

    private final File dir;
    private Consumer<String> afterStep = step -> { };

    public DirSaveFiles(File dir) {
        this.dir = dir;
    }

    /** Tests: called after "tmp" (written) and "backup" (old file moved); throwing there simulates a crash. */
    void setAfterStep(Consumer<String> afterStep) {
        this.afterStep = afterStep;
    }

    @Override
    public String read(String name) throws IOException {
        File file = new File(dir, name);
        if (!file.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(64, file.length()));
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    @Override
    public boolean exists(String name) {
        return new File(dir, name).isFile();
    }

    @Override
    public void write(String name, String text) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create the folder " + dir);
        }
        File tmp = new File(dir, name + ".tmp");
        File file = new File(dir, name);
        File backup = new File(dir, name + ".bak");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
        }
        afterStep.accept("tmp");
        if (file.exists()) {
            if (backup.exists() && !backup.delete()) {
                throw new IOException("cannot replace the backup " + backup);
            }
            if (!file.renameTo(backup)) {
                throw new IOException("cannot move " + file + " to its backup");
            }
        }
        afterStep.accept("backup");
        if (!tmp.renameTo(file)) {
            throw new IOException("cannot rename " + tmp + " to " + file.getName());
        }
    }

    @Override
    public boolean delete(String name) {
        File file = new File(dir, name);
        return file.isFile() && file.delete();
    }

    @Override
    public String describe() {
        return dir.getAbsolutePath();
    }
}
