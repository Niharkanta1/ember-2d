package dev.ember2d;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Proves that the JUnit 5 platform, AssertJ and the Java 17 language level are wired into the build.
 * Replace or remove once real engine tests exist.
 */
class BuildSmokeTest {

    private record Pair(String name, int value) {
    }

    @Test
    void java17LanguageFeaturesCompileAndRun() {
        Object pair = new Pair("ember", 2);
        String described = pair instanceof Pair p ? p.name() + p.value() : "unknown";
        String size = switch (described.length()) {
            case 0 -> "empty";
            case 6 -> "six";
            default -> "other";
        };
        assertThat(described).isEqualTo("ember2");
        assertThat(size).isEqualTo("six");
    }
}
