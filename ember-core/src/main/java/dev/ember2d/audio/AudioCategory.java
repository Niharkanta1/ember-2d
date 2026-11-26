package dev.ember2d.audio;

import dev.ember2d.core.PublicApi;

/** Volume categories (F-011): the effective volume is master × category × the call's own volume. */
@PublicApi
public enum AudioCategory {
    MASTER,
    MUSIC,
    SFX
}
