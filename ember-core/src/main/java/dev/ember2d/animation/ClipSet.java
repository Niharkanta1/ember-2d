package dev.ember2d.animation;

import dev.ember2d.core.PublicApi;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** The clips of one clip set file (F-007), by name. */
@PublicApi
public final class ClipSet {

    private final String id;
    private final Map<String, Clip> clips;

    ClipSet(String id, Map<String, Clip> clips) {
        this.id = id;
        this.clips = Collections.unmodifiableMap(clips);
    }

    public String id() {
        return id;
    }

    /** The clip, or null if the set has none with that name. */
    public Clip clip(String name) {
        return clips.get(name);
    }

    public Set<String> names() {
        return clips.keySet();
    }
}
