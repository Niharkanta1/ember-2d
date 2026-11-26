package dev.ember2d.save.internal;

import java.io.IOException;

/** Where saves live (F-013): a folder on disk or memory. Names are plain file names. */
public interface SaveFiles {

    /** The file's text, or null if it does not exist. */
    String read(String name) throws IOException;

    boolean exists(String name);

    /** Writes atomically: {@code name.tmp}, then the old file becomes {@code name.bak}, then the rename. */
    void write(String name, String text) throws IOException;

    /** Deletes a file; returns whether it existed. */
    boolean delete(String name);

    /** Where the files are, for log lines. */
    String describe();
}
