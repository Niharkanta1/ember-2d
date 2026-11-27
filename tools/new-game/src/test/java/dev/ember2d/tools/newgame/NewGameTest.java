package dev.ember2d.tools.newgame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ember2d.tools.newgame.NewGame.GeneratorException;
import dev.ember2d.tools.newgame.NewGame.Options;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NewGameTest {

    private static final Path REPO = Path.of(System.getProperty("ember.repo", "../.."));

    @TempDir
    Path target;

    // ------------------------------------------------------------------------------------------------
    // Options
    // ------------------------------------------------------------------------------------------------

    @Test
    void defaultsComeFromTheName() {
        Options options = Options.of("forest-run", "com.example.forest", null, null);
        assertThat(options.problems()).isEmpty();
        assertThat(options.title()).isEqualTo("Forest Run");
        assertThat(options.template()).isEqualTo("blank");
        assertThat(options.className()).isEqualTo("ForestRunGame");
        assertThat(options.componentsClassName()).isEqualTo("ForestRunComponents");
    }

    @Test
    void aNameEndingInGameIsNotDoubled() {
        Options options = Options.of("space-game", "com.example.space", "", "");
        assertThat(options.className()).isEqualTo("SpaceGame");
        assertThat(options.componentsClassName()).isEqualTo("SpaceGameComponents");
    }

    @ParameterizedTest
    @ValueSource(strings = {"My-Game", "my_game", "1game", "my--game", "game-", "-game",
            "a-very-long-name-that-goes-on-and-on-and-on"})
    void badNamesAreRefused(String name) {
        assertThat(Options.of(name, "com.example.game", "Title", "blank").problems())
                .anySatisfy(p -> assertThat(p).contains("gameName"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"game", "com.Example.game", "com.example.class", "com..game", "com.example.2d",
            "dev.ember2d.game", "dev.ember2d", "com.example-x.game"})
    void badPackagesAreRefused(String pkg) {
        assertThat(Options.of("my-game", pkg, "Title", "blank").problems())
                .anySatisfy(p -> assertThat(p).contains("gamePackage"));
    }

    @Test
    void demoPackagesAreAllowedButNotTheTemplatesOwn() {
        assertThat(Options.of("my-game", "dev.ember2d.demo.mine", null, "shooter").problems()).isEmpty();
        assertThat(Options.of("my-game", "dev.ember2d.demo.shooter", null, "shooter").problems())
                .anySatisfy(p -> assertThat(p).contains("template's own package"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Say \"hi\"", "Bob's Game", "A & B", "Back\\slash", "<b>", "Two\nlines"})
    void titlesThatBreakFilesAreRefused(String title) {
        assertThat(Options.of("my-game", "com.example.game", title, "blank").problems())
                .anySatisfy(p -> assertThat(p).contains("gameTitle"));
    }

    @Test
    void unknownTemplatesAreRefusedAndEveryProblemIsListed() {
        List<String> problems = Options.of("Bad", "bad", "x\"", "racing").problems();
        assertThat(problems).hasSize(4);
        assertThat(problems.get(3)).contains("racing").contains("blank, platformer, shooter, rpg, novel");
    }

    // ------------------------------------------------------------------------------------------------
    // Generating
    // ------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"platformer", "shooter", "rpg", "novel"})
    void demoTemplatesAreRenamedThroughout(String template) throws Exception {
        writeSettings();
        Options options = Options.of("forest-run", "com.example.forest", "Forest Run", template);
        List<Path> written = NewGame.generate(REPO, target, options);

        Path game = target.resolve("demos/forest-run");
        assertThat(written).contains(Path.of("settings.gradle"));
        assertThat(game.resolve("core/src/main/java/com/example/forest/ForestRunGame.java")).isRegularFile();
        assertThat(game.resolve("core/src/main/java/com/example/forest/ForestRunComponents.java")).isRegularFile();
        assertThat(game.resolve("desktop/src/main/java/com/example/forest/desktop/DesktopLauncher.java"))
                .isRegularFile();
        assertThat(game.resolve("android/src/main/java/com/example/forest/android/AndroidLauncher.java"))
                .isRegularFile();
        assertThat(game.resolve("core/src/main/java/dev")).doesNotExist();
        assertThat(game.resolve("core/build")).doesNotExist();

        for (Path file : textFiles(game)) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(content).as(file.toString())
                    .doesNotContain("dev.ember2d.demo." + template)
                    .doesNotContain(":demos:demo-" + template)
                    .doesNotContain("'demos/demo-" + template)
                    .doesNotContain(template + "-demo\"");
        }
        String android = Files.readString(game.resolve("android/build.gradle"), StandardCharsets.UTF_8);
        assertThat(android).contains("applicationId = 'com.example.forest'")
                .contains("'com.example.forest.ForestRunComponents'")
                .contains("rootProject.file('demos/forest-run/core/assets')");
        assertThat(Files.readString(game.resolve("android/src/main/res/values/strings.xml"), StandardCharsets.UTF_8))
                .contains(">Forest Run<");
        String desktop = Files.readString(game.resolve("desktop/src/main/java/com/example/forest/desktop/"
                + "DesktopLauncher.java"), StandardCharsets.UTF_8);
        assertThat(desktop).contains("GAME_NAME = \"forest-run\"").contains("\"Forest Run \" + version")
                .contains("ForestRunGame.config(");

        String settings = Files.readString(target.resolve("settings.gradle"), StandardCharsets.UTF_8);
        assertThat(settings).contains("include 'demos:forest-run:core'", "include 'demos:forest-run:desktop'",
                "include 'demos:forest-run:android'");

        // Binary files are identical copies.
        Path demoAssets = REPO.resolve("demos/demo-" + template + "/core/assets");
        try (Stream<Path> walk = Files.walk(demoAssets)) {
            Path png = walk.filter(f -> f.toString().endsWith(".png")).findFirst().orElseThrow();
            assertThat(game.resolve("core/assets").resolve(demoAssets.relativize(png).toString()))
                    .hasSameBinaryContentAs(png);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"rpg", "novel"})
    void inkSourcesMoveIntoTheGame(String template) throws Exception {
        writeSettings();
        NewGame.generate(REPO, target, Options.of("tale", "com.example.tale", null, template));
        Path game = target.resolve("demos/tale");
        try (Stream<Path> ink = Files.list(game.resolve("core/ink"))) {
            assertThat(ink.map(p -> p.getFileName().toString())).isNotEmpty().allMatch(n -> n.endsWith(".ink"));
        }
        assertThat(Files.readString(game.resolve("core/build.gradle"), StandardCharsets.UTF_8))
                .contains("rootProject.fileTree('demos/tale/core/ink')");
        if (template.equals("novel")) {
            assertThat(Files.readString(game.resolve("core/src/test/java/com/example/tale/ChapterTest.java"),
                    StandardCharsets.UTF_8)).contains("Path.of(\"ink\", \"chapter1.ink\")");
        }
    }

    @Test
    void theBlankTemplateHasAMinimalCoreAndTheShooterLaunchers() throws Exception {
        writeSettings();
        NewGame.generate(REPO, target, Options.of("my-game", "com.example.mygame", "My Game", "blank"));
        Path game = target.resolve("demos/my-game");
        Path src = game.resolve("core/src/main/java/com/example/mygame");
        try (Stream<Path> files = Files.list(src)) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .containsExactlyInAnyOrder("MyGame.java", "MyGameComponents.java", "MainScene.java");
        }
        assertThat(game.resolve("core/src/test/java/com/example/mygame/ArchitectureTest.java")).isRegularFile();
        assertThat(game.resolve("core/assets/fonts/PressStart2P-Regular.ttf"))
                .hasSameBinaryContentAs(REPO.resolve("demos/demo-shooter/core/assets/fonts/PressStart2P-Regular.ttf"));
        assertThat(game.resolve("core/assets/manifest.json")).isRegularFile();
        assertThat(Files.readString(game.resolve("core/build.gradle"), StandardCharsets.UTF_8))
                .doesNotContainPattern("(?m)^\\s*api project\\(':kits").contains("api project(':ember-core')");
        assertThat(Files.readString(src.resolve("MainScene.java"), StandardCharsets.UTF_8))
                .contains("package com.example.mygame;").contains("\"My Game\"")
                .doesNotContain("@GAME@").doesNotContain("@TITLE@");
        for (Path file : textFiles(game)) {
            assertThat(Files.readString(file, StandardCharsets.UTF_8)).as(file.toString())
                    .doesNotContain("shooter").doesNotContain("Shooter").doesNotContain("@PACKAGE@");
        }
        assertThat(game.resolve("desktop/src/dev/java/com/example/mygame/desktop/DevLauncher.java")).isRegularFile();
    }

    @Test
    void anExistingGameIsRefusedWithoutWritingAnything() throws Exception {
        writeSettings();
        Files.createDirectories(target.resolve("demos/my-game"));
        String before = Files.readString(target.resolve("settings.gradle"), StandardCharsets.UTF_8);
        assertThatThrownBy(() -> NewGame.generate(REPO, target, Options.of("my-game", "com.example.g", null, null)))
                .isInstanceOf(GeneratorException.class).hasMessageContaining("demos/my-game already exists");
        assertThat(Files.readString(target.resolve("settings.gradle"), StandardCharsets.UTF_8)).isEqualTo(before);
        try (Stream<Path> files = Files.list(target.resolve("demos/my-game"))) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void aGameAlreadyInSettingsIsRefused() throws Exception {
        Files.writeString(target.resolve("settings.gradle"), "include 'demos:my-game:core'\n");
        assertThatThrownBy(() -> NewGame.generate(REPO, target, Options.of("my-game", "com.example.g", null, null)))
                .isInstanceOf(GeneratorException.class).hasMessageContaining("already includes");
        assertThat(target.resolve("demos")).doesNotExist();
    }

    @Test
    void invalidOptionsAreRefusedBeforeAnythingIsWritten() throws Exception {
        writeSettings();
        assertThatThrownBy(() -> NewGame.generate(REPO, target, Options.of("Bad Name", "x", null, null)))
                .isInstanceOf(GeneratorException.class)
                .satisfies(e -> assertThat(((GeneratorException) e).problems()).hasSize(2));
        assertThat(target.resolve("demos")).doesNotExist();
    }

    @Test
    void aNameThatClashesWithATemplateClassIsRefused() throws Exception {
        writeSettings();
        // The RPG demo has a class called Game.
        assertThatThrownBy(() -> NewGame.generate(REPO, target, Options.of("game", "com.example.g", null, "rpg")))
                .isInstanceOf(GeneratorException.class).hasMessageContaining("already has a class Game");
    }

    @Test
    void buildFoldersAndIdeFilesAreSkipped() {
        assertThat(NewGame.skipped(Path.of("core/build/classes/A.class"))).isTrue();
        assertThat(NewGame.skipped(Path.of(".project"))).isTrue();
        assertThat(NewGame.skipped(Path.of("core/.settings/x.prefs"))).isTrue();
        assertThat(NewGame.skipped(Path.of("core/bin/main/A.class"))).isTrue();
        assertThat(NewGame.skipped(Path.of("desktop/logs/game.log"))).isTrue();
        assertThat(NewGame.skipped(Path.of("core/assets/maps/room.tmx"))).isFalse();
    }

    private void writeSettings() throws IOException {
        Files.writeString(target.resolve("settings.gradle"), "rootProject.name = 'test'\n");
    }

    private static List<Path> textFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).filter(NewGame::isText).toList();
        }
    }
}
