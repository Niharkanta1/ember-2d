package dev.ember2d.demo.platformer.desktop;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import dev.ember2d.core.Log;
import imgui.ImGui;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImString;

/**
 * SPIKE S-01 (throwaway, never merged): Dear ImGui drawn over the game in the same LWJGL3 window, sharing input.
 * Wraps the game; ImGui renders after the game's frame. Game input is counted only when ImGui does not want it.
 */
final class ImGuiSpike implements ApplicationListener {

    private static final Log LOG = Log.get("spike");

    private final ApplicationListener game;
    private final ImGuiImplGlfw glfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 gl3 = new ImGuiImplGl3();
    private final ImString text = new ImString(64);
    private final float[] slider = {0.5f};
    private int buttonPresses;
    private int gameClicks;
    private int gameKeys;
    private String lastGameEvent = "-";
    private long uiNanos;
    private long drawNanos;
    private int frames;

    ImGuiSpike(ApplicationListener game) {
        this.game = game;
    }

    @Override
    public void create() {
        game.create();
        long window = ((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle();
        ImGui.createContext();
        ImGui.getIO().setIniFilename(null);   // do not write imgui.ini
        glfw.init(window, true);              // installs ImGui callbacks and forwards to libGDX's
        gl3.init(null);                       // default GLSL version for the context
        LOG.info("ImGui " + ImGui.getVersion() + " attached to GLFW window " + window
                + "; GL " + Gdx.gl.glGetString(com.badlogic.gdx.graphics.GL20.GL_VERSION));

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int x, int y, int pointer, int button) {
                LOG.info("  diag: ImGui mouse pos " + ImGui.getIO().getMousePosX() + "," + ImGui.getIO().getMousePosY()
                        + " wantsMouse=" + ImGui.getIO().getWantCaptureMouse() + " hoveringAnyWindow="
                        + ImGui.isWindowHovered(imgui.flag.ImGuiHoveredFlags.AnyWindow));
                if (ImGui.getIO().getWantCaptureMouse()) {
                    LOG.info("click at " + x + "," + y + " -> ImGui");
                    return false;
                }
                gameClicks++;
                lastGameEvent = "click " + x + "," + y;
                LOG.info("click at " + x + "," + y + " -> game (" + gameClicks + ")");
                return true;
            }

            @Override
            public boolean keyTyped(char character) {
                LOG.info("  diag: libGDX keyTyped '" + character + "' wantsKeyboard=" + ImGui.getIO().getWantCaptureKeyboard());
                return false;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (ImGui.getIO().getWantCaptureKeyboard()) {
                    LOG.info("key " + com.badlogic.gdx.Input.Keys.toString(keycode) + " -> ImGui");
                    return false;
                }
                gameKeys++;
                lastGameEvent = "key " + com.badlogic.gdx.Input.Keys.toString(keycode);
                LOG.info("key " + com.badlogic.gdx.Input.Keys.toString(keycode) + " -> game (" + gameKeys + ")");
                return true;
            }
        });
    }

    private static final boolean IMGUI_OFF = Boolean.getBoolean("spike.noImGui");

    @Override
    public void render() {
        game.render();
        if (IMGUI_OFF) {
            if (++frames % 1000 == 0) {
                LOG.info("FPS " + Gdx.graphics.getFramesPerSecond() + " (ImGui off)");
            }
            return;
        }

        long start = System.nanoTime();
        gl3.newFrame();
        glfw.newFrame();
        ImGui.newFrame();
        ImGui.setNextWindowPos(20, 20, imgui.flag.ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(360, 230, imgui.flag.ImGuiCond.FirstUseEver);
        ImGui.begin("S-01 spike");
        ImGui.text("Game clicks: " + gameClicks + "   game keys: " + gameKeys);
        ImGui.text("Last game event: " + lastGameEvent);
        ImGui.inputText("type here", text);
        if (ImGui.button("Press me")) {
            buttonPresses++;
            LOG.info("ImGui button pressed (" + buttonPresses + ")");
        }
        ImGui.sameLine();
        ImGui.text("button presses: " + buttonPresses);
        ImGui.sliderFloat("slider", slider, 0f, 1f);
        ImGui.text("wants mouse: " + ImGui.getIO().getWantCaptureMouse()
                + "   wants keyboard: " + ImGui.getIO().getWantCaptureKeyboard());
        ImGui.text("text field: \"" + text.get() + "\"");
        ImGui.end();
        ImGui.render();
        long drawStart = System.nanoTime();
        gl3.renderDrawData(ImGui.getDrawData());
        long end = System.nanoTime();
        uiNanos += drawStart - start;
        drawNanos += end - drawStart;

        if (frames == 120 || frames == 600) {
            com.badlogic.gdx.graphics.Pixmap shot = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0, 0,
                    Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.local("logs/s01-frame-" + frames + ".png"), shot, 0, true);
            shot.dispose();
            LOG.info("  diag: saved logs/s01-frame-" + frames + ".png; ImGui display size " + ImGui.getIO().getDisplaySizeX()
                    + "x" + ImGui.getIO().getDisplaySizeY() + ", framebuffer scale " + ImGui.getIO().getDisplayFramebufferScaleX()
                    + ", draw lists " + ImGui.getDrawData().getCmdListsCount() + ", vertices " + ImGui.getDrawData().getTotalVtxCount());
        }
        if (frames % 1000 == 999) {
            LOG.info("FPS " + Gdx.graphics.getFramesPerSecond() + " (ImGui on)");
        }
        if (++frames % 300 == 0) {
            LOG.info("ImGui cost per frame (avg over " + frames + " frames): UI " + (uiNanos / frames / 1000)
                    + " us, GL draw " + (drawNanos / frames / 1000) + " us; "
                    + "text=\"" + text.get() + "\" slider=" + slider[0] + " buttons=" + buttonPresses);
        }
    }

    @Override
    public void resize(int width, int height) {
        game.resize(width, height);
        LOG.info("resize " + width + "x" + height);
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
        gl3.shutdown();
        glfw.shutdown();
        ImGui.destroyContext();
        game.dispose();
    }
}
