package dev.ember2d.tilemap;

import dev.ember2d.core.PublicApi;

/** A map Ember2D cannot use (F-009); the message names the map. */
@PublicApi
public final class TileMapException extends RuntimeException {

    public TileMapException(String source, String reason) {
        super("Tiled map " + source + ": " + reason);
    }
}
