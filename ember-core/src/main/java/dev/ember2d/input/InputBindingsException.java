package dev.ember2d.input;

import dev.ember2d.core.PublicApi;
import java.util.List;

/** An invalid input bindings file; lists every problem at once, each with the file and the path inside it (F-005). */
@PublicApi
public final class InputBindingsException extends RuntimeException {

    private final List<String> problems;

    public InputBindingsException(String summary, List<String> problems) {
        super(summary + ":\n  " + String.join("\n  ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
