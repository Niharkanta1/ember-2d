package dev.ember2d.archtest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.badlogic.gdx.Gdx;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.ember2d.core.PublicApi;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

/**
 * Architecture rules from documents/ARCHITECTURE.md ("Dependency rules"), CODING_GUIDELINES.md
 * ("Android safety") and TEST_PLAN.md, written once and shared by every module's {@code ArchitectureTest}.
 *
 * <p>Every rule takes the package it applies to as a parameter, so the same rules can be checked against
 * deliberately broken fixture classes (see {@code EmberArchitectureRulesTest}). Rules allow an empty
 * selection because most engine packages do not exist yet; the self-test proves that each rule does fire.
 */
public final class EmberArchitectureRules {

    /** Base package of Ember2D. */
    public static final String BASE = "dev.ember2d";

    /**
     * Words that name a genre concept; never allowed in engine package or class names (rule 5).
     * "dialogue" and "player" are deliberately absent: ember.ui has a dialogue box widget (F-012), and
     * "player" is a generic engine word (for example an audio player).
     */
    public static final Set<String> GENRE_WORDS = Set.of(
            "jump", "weapon", "quest", "inventory", "bullet",
            "enemy", "platformer", "shooter", "rpg", "novel");

    private static final List<String> TIER_1 = List.of("core");
    private static final List<String> TIER_2 = List.of("event", "ecs", "assets", "input");
    private static final List<String> TIER_3 = List.of(
            "render", "camera", "animation", "collision", "audio", "ui", "particles", "debug", "i18n");
    private static final List<String> TIER_4 = List.of("tilemap");
    private static final List<String> TIER_5 = List.of("scene", "data", "save");
    /** Composition root (ADR-022): may depend on everything; nothing depends on it. */
    private static final List<String> TIER_APP = List.of("app");

    /** Every documented engine package in ember-core, in tier order. */
    public static final List<String> ENGINE_PACKAGES = concat(TIER_1, TIER_2, TIER_3, TIER_4, TIER_5, TIER_APP);

    private static final Pattern WORD_BOUNDARY =
            Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[_$]");

    private EmberArchitectureRules() {
    }

    // ------------------------------------------------------------------------------------------------
    // Rule sets per module kind
    // ------------------------------------------------------------------------------------------------

    /** All rules for {@code ember-core}. */
    public static List<ArchRule> engineRules(String base) {
        List<ArchRule> rules = new ArrayList<>(packageDependencyRules(base));
        rules.add(onlyDocumentedPackagesRule(base));
        rules.add(noGenreWordsRule(base));
        rules.add(noPackageCyclesRule(base));
        rules.addAll(moduleCodeRules(base, List.of(base + ".assets", base + ".save"), List.of(base + ".data")));
        return rules;
    }

    /** Rules for Android-safe module code outside ember-core: kits and demo {@code core} projects. */
    public static List<ArchRule> kitRules(String root) {
        return moduleCodeRules(root, List.of(), List.of());
    }

    private static List<ArchRule> moduleCodeRules(String root, List<String> fileAccessPackages,
            List<String> reflectionPackages) {
        return List.of(
                androidSafeApisRule(root),
                noThreadsRule(root),
                fileAccessRule(root, fileAccessPackages),
                reflectionRule(root, reflectionPackages),
                internalIsNotPublicApiRule(root),
                publicApiHidesTypesRule(root));
    }

    // ------------------------------------------------------------------------------------------------
    // Rules 1-4: package dependencies inside ember-core
    // ------------------------------------------------------------------------------------------------

    /** Which engine packages each engine package may depend on (rules 1-4a; 4a is ADR-022). */
    public static Map<String, List<String>> allowedPackageDependencies() {
        Map<String, List<String>> allowed = new LinkedHashMap<>();
        allowed.put("core", List.of());
        allowed.put("event", TIER_1);
        for (String p : List.of("ecs", "assets", "input")) {
            allowed.put(p, concat(TIER_1, List.of("event")));
        }
        List<String> lower = concat(TIER_1, TIER_2);
        for (String p : TIER_3) {
            allowed.put(p, lower);
        }
        allowed.put("render", concat(lower, List.of("camera")));
        allowed.put("ui", concat(lower, List.of("render")));
        allowed.put("collision", concat(lower, List.of("debug", "render")));   // debug view (I-120)
        allowed.put("animation", concat(lower, List.of("render")));            // writes Sprite frames (I-130)
        allowed.put("particles", concat(lower, List.of("render")));            // draws in a render layer (I-510)
        allowed.put("tilemap", concat(lower, List.of("render", "collision")));
        List<String> belowApp = concat(TIER_1, TIER_2, TIER_3, TIER_4, TIER_5);
        for (String p : TIER_5) {
            allowed.put(p, belowApp);
        }
        allowed.put("app", ENGINE_PACKAGES);
        return allowed;
    }

    /** One rule per engine package that has forbidden dependencies (rules 1-4). */
    public static List<ArchRule> packageDependencyRules(String base) {
        List<ArchRule> rules = new ArrayList<>();
        allowedPackageDependencies().forEach((pkg, allowed) -> {
            List<String> forbidden = ENGINE_PACKAGES.stream()
                    .filter(other -> !other.equals(pkg) && !allowed.contains(other))
                    .map(other -> base + "." + other + "..")
                    .toList();
            if (!forbidden.isEmpty()) {
                rules.add(noClasses().that().resideInAPackage(base + "." + pkg + "..")
                        .should().dependOnClassesThat().resideInAnyPackage(forbidden.toArray(String[]::new))
                        .because("ember." + pkg + (allowed.isEmpty()
                                ? " depends on no other engine package"
                                : " may depend only on " + describe(allowed))
                                + " (ARCHITECTURE.md, dependency rules 1-4)")
                        .allowEmptyShould(true));
            }
        });
        return rules;
    }

    /** Engine classes live only in the documented packages, so new packages are not silently unchecked. */
    public static ArchRule onlyDocumentedPackagesRule(String base) {
        String[] documented = Stream.concat(Stream.of(base),
                ENGINE_PACKAGES.stream().map(p -> base + "." + p + "..")).toArray(String[]::new);
        return classes().that().resideInAPackage(base + "..")
                .should().resideInAnyPackage(documented)
                .because("every ember-core package must be documented in ARCHITECTURE.md and covered by "
                        + "the dependency rules")
                .allowEmptyShould(true);
    }

    // ------------------------------------------------------------------------------------------------
    // Rule 5: no genre words in the engine
    // ------------------------------------------------------------------------------------------------

    public static ArchRule noGenreWordsRule(String base) {
        return classes().that().resideInAPackage(base + "..")
                .should(haveNoGenreWordInName())
                .because("the engine never names a genre concept (ARCHITECTURE.md, rule 5)")
                .allowEmptyShould(true);
    }

    /** Splits a class's package segments and camel-case simple-name parts into lower-case words. */
    public static List<String> nameWords(JavaClass javaClass) {
        List<String> words = new ArrayList<>();
        for (String segment : javaClass.getPackageName().split("\\.")) {
            words.add(segment.toLowerCase(Locale.ROOT));
        }
        for (String part : WORD_BOUNDARY.split(javaClass.getSimpleName())) {
            if (!part.isEmpty()) {
                words.add(part.toLowerCase(Locale.ROOT));
            }
        }
        return words;
    }

    private static ArchCondition<JavaClass> haveNoGenreWordInName() {
        return new ArchCondition<>("have no genre word in package or class name") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (String word : nameWords(javaClass)) {
                    if (GENRE_WORDS.contains(word)) {
                        events.add(SimpleConditionEvent.violated(javaClass,
                                javaClass.getName() + " uses genre word '" + word + "'"));
                    }
                }
            }
        };
    }

    // ------------------------------------------------------------------------------------------------
    // Rule 12: no cycles
    // ------------------------------------------------------------------------------------------------

    public static ArchRule noPackageCyclesRule(String base) {
        return slices().matching(base + ".(*)..")
                .should().beFreeOfCycles()
                .because("there are no circular package dependencies (ARCHITECTURE.md, rule 12)")
                .allowEmptyShould(true);
    }

    // ------------------------------------------------------------------------------------------------
    // Rule 9: Android safety, threads, file access
    // ------------------------------------------------------------------------------------------------

    public static ArchRule androidSafeApisRule(String root) {
        return noClasses().that().resideInAPackage(root + "..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.nio.file..", "java.awt..", "javax..", "java.lang.management..")
                .orShould().dependOnClassesThat().belongToAnyOf(ProcessBuilder.class, Process.class)
                .orShould().callMethodWhere(isCallTo(System.class, Set.of("exit")))
                .orShould().callMethodWhere(isCallTo(Runtime.class, Set.of("exit", "halt", "exec")))
                .because("engine, kit and demo core code must run on Android (ADR-015, CODING_GUIDELINES.md)")
                .allowEmptyShould(true);
    }

    public static ArchRule noThreadsRule(String root) {
        return noClasses().that().resideInAPackage(root + "..")
                .should().dependOnClassesThat().areAssignableTo(Thread.class)
                .orShould().dependOnClassesThat().areAssignableTo(Executor.class)
                .orShould().dependOnClassesThat().belongToAnyOf(java.util.concurrent.Executors.class,
                        java.util.Timer.class)
                .orShould().dependOnClassesThat().resideInAPackage("com.badlogic.gdx.utils.async..")
                .because("no threads except AssetManager's own loading thread (ARCHITECTURE.md, Threading)")
                .allowEmptyShould(true);
    }

    /** {@code Gdx.files} may be used only in the given packages (rule 9, F-008). */
    public static ArchRule fileAccessRule(String root, List<String> allowedPackages) {
        String allowedText = allowedPackages.isEmpty() ? "nowhere in this module" : describe(allowedPackages);
        var selection = noClasses().that().resideInAPackage(root + "..");
        if (!allowedPackages.isEmpty()) {
            selection = selection.and().resideOutsideOfPackages(withSubpackages(allowedPackages));
        }
        return selection.should().accessField(Gdx.class, "files")
                .because("file access goes through Gdx.files only in " + allowedText
                        + "; games use asset IDs (ARCHITECTURE.md, rule 9)")
                .allowEmptyShould(true);
    }

    // ------------------------------------------------------------------------------------------------
    // Rule 10: reflection
    // ------------------------------------------------------------------------------------------------

    public static ArchRule reflectionRule(String root, List<String> allowedPackages) {
        String allowedText = allowedPackages.isEmpty() ? "nowhere in this module" : describe(allowedPackages);
        var selection = noClasses().that().resideInAPackage(root + "..");
        if (!allowedPackages.isEmpty()) {
            selection = selection.and().resideOutsideOfPackages(withSubpackages(allowedPackages));
        }
        return selection.should().dependOnClassesThat()
                .resideInAnyPackage("java.lang.reflect..", "com.badlogic.gdx.utils.reflect..")
                .orShould().callMethodWhere(isCallTo(Class.class, Set.of("forName", "newInstance")))
                .because("reflection is allowed only in " + allowedText
                        + ", so R8 keep rules can be generated (ARCHITECTURE.md, rule 10)")
                .allowEmptyShould(true);
    }

    // ------------------------------------------------------------------------------------------------
    // Rule 11: public API
    // ------------------------------------------------------------------------------------------------

    public static ArchRule internalIsNotPublicApiRule(String root) {
        return noClasses().that().resideInAPackage(root + "..internal..")
                .should().beAnnotatedWith(PublicApi.class)
                .because("packages named internal are not public API (ARCHITECTURE.md, rule 11)")
                .allowEmptyShould(true);
    }

    public static ArchRule publicApiHidesTypesRule(String root) {
        return classes().that().resideInAPackage(root + "..").and().areAnnotatedWith(PublicApi.class)
                .should(notExposeHiddenTypes())
                .because("Box2D, Spine, backend, file and internal types stay behind Ember2D APIs "
                        + "(ARCHITECTURE.md, rule 11; ADR-013)")
                .allowEmptyShould(true);
    }

    /** Types that must never appear in a public API signature. */
    public static boolean isHiddenType(JavaClass type) {
        String name = type.getBaseComponentType().getName();
        String pkg = type.getBaseComponentType().getPackageName();
        return name.startsWith("com.badlogic.gdx.physics.box2d.")
                || name.startsWith("com.esotericsoftware.spine.")
                || name.startsWith("com.badlogic.gdx.backends.")
                || name.startsWith("org.lwjgl.")
                || name.startsWith("android.")
                || name.startsWith("java.nio.file.")
                || name.equals("java.io.File")
                || name.equals("com.badlogic.gdx.files.FileHandle")
                || pkg.endsWith(".internal") || pkg.contains(".internal.");
    }

    private static ArchCondition<JavaClass> notExposeHiddenTypes() {
        return new ArchCondition<>("not expose hidden types in public signatures") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Map<String, Set<JavaClass>> exposed = new LinkedHashMap<>();
                javaClass.getRawSuperclass().ifPresent(s -> exposed.put("supertype", Set.of(s)));
                exposed.put("interfaces", new LinkedHashSet<>(javaClass.getRawInterfaces()));
                javaClass.getFields().stream().filter(EmberArchitectureRules::isVisible)
                        .forEach(f -> exposed.put(f.getFullName(), f.getType().getAllInvolvedRawTypes()));
                javaClass.getCodeUnits().stream().filter(EmberArchitectureRules::isVisible).forEach(unit -> {
                    Set<JavaClass> types = new LinkedHashSet<>(unit.getReturnType().getAllInvolvedRawTypes());
                    for (JavaType parameter : unit.getParameterTypes()) {
                        types.addAll(parameter.getAllInvolvedRawTypes());
                    }
                    exposed.put(unit.getFullName(), types);
                });
                exposed.forEach((where, types) -> types.stream().filter(EmberArchitectureRules::isHiddenType)
                        .forEach(hidden -> events.add(SimpleConditionEvent.violated(javaClass,
                                javaClass.getName() + " exposes hidden type " + hidden.getName()
                                        + " in " + where))));
            }
        };
    }

    private static boolean isVisible(JavaMember member) {
        Set<JavaModifier> modifiers = member.getModifiers();
        return modifiers.contains(JavaModifier.PUBLIC) || modifiers.contains(JavaModifier.PROTECTED);
    }

    // ------------------------------------------------------------------------------------------------
    // Test helpers
    // ------------------------------------------------------------------------------------------------

    /**
     * Imports the production classes of the given packages (no tests, no test fixtures). Jars are included on
     * purpose: Gradle may put a module's own main classes on its test classpath as a jar.
     */
    public static JavaClasses importProductionClasses(String... packages) {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(location -> !location.contains("testFixtures")
                        && !location.contains("test-fixtures"))
                .importPackages(packages);
    }

    /** One JUnit dynamic test per rule, named after the rule. */
    public static Stream<DynamicTest> asTests(JavaClasses classes, Collection<ArchRule> rules) {
        return rules.stream().map(rule -> DynamicTest.dynamicTest(rule.getDescription(), () -> rule.check(classes)));
    }

    private static DescribedPredicate<JavaMethodCall> isCallTo(Class<?> owner, Set<String> methodNames) {
        return DescribedPredicate.describe(owner.getSimpleName() + "." + methodNames,
                call -> call.getTargetOwner().isEquivalentTo(owner) && methodNames.contains(call.getName()));
    }

    private static String[] withSubpackages(List<String> packages) {
        return packages.stream().map(p -> p + "..").toArray(String[]::new);
    }

    private static String describe(List<String> packages) {
        return packages.isEmpty() ? "no other engine package" : String.join(", ", packages);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static List<String> concat(List<String>... lists) {
        return Stream.of(lists).flatMap(List::stream).distinct().toList();
    }
}
