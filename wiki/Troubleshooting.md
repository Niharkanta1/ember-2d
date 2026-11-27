# Troubleshooting

Problems collected from building the demos, with their causes. Each reference page has its own table too. This page gathers the common ones by symptom.

## Build
| Symptom | Cause | Fix |
| --- | --- | --- |
| `SDK location not found` | no Android SDK configured | `ANDROID_HOME` or `local.properties` (`sdk.dir=...`) |
| `Failed to find Build Tools revision 36.0.0` / `compile against version 36` | SDK parts missing | `sdkmanager "build-tools;36.0.0" "platforms;android-36"` |
| `Call requires API level NN (current min is 23)` | a Java API newer than Android 6 in shared code | use an API 23 alternative (e.g. a loop instead of `String.chars()`) |
| A Java API compile error that works in your IDE | compiled with `--release 17` | use Java 17 APIs |
| Architecture test failure | a forbidden import, package or genre word | fix the code (the message names the rule and the line) |
| `... is not listed in allowedModuleDependencies` | a module outside the allowed layout | put games under `demos/<game>/core|desktop|android` |
| `newGame` fails with `Cannot create the game:` | an option breaks its rule, or the game already exists | fix the listed options; pick a new name or remove the old game ([Creating a New Game Project](Creating-a-New-Game-Project)) |
| `TiledProjectTest` fails | the Tiled project file is stale (a component or prefab changed) | `./gradlew :demos:<game>:core:tiledTypes` |
| Gradle: "uses this output of task ':…:compileInk' without declaring a dependency" | `compileInk` and asset tasks in one build | add `mustRunAfter` ([Build Differences per Kit](Build-Differences-per-Kit)) |

## Start-up
| Symptom | Cause | Fix |
| --- | --- | --- |
| `EngineConfigException` listing fields | invalid config | fix the listed fields |
| `Unknown asset id "x"; closest: …` | typo, or the asset isn't in the manifest | fix the ID or add the entry |
| `Unknown input action "fire"` | a kit or component reads an action your bindings lack | bind it, or guard with `hasAction` |
| `unknown component "X" (registered: …)` | the component isn't in your `ComponentSetup` | register it |
| Android app closes at start | an exception | `adb logcat -d -s AndroidRuntime:E core:*` |
| Packaged desktop game closes at start | an exception | read the crash log (`%APPDATA%\<game>\`) |

## Drawing
| Symptom | Cause | Fix |
| --- | --- | --- |
| Magenta squares | a missing optional file, or a region not in the atlas | read the warning; fix the path or region name |
| A widget is a black box | made before the skin was set | `ui.setSkin(...)` before creating widgets |
| A sprite slides across the screen when spawned | position set without `teleport` | `transform.teleport(x, y)` |
| A character's head is drawn behind an object it stands in front of | the layer isn't Y-sorted, or origins aren't at the feet | `SortMode.Y_SORT`; `origin.y = 0` |
| Particles spawn but are invisible | a code-built emitter has transparency high 0 | `getTransparency().setHigh(1f)` |
| Effects don't show on a sprite | `paletteId`/`outline`/`flash` unset, or the shader failed (logged once) | check the fields; read the `render` log |
| The outline fills a shadow | old outline rule; current builds only outline fully transparent pixels | update |

## Gameplay
| Symptom | Cause | Fix |
| --- | --- | --- |
| Falling through the floor | tiles lack `solid`, or no `addTileGrid` | set `solid` in the tileset |
| Triggers never fire | no `CollisionSystem`, or it runs before movement | add it after movement |
| Bullets stop at enemies without hurting them | actors on the wall collision layer | actors on layer 1 |
| Enemies stand still | `Steering` has no target | set it (spawned enemies can't name the player in a prefab) |
| Contact damage only once | hit boxes hit once per activation | `hitbox.restart()` every second |
| A function in an Ink story runs twice | raw blade-ink binding with lookahead | use `DialogueRunner.bind` |
| `Story …: unknown staging tag "x:"` | a game tag with a colon in a novel story | plain tag (`#ending_x`) |
| The first line of a conversation is skipped | the opening press also confirmed | `DialogueBox` ignores it; do the same in your own box |
| Confirm in a pause menu also advances the story | a reader that ignores menus | `DialogueBox` yields to active menus |
| Loading puts the player in a travel zone and leaves again | saved inside the zone | save on arrival at a marker |

## Input and UI
| Symptom | Cause | Fix |
| --- | --- | --- |
| A menu ignores the keyboard | `ui` context not pushed | `input.pushContext("ui")` |
| Keys stop working after leaving a scene | a context left pushed | pop contexts in `exit()` |
| Nothing moves in the next scene | time left paused | unpause in `exit()` |
| A dialog's message runs off screen | `ConfirmDialog` labels don't wrap | shorter text |
| A tap happens by itself in an emulator test | `adb shell monkey` | use `am start` |

## Saves
| Symptom | Cause | Fix |
| --- | --- | --- |
| Settings lost after restart | no `saveFolder`, or `saveSettings()` never called | set both |
| Progress lost on Android | the process was killed in the background | save at checkpoints; `setSuspendSave` |
| A slot is `DAMAGED` | the file is broken | `loadBackup` |
| `TOO_NEW` | a newer game wrote it | expected; don't downgrade |

## Release
| Symptom | Cause | Fix |
| --- | --- | --- |
| A component works in debug but not in the release APK | not registered (no R8 keep rule) | register it in `ComponentSetup` |
| Google Play rejects the bundle | debug-signed | `ember.signing.*` properties |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | debug and release signatures differ | `adb uninstall` first |
| Packaging fails about `ember-devtools` | dev tools on the release classpath | keep them in `devImplementation` |

Still stuck? Read the first `ERROR` or `WARN` line in the log, check the F3 overlay and the collider view, and look at how the closest demo does it.
