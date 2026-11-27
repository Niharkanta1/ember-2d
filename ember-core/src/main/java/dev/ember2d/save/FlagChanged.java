package dev.ember2d.save;

import dev.ember2d.core.PublicApi;

/** A world flag was set to a different value or removed (F-013, I-430). */
@PublicApi
public record FlagChanged(String key) {
}
