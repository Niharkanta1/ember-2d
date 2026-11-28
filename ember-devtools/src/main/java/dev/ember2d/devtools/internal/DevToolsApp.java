package dev.ember2d.devtools.internal;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import dev.ember2d.app.EmberGame;
import dev.ember2d.app.EngineContext;
import dev.ember2d.assets.AssetReloadFailed;
import dev.ember2d.assets.AssetReloaded;
import dev.ember2d.assets.AssetWatcher;
import dev.ember2d.core.Log;
import dev.ember2d.event.Subscription;
import dev.ember2d.input.InputCapture;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs the game and draws the dev tools over it in the same LWJGL3 window (F-018, I-160), as found in spike S-01:
 * ImGui attaches to libGDX's GLFW window after the game's {@code create()}, draws after each game frame and shuts
 * down before the game is disposed. F1 shows and hides the tools; while ImGui wants the mouse or keyboard the engine's
 * {@link InputCapture} keeps those events from the game's actions.
 */
public final class DevToolsApp implements ApplicationListener {

    private static final Log LOG = Log.get("devtools");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final EmberGame game;
    private final ImGuiImplGlfw glfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 gl3 = new ImGuiImplGl3();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private DevState state;
    private Panels panels;
    private boolean attached;
    private Screenshots screenshots;
    private boolean visible;

    public DevToolsApp(EmberGame game) {
        this.game = Objects.requireNonNull(game, "game");
    }

    @Override
    public void create() {
        game.create();
        EngineContext context = game.context();
        AssetWatcher watcher = context.config().assetWatcher;
        state = new DevState(context, watcher instanceof ManualReloadWatcher m ? m : null);
        panels = new Panels(state);
        if (!(Gdx.graphics instanceof Lwjgl3Graphics graphics)) {
            LOG.warn("Dev tools need the LWJGL3 desktop backend; they are off");
            return;
        }
        ImGui.createContext();
        ImGui.getIO().setIniFilename(null);   // no imgui.ini: the layout is not saved
        glfw.init(graphics.getWindow().getWindowHandle(), true);   // forwards every event to libGDX's callbacks
        gl3.init(null);
        attached = true;

        ImGuiIO io = ImGui.getIO();
        context.input().setCapture(new InputCapture() {
            @Override
            public boolean capturesKeyboard() {
                return visible && io.getWantCaptureKeyboard();
            }

            @Override
            public boolean capturesPointer() {
                return visible && io.getWantCaptureMouse();
            }
        });
        subscriptions.add(context.events().monitor(e -> state.events.record(context.time().fixedTick(), e)));
        subscriptions.add(context.events().onGlobal(AssetReloaded.class,
                e -> state.reloaded(LocalTime.now().format(CLOCK) + "  " + e.id() + "  (" + e.path() + ")")));
        subscriptions.add(context.events().onGlobal(AssetReloadFailed.class, e -> {
            state.lastReloadError = LocalTime.now().format(CLOCK) + "  " + e.id() + " (" + e.path() + "): "
                    + e.problem();
            state.reloaded(LocalTime.now().format(CLOCK) + "  " + e.id() + "  FAILED");
        }));
        LOG.info("Dev tools ready (ImGui " + ImGui.getVersion() + "); press F1");
    }

    @Override
    public void render() {
        long start = System.nanoTime();
        game.render();
        if (screenshots == null) {
            screenshots = new Screenshots(System.getProperty("ember.screenshotDir"),
                    System.getProperty("ember.screenshotAt"));
        }
        screenshots.afterFrame(Gdx.graphics.getDeltaTime());   // before the panels: they never show in a shot
        if (!attached) {
            return;
        }
        state.frames.add(Gdx.graphics.getDeltaTime() * 1000f, (System.nanoTime() - start) / 1_000_000f);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            visible = !visible;
        }
        if (!visible) {
            return;
        }
        gl3.newFrame();
        glfw.newFrame();
        ImGui.newFrame();
        try {
            panels.draw();
        } catch (RuntimeException e) {
            LOG.error("Dev tools panel failed", e);   // keep the game running
        }
        ImGui.render();
        gl3.renderDrawData(ImGui.getDrawData());
    }

    @Override
    public void resize(int width, int height) {
        game.resize(width, height);
    }

    @Override
    public void pause() {
        game.pause();
    }

    @Override
    public void resume() {
        game.resume();
    }

    @Override
    public void dispose() {
        subscriptions.forEach(Subscription::cancel);
        if (attached) {
            gl3.shutdown();
            glfw.shutdown();
            ImGui.destroyContext();
            attached = false;
        }
        game.dispose();
    }
}
