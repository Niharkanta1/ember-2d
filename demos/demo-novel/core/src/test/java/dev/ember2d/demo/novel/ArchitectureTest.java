package dev.ember2d.demo.novel;

import dev.ember2d.archtest.EmberArchitectureRules;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Checks the novel demo's shared game code against the Android-safety, reflection, file-access and public API rules. */
class ArchitectureTest {

    @TestFactory
    Stream<DynamicTest> moduleRules() {
        return EmberArchitectureRules.asTests(
                EmberArchitectureRules.importProductionClasses("dev.ember2d.demo.novel"),
                EmberArchitectureRules.kitRules("dev.ember2d.demo.novel"));
    }
}
