package archfixture.core;

import java.util.List;
import java.util.function.Supplier;

/** Correct code using records, enums, switch expressions and lambdas: must not trip any rule. */
public record CleanRecord(String name, int value) {

    public enum Kind { A, B }

    public static Kind parse(String text) {
        return Kind.valueOf(text);
    }

    public int score(Kind kind) {
        Supplier<Integer> base = () -> value;
        return switch (kind) {
            case A -> base.get();
            case B -> List.of(1, 2).size() + base.get();
        };
    }
}
