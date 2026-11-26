package dev.ember2d.ui;

import dev.ember2d.core.PublicApi;

/** How the UI is laid out (F-012, {@code EngineConfig.uiMode}). */
@PublicApi
public enum UiMode {
    /** In virtual pixels, in the same screen rectangle and at the same scale as the world: crisp pixel fonts. */
    VIRTUAL,
    /** In screen pixels divided by {@code EngineConfig.uiScale}: smooth text at native resolution. */
    SCREEN
}
