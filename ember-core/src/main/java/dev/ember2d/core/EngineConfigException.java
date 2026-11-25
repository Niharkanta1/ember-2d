package dev.ember2d.core;

import java.util.List;

/** Thrown at startup when the {@link EngineConfig} is invalid; lists every invalid field at once. */
@PublicApi
public final class EngineConfigException extends RuntimeException {

    private final List<String> problems;

    public EngineConfigException(List<String> problems) {
        super("Invalid EngineConfig:\n  " + String.join("\n  ", problems));
        this.problems = List.copyOf(problems);
    }

    /** One message per invalid field, naming the field, the expected value and the actual value. */
    public List<String> problems() {
        return problems;
    }
}
