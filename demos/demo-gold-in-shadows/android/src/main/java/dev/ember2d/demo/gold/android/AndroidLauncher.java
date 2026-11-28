package dev.ember2d.demo.gold.android;

import android.content.pm.PackageManager;
import dev.ember2d.android.EmberAndroidLauncher;
import dev.ember2d.app.EngineConfig;
import dev.ember2d.demo.gold.GoldGame;

/** Launches the game on Android. */
public class AndroidLauncher extends EmberAndroidLauncher {

    @Override
    protected EngineConfig config(boolean devMode) {
        EngineConfig config = GoldGame.config(devMode, versionName());
        config.debugOverlayVisibleAtStart = devMode;   // phones have no F3 key
        return config;
    }

    /** The package's version name (from the Gradle version), shown in the main menu (I-250). */
    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "dev";
        }
    }
}
