package dev.ember2d.demo.platformer.android;

import dev.ember2d.android.EmberAndroidLauncher;
import dev.ember2d.app.EngineConfig;
import dev.ember2d.demo.platformer.PlatformerDemo;

/** Launches the platformer demo on Android. */
public class AndroidLauncher extends EmberAndroidLauncher {

    @Override
    protected EngineConfig config(boolean devMode) {
        EngineConfig config = PlatformerDemo.config(devMode);
        config.debugOverlayVisibleAtStart = devMode;   // phones have no F3 key
        return config;
    }
}
