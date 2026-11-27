package dev.ember2d.kit.bullets;

import dev.ember2d.core.PublicApi;
import java.util.List;

/** A bullet pattern (F-024): emitters that run together; loaded by {@link BulletPatterns}. */
@PublicApi
public final class Pattern {

    private final String id;
    private final List<Emitter> emitters;

    Pattern(String id, List<Emitter> emitters) {
        this.id = id;
        this.emitters = List.copyOf(emitters);
    }

    public String id() {
        return id;
    }

    public List<Emitter> emitters() {
        return emitters;
    }
}
