package dev.ember2d.archtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves that every architecture rule fires on a known violation and accepts known-good code, using the
 * fixture classes in the {@code archfixture} test package as a stand-in engine.
 */
class EmberArchitectureRulesTest {

    private static final String BASE = "archfixture";
    private static JavaClasses fixtures;

    @BeforeAll
    static void importFixtures() {
        fixtures = new ClassFileImporter().importPackages(BASE);
    }

    private static String failures(ArchRule rule) {
        EvaluationResult result = rule.evaluate(fixtures);
        return String.join("\n", result.getFailureReport().getDetails());
    }

    @Test
    void packageDependencyRulesRejectCoreDependingOnRender() {
        String report = failuresOf(EmberArchitectureRules.packageDependencyRules(BASE));
        assertThat(report).contains("archfixture.core.CoreUsesRender").contains("archfixture.input.InputThing");
    }

    @Test
    void undocumentedPackagesAreRejected() {
        assertThat(failures(EmberArchitectureRules.onlyDocumentedPackagesRule(BASE)))
                .contains("Class <archfixture.misc.Undocumented>")
                .doesNotContain("Class <archfixture.core.");
    }

    @Test
    void genreWordsAreRejectedAsWholeWordsOnly() {
        assertThat(failures(EmberArchitectureRules.noGenreWordsRule(BASE)))
                .contains("archfixture.collision.JumpSolver")
                .doesNotContain("BulletinBoard");
    }

    @Test
    void packageCyclesAreRejected() {
        assertThat(failures(EmberArchitectureRules.noPackageCyclesRule(BASE)))
                .contains("archfixture.ecs.EcsThing")
                .contains("archfixture.input.InputThing");
    }

    @Test
    void androidUnsafeApisAreRejected() {
        assertThat(failures(EmberArchitectureRules.androidSafeApisRule(BASE)))
                .contains("archfixture.audio.UsesNioFile")
                .contains("archfixture.audio.CallsSystemExit");
    }

    @Test
    void threadsAreRejected() {
        assertThat(failures(EmberArchitectureRules.noThreadsRule(BASE)))
                .contains("archfixture.audio.StartsThread");
    }

    @Test
    void gdxFilesIsAllowedOnlyInAllowedPackages() {
        assertThat(failures(EmberArchitectureRules.fileAccessRule(BASE, List.of(BASE + ".assets", BASE + ".save"))))
                .contains("archfixture.audio.ReadsGdxFiles")
                .doesNotContain("AllowedFileAccess");
        assertThat(failures(EmberArchitectureRules.fileAccessRule(BASE, List.of())))
                .contains("AllowedFileAccess");
    }

    @Test
    void reflectionIsAllowedOnlyInAllowedPackages() {
        assertThat(failures(EmberArchitectureRules.reflectionRule(BASE, List.of(BASE + ".data"))))
                .contains("archfixture.audio.UsesReflection")
                .doesNotContain("AllowedReflection");
        assertThat(failures(EmberArchitectureRules.reflectionRule(BASE, List.of())))
                .contains("AllowedReflection");
    }

    @Test
    void internalTypesAreNotPublicApi() {
        assertThat(failures(EmberArchitectureRules.internalIsNotPublicApiRule(BASE)))
                .contains("archfixture.render.internal.InternalPublic");
    }

    @Test
    void publicApiDoesNotExposeHiddenTypes() {
        String report = failures(EmberArchitectureRules.publicApiHidesTypesRule(BASE));
        assertThat(report)
                .contains("ExposesFileHandle exposes hidden type com.badlogic.gdx.files.FileHandle in "
                        + "archfixture.render.ExposesFileHandle.file()")
                .contains("archfixture.render.ExposesFileHandle.load(java.util.List)")
                .contains("ExposesInternal exposes hidden type archfixture.render.internal.InternalPublic")
                .doesNotContain("hiddenIsFine");
    }

    @Test
    void correctModernJavaTripsNoRule() {
        String report = failuresOf(EmberArchitectureRules.engineRules(BASE));
        assertThat(report).doesNotContain("CleanRecord");
    }

    private static String failuresOf(List<ArchRule> rules) {
        StringBuilder report = new StringBuilder();
        for (ArchRule rule : rules) {
            report.append(failures(rule)).append('\n');
        }
        return report.toString();
    }
}
