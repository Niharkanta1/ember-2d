# S-01 — Dear ImGui (imgui-java) in the Ember2D LWJGL3 window

- Time box: 1 day (used: about half, including two interactive sessions with the developer)
- Phase: 1
- Related spec/ADR: ADR-009, F-018, F-014
- Spike code: branch `spike/s-01-imgui` (pushed for reference, never merged)
- Date: 2026-11-26

## Question
Can imgui-java render panels in the Ember2D LWJGL3 window and share input correctly with the game?

## Approach
- imgui-java **1.92.7.1** (`imgui-java-binding`, `imgui-java-lwjgl3`, `imgui-java-natives-windows`) added to the platformer demo's desktop launcher on the spike branch.
- A wrapper `ApplicationListener` around `EmberGame` (no engine changes): after `create()` it attaches ImGui to libGDX's GLFW window; after each game frame (world image already on the window) it draws a test panel (text, text field, button, slider, input counters).
- A game-side libGDX `InputProcessor` counts clicks and keys only when ImGui does not want them (`io.getWantCaptureMouse()` / `getWantCaptureKeyboard()`), logging every event with ImGui's view of the mouse.
- Rendering checked by saving the frame (after ImGui) to a PNG from inside the game; cost measured with vsync on and with vsync off and the frame cap removed, with ImGui on and off.
- Input checked by the developer on Windows 11 (computer use cannot target a plain `java.exe` window).

## Out of scope
Real dev-tool panels, the F1 toggle, hot reload, docking and multi-viewport, macOS and Linux, high-DPI scaling other than 1.0.

## Finding
**Answer: yes.** ImGui renders over the game in the same window and input is shared correctly, provided the game ignores events ImGui wants. One dependency consequence: imgui-java 1.92 needs LWJGL 3.4.1, which also upgrades libGDX's LWJGL.

**Evidence**

| Check | Result |
| --- | --- |
| LWJGL | Gradle resolves libGDX 1.14.2's LWJGL 3.3.3 up to **3.4.1** (imgui-java 1.92); libGDX runs normally on it (boot, scene, rendering, resize, shutdown) |
| Attach to libGDX's window | `((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle()`, then `ImGuiImplGlfw.init(handle, true)` and `ImGuiImplGl3.init(null)` in `create()`, after `game.create()` |
| OpenGL context | libGDX's default compatibility context works (GL 4.6, NVIDIA); no core-profile switch needed |
| Rendering | Saved frame shows the panel at the expected place with all widgets, over the letterboxed world; the game renders normally on following frames (ImGui draws after the engine's frame) |
| Clicks on the panel ("Press me" x3) | `wantsMouse=true`, routed to ImGui, button presses 3, game clicks 0 |
| Typing in the text field (`hello`) | `wantsKeyboard=true` for every key, field ends `"hello"`, game keys 0 |
| Slider drag | Value 0.5 to 0.615 |
| Clicks outside the panel (x10, and 54 in the first session) | `wantsMouse=false`, all counted by the game |
| Keys outside the panel | Reached the game (`L-Alt -> game` in an unattended run) |
| Window resize | libGDX resize events flow; game re-letterboxes; panel usable |
| Cost, vsync off, uncapped | 5,714 FPS without ImGui, 3,067 FPS with: **about 0.15 ms per frame** for the test panel (ImGui's own split: UI 0.15 ms, GL draw 0.14 ms) |
| Cost, vsync on | GL draw step measures 4-5 ms: the driver waits for the next refresh inside ImGui's first GL calls; not real cost |

**Integration pattern (for `ember-devtools`, I-160)**
1. `create()`: after the engine's `create()`, `ImGui.createContext()`, disable `imgui.ini` (`io.setIniFilename(null)`), `ImGuiImplGlfw.init(windowHandle, true)` (installs ImGui's GLFW callbacks and forwards every event to libGDX's), `ImGuiImplGl3.init(null)`.
2. Every frame, after the engine frame: `gl3.newFrame()`, `glfw.newFrame()`, `ImGui.newFrame()`, panels, `ImGui.render()`, `gl3.renderDrawData(ImGui.getDrawData())`.
3. `dispose()`: `gl3.shutdown()`, `glfw.shutdown()`, `ImGui.destroyContext()`, then the engine.
4. **Input routing**: both ImGui and libGDX receive every event, so game input must ignore mouse events while `io.getWantCaptureMouse()` and keyboard events while `io.getWantCaptureKeyboard()` are true.

**Surprises**
- The LWJGL upgrade is forced by the imgui-java version, not chosen.
- With vsync on, ImGui's draw call appears to take 4-5 ms; real cost must be measured uncapped.
- Repeated "Dropped 10 fixed step(s)" warnings appeared only in the interactive sessions, while the developer switched between windows (large frame gaps when the window loses or regains focus); not seen in unattended runs. Not caused by ImGui as far as observed; worth watching when the window loses focus.

## Consequences (proposed; each needs approval)
1. **Pin imgui-java 1.92.7.1 and LWJGL 3.4.1 for every desktop build** (a dependency constraint), so dev builds (with `ember-devtools`) and release builds (without) run the same LWJGL. Otherwise release would run 3.3.3 and dev 3.4.1. Add both to the dependency register with this note.
2. **Input actions (I-100) need a "UI captures input" hook**: the engine input layer asks a capture predicate before handling mouse and keyboard events; `ember-devtools` sets it from ImGui's `wantCapture*` flags (later also Scene2D UI, F-012).
3. **`ember-devtools` needs an engine hook to draw after the frame** (the spike wrapped the `ApplicationListener`). Options for I-160: a desktop-side wrapper like the spike, or a small post-frame hook in `EmberGame`. Decide in I-160.
4. **Window focus and frame gaps**: decide (with input, I-100) whether losing window focus should pause the game or reset the accumulator like a resume, to avoid catch-up after focus changes.
5. Untested here: macOS (needs `-XstartOnFirstThread`, already handled by `StartupHelper`), Linux, high-DPI framebuffer scale, docking. Check when `ember-devtools` is built.
