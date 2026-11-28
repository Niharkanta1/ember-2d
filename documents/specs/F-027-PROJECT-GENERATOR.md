# F-027 — Project generator

- Status: Ready (written and approved in I-580, 2026-11-27: the developer asked for the generator, "build the generator")
- Phase: 6 (tooling)
- Module: `tools/new-game` (build-time tool; not part of any game)
- Depends on: F-020 (packaging), the demos as templates
- Tasks: I-580

## Goal
One command creates a complete, buildable game project with its own name, package and title. The new project has `core`, `desktop` and `android` modules and is registered in the build, so nobody copies or renames folders by hand.

## Scope
**Command** (from the repository root):
```
./gradlew newGame -PgameName=my-game -PgamePackage=com.example.mygame [-PgameTitle="My Game"] [-Ptemplate=blank]
```
| Property | Required | Rule | Default |
| --- | --- | --- | --- |
| `gameName` | yes | lower-case words joined by `-`: `[a-z][a-z0-9]*(-[a-z0-9]+)*`, at most 40 characters; becomes `demos/<gameName>`, the Construo package name and the save folder | — |
| `gamePackage` | yes | a Java package of at least two segments, each `[a-z][a-z0-9_]*`, no Java keywords; becomes the Java package, the Android `namespace` and `applicationId`, and the macOS identifier. Not inside `dev.ember2d`, except `dev.ember2d.demo.*` | — |
| `gameTitle` | no | at most 60 characters, without line breaks or any of `" ' \ < > &` (they would break the Java, Gradle and XML files); the window title, the Android app name and the package's human name | the name's words capitalised (`my-game` → `My Game`) |
| `template` | no | `blank`, `platformer`, `shooter`, `rpg` or `novel` | `blank` |

The main class names come from the name: `forest-run` → `ForestRunGame` (the config) and `ForestRunComponents` (the component setup). A name that ends in `-game` is not doubled: `space-game` → `SpaceGame` and `SpaceGameComponents`.

**What it creates:**
- `demos/<gameName>/core`, `desktop`, `android`, with sources in the new package and its folders.
- The three `include` lines appended to `settings.gradle`.
- For `rpg` and `novel`: the Ink sources in `demos/<gameName>/core/ink/`. The `compileInk` task reads them from there.

**Templates:**
- `blank` (new): a minimal game.
  - `core` depends on `ember-core` only.
  - Classes: the config, an empty component setup, and one `MainScene` that shows the title as world text (gently bobbing, a tween), a hint label in the UI, and quits on Escape (desktop) or Back (Android).
  - Assets: the Press Start 2P font with its licence, `manifest.json`, `input.json` and `ART-CREDITS.txt`.
  - Tests: an architecture test.
  - A `tiledTypes` task writing `assets/maps/<gameName>.tiled-project` (added in I-590 for the I-550 review).
  - The `desktop` and `android` modules come from the shooter demo (no Ink step).
- `platformer`, `shooter`, `rpg`, `novel`: a full copy of the demo, assets and tests included, with every name changed.

**Renaming rules for demo templates** (text files only; images, sounds and fonts are copied unchanged):
1. `assets-src/demo-<t>/ink` → `demos/<gameName>/core/ink`; the novel test's story path → `Path.of("ink", "chapter1.ink")`.
2. `dev.ember2d.demo.<t>` → the package (the `.desktop` and `.android` subpackages follow).
3. The template's config class (`PlatformerDemo`, `ShooterDemo`, `RpgDemo`, `NovelDemo`) → `<Game>Game`, and its component setup (`DemoComponents`, `ShooterComponents`, `RpgComponents`, `NovelComponents`) → `<Game>Components`. Files are renamed to match.
4. `Ember2D - <T> Demo`, `Ember2D <T> Demo`, the Android `app_name` (`Ember2D <T>`) and `<T> demo` → the title (`<T>` is `Platformer`, `Shooter`, `RPG` or `Novel`); `the <t> demo` → `the game`.
5. `demos/demo-<t>`, `:demos:demo-<t>` and `demo-<t>` → `demos/<gameName>`, `:demos:<gameName>` and `<gameName>`. Other `assets-src/demo-<t>` references (the generator scripts named in comments and `ART-CREDITS.txt`) keep pointing at the demo.
6. `<t>-demo` → `<gameName>` (the Construo name, the log file, the crash-log folder).

Other comments that mention "the demo" are left as they are. Text files are `.java`, `.gradle`, `.xml`, `.pro`, `.txt`, `.md`, `.properties` and `.ink`; JSON, maps and other assets contain no package or module names and are copied unchanged.

**Skipped while copying:** `build/`, `bin/`, `logs/`, every file or folder whose name starts with `.` (`.gradle/`, IDE files such as `.project` and `.settings/`) and `*.iml`.

**Checks before writing anything:** every problem is reported together, and nothing is written when any check fails:
- the options break their rules;
- `demos/<gameName>` already exists;
- `settings.gradle` already includes the game;
- the template already has a class with the new config or component class name (for example `game` with the `rpg` template, which has a class `Game`).

## Non-goals
- Creating projects outside this repository, or publishing Ember2D as a library (a separate decision).
- Changing an existing game's name.
- Templates for other genres than the four demos.
- An interactive wizard.

## API sketch
```java
package dev.ember2d.tools.newgame;
public final class NewGame {
    public record Options(String name, String pkg, String title, String template) {
        public static Options of(String name, String pkg, String title, String template);   // fills defaults
        public List<String> problems();                                                      // empty when valid
        public String className();                                                           // "ForestRunGame"
        public String componentsClassName();                                                 // "ForestRunComponents"
    }
    /** Copies a template from {@code repo} into {@code target} (usually the same folder) and registers the game. */
    public static List<Path> generate(Path repo, Path target, Options options) throws IOException, GeneratorException;
    public static void main(String[] args);   // --name= --package= --title= --template=
}
```
`GeneratorException` lists every problem. The blank template's files live in `tools/new-game/templates/blank/core` (`.tmpl` files with `@PACKAGE@`, `@GAME@`, `@COMPONENTS@`, `@TITLE@` and `@NAME@`). The `newGame` Gradle task (in `tools/new-game/build.gradle`) runs `main` with the repository root as the working directory.

## Acceptance criteria
- Each template generates a project that builds (`./gradlew build` with the new game included), with its tests passing.
- The generated `blank` game runs on desktop and shows its title.
- Generated sources contain no `dev.ember2d.demo.<template>` references.
- Invalid options and existing folders are refused with every problem listed, and nothing is written.

## Tests
- Option rules and defaults (names, packages, keywords, `dev.ember2d`, titles, templates, the class-name rule).
- Generating every template into a temporary folder:
  - package folders are moved and no old package or demo name is left;
  - the `settings.gradle` lines are added;
  - binary files are identical copies.
- Refusing an existing folder without writing anything.
- By hand in I-580: generate a game from each template into the real repository, run `./gradlew build` with them, run the blank game on desktop, then remove the test games.
