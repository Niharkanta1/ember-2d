package dev.ember2d.animation;

import dev.ember2d.core.PublicApi;
import java.util.List;

/** An invalid clip set; lists every problem with its file, clip and field (F-007). */
@PublicApi
public final class AnimationException extends RuntimeException {

    private final List<String> problems;

    public AnimationException(String summary, List<String> problems) {
        super(summary + ":\n  " + String.join("\n  ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
