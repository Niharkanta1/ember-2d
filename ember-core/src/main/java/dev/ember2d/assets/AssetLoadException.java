package dev.ember2d.assets;

import dev.ember2d.core.PublicApi;
import java.util.List;

/** An invalid manifest or missing required asset files; lists every problem at once (F-008). */
@PublicApi
public final class AssetLoadException extends RuntimeException {

    private final List<String> problems;

    public AssetLoadException(String summary, List<String> problems) {
        super(summary + ":\n  " + String.join("\n  ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
