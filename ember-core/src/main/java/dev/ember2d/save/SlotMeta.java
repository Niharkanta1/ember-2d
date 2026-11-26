package dev.ember2d.save;

import dev.ember2d.core.PublicApi;

/** What a game shows about a slot before loading it (F-013). Null strings are stored as empty. */
@PublicApi
public record SlotMeta(String scene, float playTimeSeconds, String summary) {

    public SlotMeta {
        scene = scene == null ? "" : scene;
        summary = summary == null ? "" : summary;
        if (!(playTimeSeconds >= 0f) || Float.isInfinite(playTimeSeconds)) {
            throw new IllegalArgumentException("playTimeSeconds must be a finite number >= 0 (was "
                    + playTimeSeconds + ")");
        }
    }
}
