package dev.ember2d;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import dev.ember2d.archtest.EmberArchitectureRules;
import dev.ember2d.core.PublicApi;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Checks ember-core against the architecture rules in documents/ARCHITECTURE.md. */
class ArchitectureTest {

    private static JavaClasses engine;

    @BeforeAll
    static void importEngine() {
        engine = EmberArchitectureRules.importProductionClasses(EmberArchitectureRules.BASE);
    }

    /** Guards against the rules passing vacuously because no production class was imported. */
    @Test
    void importsEngineProductionClassesOnly() {
        assertThat(engine.contain(PublicApi.class)).isTrue();
        assertThat(engine.stream().map(c -> c.getPackageName()))
                .noneMatch(pkg -> pkg.startsWith("dev.ember2d.archtest"));
    }

    @TestFactory
    Stream<DynamicTest> engineRules() {
        return EmberArchitectureRules.asTests(engine, EmberArchitectureRules.engineRules(EmberArchitectureRules.BASE));
    }
}
