package dev.ember2d.box2d;

import dev.ember2d.archtest.EmberArchitectureRules;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Checks ember-box2d against the Android-safety, reflection, file-access and public API rules; the public API rule
 * also keeps Box2D types out of public signatures (ADR-013).
 */
class ArchitectureTest {

    @TestFactory
    Stream<DynamicTest> moduleRules() {
        return EmberArchitectureRules.asTests(
                EmberArchitectureRules.importProductionClasses("dev.ember2d.box2d"),
                EmberArchitectureRules.kitRules("dev.ember2d.box2d"));
    }
}
