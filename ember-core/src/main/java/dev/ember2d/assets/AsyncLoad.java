package dev.ember2d.assets;

import dev.ember2d.core.PublicApi;

/** Progress of a group load started with {@link Assets#loadGroup(String)}. */
@PublicApi
public interface AsyncLoad {

    String group();

    /** Fraction of the group's assets loaded, 0 to 1. */
    float progress();

    boolean isDone();

    /** Blocks until the group is loaded (loading screens should poll {@link #isDone()} instead). */
    void finish();
}
