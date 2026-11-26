package dev.ember2d.data;

import dev.ember2d.core.PublicApi;
import java.util.List;

/** Invalid prefab data or placement; lists every problem, each naming the file or map, object and field (F-010). */
@PublicApi
public final class PrefabException extends RuntimeException {

    private final List<String> problems;

    public PrefabException(String summary, List<String> problems) {
        super(summary + ":\n  " + String.join("\n  ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
