# Build Differences per Kit

All games build the same way ([Building for Desktop](Building-for-Desktop), [Building for Android](Building-for-Android)). This page lists what **changes** depending on the kits a game uses, and which demos have been checked in which build.

## 1. Dependencies each kit adds
| Kit | Extra libraries | Notes |
| --- | --- | --- |
| kit-common | blade-ink 1.2.1 (Ink runtime) | needed only by `DialogueRunner`, but it is a dependency of the kit |
| kit-bullets | none | |
| kit-platformer | none | |
| kit-topdown | gdx-ai 1.8.2 (steering and A*) | |
| kit-novel | (blade-ink through kit-common) | |
| ember-box2d (optional module) | gdx-box2d 1.14.2 | add its natives to desktop and Android and the R8 keep rule ([Physics with Box2D](Physics-Box2D)) |
| gamepads (engine) | gdx-controllers 2.2.4 | nothing to add: `ember-desktop` and `ember-android` bring the backends, and `ember-android` ships the R8 rule |

- These libraries are already in the dependency register (`documents/TECHNICAL_DECISIONS.md`), in `gradle/libs.versions.toml` and on the Android API check's classpath. You add nothing.
- A game's `core/build.gradle` lists the kits it uses as `api project(':kits:kit-…')`.

## 2. Extra build steps
| Kit or feature | Step | When |
| --- | --- | --- |
| Ink stories (kit-common's `DialogueRunner`, kit-novel) | `./gradlew :demos:<game>:core:compileInk` with `ember.inklecate=<path to inklecate>` in `~/.gradle/gradle.properties` | after editing a `.ink` file; the compiled `.ink.json` is committed, so normal builds don't need inklecate |
| Tiled class-typed properties (any kit; used by the platformer demo) | `./gradlew :demos:<game>:core:tiledTypes` | after adding a component or changing a prefab; `TiledProjectTest` fails while the file is stale |
| Particle effects made in code (shooter demo) | `java -cp gdx-1.14.2.jar assets-src/demo-shooter/fx/MakeEffects.java` | after changing the generator; `.p` files are committed |
| Generated demo data | `python assets-src/demo-<game>/make_assets.py` | only to regenerate from scratch; after that the files are edited by hand |

### Setting up `compileInk` in your game
A game generated from the `rpg` or `novel` template already has it, reading `demos/<game>/core/ink/`. Otherwise, copy the `CompileInk` task from `demos/demo-rpg/core/build.gradle` and change the source folder. Then make the launchers' asset tasks run after it, so a combined build never reads half-written stories:
```groovy
// desktop/build.gradle
tasks.named('processResources') { mustRunAfter ':demos:my-game:core:compileInk' }
// android/build.gradle
tasks.matching { it.name.startsWith('merge') && it.name.endsWith('Assets') }.configureEach {
    mustRunAfter ':demos:my-game:core:compileInk'
}
```
inklecate 1.2.x is a separate download from the Ink project (Windows, macOS and Linux builds). It is not a build dependency.

## 3. R8 (Android release)
| Kit | What R8 must keep | How it is handled |
| --- | --- | --- |
| every kit | components created from data | generated keep rules from your `ComponentSetup` |
| kit-common (blade-ink) | the Ink runtime | verified: the RPG and novel release APKs run their stories after minification, with no extra rules |
| kit-topdown (gdx-ai) | steering and A* | built into the shooter's release APK; that APK has not been run |
| kit-novel | no components (empty `ComponentSetup`) | the keep-rules task still needs the setup class |

If you add a library that uses reflection, add its keep rules to your `android/proguard-rules.pro`, then run the release APK on a device.

## 4. Assets per kit
| Kit | Typical asset types |
| --- | --- |
| kit-platformer | `texture` sheets, `clips`, `asm`, `map`, `prefab` |
| kit-topdown (shooter) | `atlas`, `map`, `prefab`, `json` weapons |
| kit-bullets | `json` bullet types and `pattern.*` files |
| kit-topdown (rpg) | `json` items, `json` Ink stories, `map`, `prefab`, `clips` |
| kit-novel | `texture` backgrounds and CGs, `atlas` expressions, `json` cast, `json` stories, `music` |

All of them pack the same way: desktop serves `core/assets` from the classpath, and Android packs it into the APK or AAB.

## 5. Status of the four demos
| Demo | Desktop dev run | Desktop package | Android debug | Android release (R8) |
| --- | --- | --- | --- | --- |
| platformer | yes | Windows zip built and run without Java; Linux zip built in CI | emulator | emulator |
| shooter | started (not played with the mouse) | tasks configured, not built | emulator | built, not run |
| rpg | built and tested headless; not run by hand on desktop | tasks configured, not built | emulator | emulator |
| novel | built and tested headless; not run by hand on desktop | tasks configured, not built | emulator | emulator |

None of the demos has been run on a real phone yet; all Android checks used an API 23 emulator. macOS packages have never been tested.
