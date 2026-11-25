package dev.ember2d.android;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import dev.ember2d.app.EmberGame;
import dev.ember2d.app.EngineConfig;
import dev.ember2d.core.Log;
import dev.ember2d.core.LogConfig;
import dev.ember2d.core.LogLevel;
import dev.ember2d.core.PublicApi;

/**
 * Base class for a game's Android launcher activity (F-022). A game implements {@link #config(boolean)} only;
 * the launcher starts an {@link EmberGame} with it. Android {@code onPause}/{@code onResume} reach the game as
 * suspend/resume through libGDX.
 *
 * <p>Dev mode is the app's debuggable flag: debug builds log at {@link LogLevel#DEBUG}, release builds at
 * {@link LogLevel#INFO}. Messages go to logcat.
 */
@PublicApi
public abstract class EmberAndroidLauncher extends AndroidApplication {

    private static final Log LOG = Log.get("launcher");

    /** The game's engine configuration; usually returned by the shared setup class in the game's core module. */
    protected abstract EngineConfig config(boolean devMode);

    /** The libGDX Android settings: immersive full screen, accelerometer and compass off. Override to change. */
    protected AndroidApplicationConfiguration androidConfig() {
        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true;
        configuration.useAccelerometer = false;
        configuration.useCompass = false;
        return configuration;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean devMode = isDebuggable();
        LogConfig.setDefaultLevel(devMode ? LogLevel.DEBUG : LogLevel.INFO);
        initialize(new EmberGame(config(devMode)), androidConfig());
        LOG.info(getPackageName() + " " + versionName() + (devMode ? " (dev mode)" : ""));
    }

    /** Whether the app was built debuggable (debug build type). */
    protected final boolean isDebuggable() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private String versionName() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return name != null ? name : "unknown";
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }
}
