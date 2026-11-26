package dev.ember2d.save;

import dev.ember2d.core.PublicApi;

/** The outcome of a write (F-013): saving never throws for file problems, it reports them here. */
@PublicApi
public record SaveResult(boolean ok, String error) {

    private static final SaveResult OK = new SaveResult(true, "");

    public static SaveResult success() {
        return OK;
    }

    public static SaveResult failed(String error) {
        return new SaveResult(false, error);
    }
}
