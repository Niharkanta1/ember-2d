package dev.ember2d.tools.newgame;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The project generator (F-027): copies a template into {@code demos/<name>}, renames its package, classes, titles
 * and module names, and adds the game to {@code settings.gradle}. Run through {@code ./gradlew newGame}.
 */
public final class NewGame {

    /** Templates: {@code blank} plus the four demos. */
    public static final List<String> TEMPLATES = List.of("blank", "platformer", "shooter", "rpg", "novel");

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    private static final Pattern SEGMENT = Pattern.compile("[a-z][a-z0-9_]*");
    private static final int MAX_NAME = 40;
    private static final int MAX_TITLE = 60;
    private static final String TITLE_FORBIDDEN = "\"'\\<>&";

    private static final Set<String> KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
            "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while", "true", "false", "null", "var", "record", "yield", "sealed", "permits",
            "_");

    /** Text files get the renames; everything else (images, sounds, fonts, compiled stories) is copied as is. */
    private static final Set<String> TEXT_EXTENSIONS = Set.of("java", "gradle", "xml", "pro", "txt", "md",
            "properties", "ink");

    /** The demo each template's config and component classes come from. */
    private static final Map<String, String[]> DEMO_CLASSES = Map.of(
            "platformer", new String[] {"PlatformerDemo", "DemoComponents"},
            "shooter", new String[] {"ShooterDemo", "ShooterComponents"},
            "rpg", new String[] {"RpgDemo", "RpgComponents"},
            "novel", new String[] {"NovelDemo", "NovelComponents"});

    /** The demo whose desktop and Android launchers the blank template uses (no Ink step). */
    private static final String BLANK_LAUNCHERS = "shooter";

    private static final String PROTECTED = "\u0000ASSETS_SRC\u0000";

    private NewGame() {
    }

    /** The generator's options; build them with {@link #of}. */
    public record Options(String name, String pkg, String title, String template) {

        /** Fills the defaults: the title from the name, the {@code blank} template. */
        public static Options of(String name, String pkg, String title, String template) {
            String n = name == null ? "" : name.trim();
            String p = pkg == null ? "" : pkg.trim();
            String t = title == null || title.isBlank() ? defaultTitle(n) : title.trim();
            String tm = template == null || template.isBlank() ? "blank" : template.trim().toLowerCase(Locale.ROOT);
            return new Options(n, p, t, tm);
        }

        /** Every rule the options break; empty when they are valid. */
        public List<String> problems() {
            List<String> problems = new ArrayList<>();
            if (name.isEmpty()) {
                problems.add("gameName is required (for example -PgameName=my-game)");
            } else if (!NAME.matcher(name).matches()) {
                problems.add("gameName '" + name + "' must be lower-case words joined by '-' (letters and digits, "
                        + "starting with a letter), for example my-game");
            } else if (name.length() > MAX_NAME) {
                problems.add("gameName '" + name + "' is longer than " + MAX_NAME + " characters");
            }
            if (pkg.isEmpty()) {
                problems.add("gamePackage is required (for example -PgamePackage=com.example.mygame)");
            } else {
                String[] segments = pkg.split("\\.", -1);
                if (segments.length < 2) {
                    problems.add("gamePackage '" + pkg + "' needs at least two parts, for example com.example.mygame");
                }
                for (String segment : segments) {
                    if (!SEGMENT.matcher(segment).matches()) {
                        problems.add("gamePackage '" + pkg + "': part '" + segment + "' must be lower-case letters, "
                                + "digits and '_', starting with a letter");
                    } else if (KEYWORDS.contains(segment)) {
                        problems.add("gamePackage '" + pkg + "': part '" + segment + "' is a Java keyword");
                    }
                }
                if ((pkg.equals("dev.ember2d") || pkg.startsWith("dev.ember2d."))
                        && !pkg.startsWith("dev.ember2d.demo.")) {
                    problems.add("gamePackage '" + pkg + "' is inside the engine's package dev.ember2d; use your own");
                }
                if (TEMPLATES.contains(template) && pkg.equals("dev.ember2d.demo." + template)) {
                    problems.add("gamePackage '" + pkg + "' is the template's own package");
                }
            }
            if (title.isEmpty()) {
                problems.add("gameTitle must not be empty");
            } else {
                if (title.length() > MAX_TITLE) {
                    problems.add("gameTitle is longer than " + MAX_TITLE + " characters");
                }
                for (int i = 0; i < title.length(); i++) {
                    char c = title.charAt(i);
                    if (Character.isISOControl(c) || TITLE_FORBIDDEN.indexOf(c) >= 0) {
                        problems.add("gameTitle must not contain line breaks or any of " + TITLE_FORBIDDEN);
                        break;
                    }
                }
            }
            if (!TEMPLATES.contains(template)) {
                problems.add("template '" + template + "' is unknown; use one of " + String.join(", ", TEMPLATES));
            }
            return problems;
        }

        /** The words of the name, capitalised and joined: {@code my-game} gives {@code MyGame}. */
        public String pascalName() {
            StringBuilder out = new StringBuilder();
            for (String word : name.split("-")) {
                if (!word.isEmpty()) {
                    out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
                }
            }
            return out.toString();
        }

        /** The config class: {@code forest-run} gives {@code ForestRunGame}, {@code space-game} gives {@code SpaceGame}. */
        public String className() {
            String pascal = pascalName();
            return pascal.endsWith("Game") ? pascal : pascal + "Game";
        }

        /** The component setup class: {@code forest-run} gives {@code ForestRunComponents}. */
        public String componentsClassName() {
            return pascalName() + "Components";
        }
    }

    static String defaultTitle(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.split("-")) {
            if (!word.isEmpty()) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    /** Thrown when the options or the target folder are refused; nothing was written. */
    public static final class GeneratorException extends Exception {
        private static final long serialVersionUID = 1L;
        private final List<String> problems;

        GeneratorException(List<String> problems) {
            super("Cannot create the game:\n  " + String.join("\n  ", problems));
            this.problems = List.copyOf(problems);
        }

        public List<String> problems() {
            return problems;
        }
    }

    /**
     * Copies a template from {@code repo} into {@code target}/demos/&lt;name&gt; and adds the game to
     * {@code target}/settings.gradle. {@code target} is usually {@code repo} itself.
     *
     * @return the files written, relative to {@code target}
     * @throws GeneratorException listing every problem, before anything is written
     */
    public static List<Path> generate(Path repo, Path target, Options options) throws IOException, GeneratorException {
        List<String> problems = new ArrayList<>(options.problems());
        Path game = target.resolve("demos").resolve(options.name());
        Path settings = target.resolve("settings.gradle");
        if (problems.isEmpty()) {
            if (Files.exists(game)) {
                problems.add("demos/" + options.name() + " already exists");
            }
            if (!Files.isRegularFile(settings)) {
                problems.add("settings.gradle not found in " + target);
            } else if (Files.readString(settings, StandardCharsets.UTF_8)
                    .contains("'demos:" + options.name() + ":core'")) {
                problems.add("settings.gradle already includes demos:" + options.name());
            }
            String source = options.template().equals("blank") ? BLANK_LAUNCHERS : options.template();
            Path demo = repo.resolve("demos").resolve("demo-" + source);
            if (!Files.isDirectory(demo)) {
                problems.add("template folder " + demo + " not found");
            } else if (!options.template().equals("blank")) {
                Path pkgDir = demo.resolve("core/src/main/java/dev/ember2d/demo/" + source);
                String[] classes = DEMO_CLASSES.get(source);
                for (String cls : new String[] {options.className(), options.componentsClassName()}) {
                    if (!cls.equals(classes[0]) && !cls.equals(classes[1])
                            && Files.exists(pkgDir.resolve(cls + ".java"))) {
                        problems.add("the " + source + " template already has a class " + cls
                                + "; choose another gameName");
                    }
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new GeneratorException(problems);
        }

        List<Path> written = new ArrayList<>();
        String t = options.template();
        if (t.equals("blank")) {
            Renamer renamer = new Renamer(BLANK_LAUNCHERS, options);
            Path demo = repo.resolve("demos").resolve("demo-" + BLANK_LAUNCHERS);
            copyTree(demo.resolve("desktop"), game.resolve("desktop"), renamer, target, written);
            copyTree(demo.resolve("android"), game.resolve("android"), renamer, target, written);
            writeBlankCore(repo, demo, game.resolve("core"), options, target, written);
        } else {
            Renamer renamer = new Renamer(t, options);
            copyTree(repo.resolve("demos").resolve("demo-" + t), game, renamer, target, written);
            Path ink = repo.resolve("assets-src").resolve("demo-" + t).resolve("ink");
            if (Files.isDirectory(ink)) {
                try (Stream<Path> files = Files.list(ink)) {
                    for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".ink")).sorted().toList()) {
                        Path out = game.resolve("core/ink").resolve(file.getFileName().toString());
                        writeText(out, renamer.apply(Files.readString(file, StandardCharsets.UTF_8)));
                        written.add(target.relativize(out));
                    }
                }
            }
        }
        register(settings, options.name());
        written.add(target.relativize(settings));
        return written;
    }

    /** The renaming rules for one demo template (F-027, "Renaming rules"). */
    static final class Renamer {
        private final Map<String, String> text = new LinkedHashMap<>();
        private final Map<String, String> paths = new LinkedHashMap<>();

        Renamer(String template, Options options) {
            String lower = template;
            String upper = lower.equals("rpg") ? "RPG" : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
            String demo = "demo-" + lower;
            String[] classes = DEMO_CLASSES.get(lower);
            String gameDir = "demos/" + options.name();
            // 1. Ink sources move into the game; other assets-src references keep pointing at the demo's generators.
            text.put("\"..\", \"..\", \"..\", \"assets-src\", \"" + demo + "\", \"ink\"", "\"ink\"");
            text.put("assets-src/" + demo + "/ink", gameDir + "/core/ink");
            text.put("assets-src/" + demo, PROTECTED);
            // 2. The package.
            text.put("dev.ember2d.demo." + lower, options.pkg());
            // 3. The config and component setup classes.
            text.put(classes[0], options.className());
            text.put(classes[1], options.componentsClassName());
            // 4. Titles.
            text.put("Ember2D - " + upper + " Demo", options.title());
            text.put("Ember2D " + upper + " Demo", options.title());
            text.put("Ember2D " + upper, options.title());
            text.put("the " + lower + " demo", "the game");
            text.put(upper + " demo", options.title());
            // 5. and 6. Module, folder, save, log and package names.
            text.put("demos/" + demo, gameDir);
            text.put(demo, options.name());
            text.put(lower + "-demo", options.name());
            text.put(PROTECTED, "assets-src/" + demo);

            paths.put("dev/ember2d/demo/" + lower, options.pkg().replace('.', '/'));
            paths.put(classes[0] + ".java", options.className() + ".java");
            paths.put(classes[1] + ".java", options.componentsClassName() + ".java");
        }

        String apply(String content) {
            String out = content;
            for (Map.Entry<String, String> rule : text.entrySet()) {
                out = out.replace(rule.getKey(), rule.getValue());
            }
            return out;
        }

        String applyToPath(String relative) {
            String out = relative;
            for (Map.Entry<String, String> rule : paths.entrySet()) {
                out = out.replace(rule.getKey(), rule.getValue());
            }
            return out;
        }
    }

    static boolean skipped(Path relative) {
        for (Path part : relative) {
            String name = part.toString();
            if (name.startsWith(".") || name.equals("build") || name.equals("bin") || name.equals("logs")
                    || name.endsWith(".iml")) {
                return true;
            }
        }
        return false;
    }

    static boolean isText(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && TEXT_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static void copyTree(Path from, Path to, Renamer renamer, Path target, List<Path> written)
            throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(from)) {
            files = walk.filter(Files::isRegularFile).filter(f -> !skipped(from.relativize(f))).sorted().toList();
        }
        for (Path file : files) {
            String relative = from.relativize(file).toString().replace('\\', '/');
            Path out = to.resolve(renamer.applyToPath(relative));
            Files.createDirectories(out.getParent());
            if (isText(file)) {
                writeText(out, renamer.apply(Files.readString(file, StandardCharsets.UTF_8)));
            } else {
                Files.copy(file, out);
            }
            written.add(target.relativize(out));
        }
    }

    private static void writeBlankCore(Path repo, Path launcherDemo, Path core, Options options, Path target,
            List<Path> written) throws IOException {
        Path templates = repo.resolve("tools/new-game/templates/blank/core");
        String pkgPath = options.pkg().replace('.', '/');
        List<Path> files;
        try (Stream<Path> walk = Files.walk(templates)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path file : files) {
            String relative = templates.relativize(file).toString().replace('\\', '/');
            relative = relative.replace("_package_", pkgPath).replace("_Game_", options.className())
                    .replace("_Components_", options.componentsClassName());
            if (relative.endsWith(".tmpl")) {
                relative = relative.substring(0, relative.length() - ".tmpl".length());
            }
            Path out = core.resolve(relative);
            String content = Files.readString(file, StandardCharsets.UTF_8)
                    .replace("@PACKAGE@", options.pkg())
                    .replace("@GAME@", options.className())
                    .replace("@COMPONENTS@", options.componentsClassName())
                    .replace("@TITLE@", options.title())
                    .replace("@NAME@", options.name());
            writeText(out, content);
            written.add(target.relativize(out));
        }
        Path fonts = launcherDemo.resolve("core/assets/fonts");
        for (String font : List.of("PressStart2P-Regular.ttf", "OFL.txt")) {
            Path out = core.resolve("assets/fonts").resolve(font);
            Files.createDirectories(out.getParent());
            Files.copy(fonts.resolve(font), out);
            written.add(target.relativize(out));
        }
    }

    private static void writeText(Path out, String content) {
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void register(Path settings, String name) throws IOException {
        String content = Files.readString(settings, StandardCharsets.UTF_8);
        String nl = content.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder out = new StringBuilder(content);
        if (!content.endsWith("\n")) {
            out.append(nl);
        }
        out.append(nl).append("// ").append(name).append(" (created by newGame, F-027)").append(nl);
        for (String module : List.of("core", "desktop", "android")) {
            out.append("include 'demos:").append(name).append(':').append(module).append('\'').append(nl);
        }
        Files.writeString(settings, out.toString(), StandardCharsets.UTF_8);
    }

    /** {@code --name= --package= --title= --template=}; run from the repository root. */
    public static void main(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (arg.startsWith("--") && eq > 2) {
                values.put(arg.substring(2, eq), arg.substring(eq + 1));
            }
        }
        Options options = Options.of(values.get("name"), values.get("package"), values.get("title"),
                values.get("template"));
        Path repo = Path.of("").toAbsolutePath();
        try {
            List<Path> written = generate(repo, repo, options);
            System.out.println("Created demos/" + options.name() + " from the '" + options.template() + "' template ("
                    + written.size() + " files):");
            System.out.println("  package " + options.pkg() + ", classes " + options.className() + " and "
                    + options.componentsClassName() + ", title \"" + options.title() + "\"");
            System.out.println("  added to settings.gradle");
            System.out.println("Next:");
            System.out.println("  ./gradlew :demos:" + options.name() + ":desktop:run");
            System.out.println("  ./gradlew :demos:" + options.name() + ":android:installDebug");
            System.out.println("  ./gradlew :demos:" + options.name() + ":core:test");
        } catch (GeneratorException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException | UncheckedIOException e) {
            System.err.println("Creating the game failed: " + e.getMessage());
            System.exit(2);
        }
    }
}
