package dev.ember2d.input.internal;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;
import dev.ember2d.core.Log;
import java.util.Arrays;

/**
 * The gamepad through gdx-controllers (F-005, I-530): the most recently used connected controller, read with its
 * standard mapping. Without a running app it reads nothing; a backend that fails to start is logged once and ignored.
 */
public final class GdxGamepads implements GamepadSource {

    private static final Log LOG = Log.get("input");
    private boolean failed;

    @Override
    public String poll(float[] values) {
        Arrays.fill(values, 0f);
        if (failed || Gdx.app == null) {
            return null;
        }
        try {
            Controller pad = Controllers.getCurrent();
            if (pad == null || !pad.isConnected()) {
                return null;
            }
            ControllerMapping m = pad.getMapping();
            int i = 0;
            values[i++] = button(pad, m.buttonA);
            values[i++] = button(pad, m.buttonB);
            values[i++] = button(pad, m.buttonX);
            values[i++] = button(pad, m.buttonY);
            values[i++] = button(pad, m.buttonBack);
            values[i++] = button(pad, m.buttonStart);
            values[i++] = button(pad, m.buttonL1);
            values[i++] = button(pad, m.buttonR1);
            values[i++] = button(pad, m.buttonL2);
            values[i++] = button(pad, m.buttonR2);
            values[i++] = button(pad, m.buttonLeftStick);
            values[i++] = button(pad, m.buttonRightStick);
            values[i++] = button(pad, m.buttonDpadUp);
            values[i++] = button(pad, m.buttonDpadDown);
            values[i++] = button(pad, m.buttonDpadLeft);
            values[i++] = button(pad, m.buttonDpadRight);
            i = stick(values, i, axis(pad, m.axisLeftX), axis(pad, m.axisLeftY));
            stick(values, i, axis(pad, m.axisRightX), axis(pad, m.axisRightY));
            String name = pad.getName();
            return name == null || name.isEmpty() ? "Gamepad" : name;
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            LOG.warn("Gamepads are off: the controller backend failed to start (" + e + ")");
            Arrays.fill(values, 0f);
            return null;
        }
    }

    /** Left, right, up, down from raw axes; gdx-controllers reports Y positive downwards. */
    private static int stick(float[] values, int i, float x, float y) {
        values[i++] = Math.max(0f, -x);
        values[i++] = Math.max(0f, x);
        values[i++] = Math.max(0f, -y);
        values[i++] = Math.max(0f, y);
        return i;
    }

    private static float button(Controller pad, int code) {
        return code != ControllerMapping.UNDEFINED && pad.getButton(code) ? 1f : 0f;
    }

    private static float axis(Controller pad, int code) {
        if (code == ControllerMapping.UNDEFINED) {
            return 0f;
        }
        float v = pad.getAxis(code);
        return Float.isNaN(v) ? 0f : Math.max(-1f, Math.min(1f, v));
    }
}
