package dev.ember2d.demo.novel;

import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.ComponentSetup;

/** The novel demo has no entities of its own: only the engine's components (this also drives the R8 keep rules). */
public final class NovelComponents implements ComponentSetup {

    @Override
    public void register(ComponentRegistry registry) {
        // nothing beyond the engine's own components
    }
}
