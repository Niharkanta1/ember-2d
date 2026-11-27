# Building for Desktop

How to run, test, package and ship an Ember2D game for **Windows, Linux and macOS**. Desktop games use the libGDX **LWJGL3** backend, and releases are packaged with **Construo** into zips that carry their own Java runtime. Players install nothing.

Spec F-020. Applies to every game module under `demos/<game>/desktop` (see [Build Differences per Kit](Build-Differences-per-Kit)).

---

## 1. Prerequisites
| Tool | Version | Why |
| --- | --- | --- |
| JDK | 21 (Temurin) | runs Gradle; compiles with `--release 17` |
| Gradle | the wrapper (9.9.2) | `./gradlew` |
| Android SDK | platform 36, build-tools 36.0.0 | every build includes the Android modules, even for desktop work |
| Disk and network | about 200 MB per packaging target, first time | Construo downloads a Temurin JDK 21 for each target, pinned by SHA-256 |

On Windows use `.\gradlew.bat` instead of `./gradlew`.

## 2. How the desktop module is set up
`demos/<game>/desktop/build.gradle`, shortened from the shooter demo:
```groovy
plugins {
    id 'application'
    alias(libs.plugins.construo)
}
dependencies {
    implementation project(':demos:demo-shooter:core')
    implementation project(':ember-desktop')
}
sourceSets { main { resources.srcDir rootProject.file('demos/demo-shooter/core/assets') } }   // assets on the classpath
application { mainClass = 'dev.ember2d.demo.shooter.desktop.DesktopLauncher' }
tasks.named('jar', Jar) { manifest { attributes 'Implementation-Title': 'Ember2D Shooter Demo', 'Implementation-Version': project.version } }

sourceSets { dev { /* DevLauncher: hot reload + dev tools */ } }
dependencies { devImplementation project(':ember-devtools') }
tasks.named('run', JavaExec) {
    classpath = sourceSets.dev.runtimeClasspath
    mainClass = 'dev.ember2d.demo.shooter.desktop.DevLauncher'
    systemProperty 'ember.devMode', 'true'
    systemProperty 'ember.assetsDir', rootProject.file('demos/demo-shooter/core/assets').absolutePath
}
tasks.register('releaseJar', Jar) { /* the game + all runtime dependencies in one jar */ }
construo {
    name.set('shooter-demo')
    humanName.set('Ember2D Shooter Demo')
    jarTask.set('releaseJar')
    targets.configure {
        create('winX64', Target.Windows)  { /* jdkUrl + jdkSha256 */ }
        create('linuxX64', Target.Linux)  { /* ... */ }
        create('macX64', Target.MacOs)    { identifier.set('dev.ember2d.demo.shooter') }
        create('macM1', Target.MacOs)     { identifier.set('dev.ember2d.demo.shooter') }
    }
}
// verifyReleaseClasspath: packaging fails if ember-devtools would be included
```
- **Assets:** the game's `core/assets` folder is the desktop's resource folder, so the same files serve desktop and Android.
- **Two launchers:**
  - `DesktopLauncher` (release) sets up logging, the crash log and the window, and starts `EmberGame`.
  - `DevLauncher` (only the `run` task) adds hot reload and the F1 dev tools. It lives in a separate `dev` source set, so release jars never contain it.
- **Window:** 1280 × 720, vsync on, foreground FPS = refresh rate + 1, title "<Game> <version>". Change these in `DesktopLauncher`.

## 3. Develop and test
```sh
./gradlew :demos:my-game:desktop:run                          # dev run: DEBUG logs, hot reload, F1 tools, F3 overlay
./gradlew :demos:my-game:desktop:run --args="--touch-controls"
./gradlew :demos:my-game:desktop:run --args="--safe-insets=80,0,80,0"
./gradlew :demos:my-game:core:test                             # your game's tests
./gradlew build                                                 # everything, including architecture rules
```
In a dev run:
- the log goes to the console and to `demos/<game>/desktop/logs/<game>-dev.log`;
- the window title says `dev` (the version comes from the jar manifest, which a dev run doesn't have).

To try **release mode** without packaging, run the main launcher without `-Dember.devMode=true`. The plain `application` distribution does that (section 6).

## 4. Version
The version is set once for the whole build in the root `build.gradle`:
```groovy
allprojects { version = '0.1.0-SNAPSHOT' }
```
It reaches:
- the jar manifest (`Implementation-Version`) and the window title;
- the game's menu, if you pass it into your config as the demos do;
- the package version;
- the Android version code and name ([Building for Android](Building-for-Android)).

## 5. Package a release (Construo)
```sh
./gradlew :demos:my-game:packageWinX64      # Windows x64
./gradlew :demos:my-game:packageLinuxX64    # Linux x64
./gradlew :demos:my-game:packageMacX64      # macOS Intel
./gradlew :demos:my-game:packageMacM1       # macOS Apple Silicon
```
Output: `demos/<game>/desktop/build/construo/dist/<name>-<target>.zip`, for example `platformer-demo-winX64.zip` (about 39 MB). Unzipped:
```text
platformer-demo.exe                      the launcher (Windows); a shell launcher on Linux, an .app on macOS
desktop-0.1.0-SNAPSHOT-release.jar       the game, engine, libGDX and natives (releaseJar)
runtime/                                 Temurin 21 trimmed by jlink
app/platformer-demo.json                 launcher settings (main class, class path)
```
**What packaging guarantees:**
- The JDK download is pinned by release and SHA-256; a tampered download fails the build.
- `verifyReleaseClasspath` fails packaging if `ember-devtools` would be included.
- Packaged builds run in **release mode**: `INFO` logging, no dev tools, no file watching, and crash logs.

**Verified so far:** the platformer's Windows zip, unzipped and run on a machine without Java. CI builds the platformer's Windows and Linux zips on every push to `main`. The shooter, RPG and novel demos have the same packaging tasks, but their zips have not been built and run yet. **The macOS targets are configured but have never been tested on a Mac.**

### Customising the package
| What | Where |
| --- | --- |
| File and folder name | `construo { name }` |
| Human-readable name | `construo { humanName }` |
| macOS bundle identifier | `identifier` on the mac targets |
| JDK version | `jdkUrl` and `jdkSha256` per target (update both together) |
| App icon | not configured in the demos. Construo supports an icon; see its documentation. This hasn't been tried in Ember2D. |

## 6. Other desktop outputs
| Task | Output | Needs Java on the player's machine |
| --- | --- | --- |
| `:demos:<game>:desktop:releaseJar` | `desktop/build/release-jar/desktop-<version>-release.jar`, a runnable jar (`java -jar …`) | yes, Java 17+ |
| `:demos:<game>:desktop:distZip` / `distTar` / `installDist` | Gradle's application distribution with start scripts (`./gradlew build` makes these too) | yes |
| `package<Target>` | the Construo zip | **no** |

For players, ship the Construo zip.

## 7. Crash logs and debugging a release
- An uncaught exception in a release build writes `crash-<yyyyMMdd-HHmmss>.log` (version, OS, Java version, stack trace) to:
  - Windows: `%APPDATA%\<game>\`
  - macOS: `~/Library/Application Support/<game>/`
  - Linux: `$XDG_DATA_HOME/<game>/` or `~/.local/share/<game>/`
- The path is also logged at `ERROR` level.
- **Testing the crash path:** start the packaged game with `--crash-test`. It throws at startup on purpose and writes a crash log.
- **Saves** live in `~/.ember2d/<saveFolder>/` (slots, `.bak` backups, `settings.json`, `global.json`), all readable JSON.

## 8. Distribution checklist
- [ ] Set the version in the root `build.gradle` (no `-SNAPSHOT` for a release).
- [ ] `./gradlew build` is green.
- [ ] Package each target you ship, and unzip and run each one on a clean machine (or a VM) without Java.
- [ ] Run once with `--crash-test` and confirm that the crash log appears.
- [ ] Credits and licences: the game's `ART-CREDITS.txt` and font licences (`OFL.txt`) are inside the jar with the assets. Mention them in your store page or readme as well.
- [ ] Upload the zips to your store or site (itch.io, Steam, …). Ember2D has no store integration.

## 9. Troubleshooting
| Symptom | Cause | Fix |
| --- | --- | --- |
| `SDK location not found` while building desktop | the Android modules are part of every build | set `ANDROID_HOME` or `local.properties` |
| Packaging fails with a checksum error | the JDK download changed or is corrupt | delete the cache and retry; update `jdkSha256` only for a JDK you chose to change |
| Packaging fails: `ember-devtools must not be packaged` | a release dependency on dev tools | keep `ember-devtools` in `devImplementation` only |
| The packaged game closes immediately | an exception at startup | read the crash log |
| The window title says `dev` | running from classes, not the packaged jar | expected in dev runs |
| A Java API compile error that works in your JDK | code is compiled with `--release 17` | use a Java 17 API |
| macOS: the app doesn't start | untested target; LWJGL needs the first thread | `StartupHelper` relaunches with `-XstartOnFirstThread`. Report what you see. |
