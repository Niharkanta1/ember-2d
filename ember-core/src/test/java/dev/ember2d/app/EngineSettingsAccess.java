package dev.ember2d.app;

/** Test access to the package-private EngineSettings.apply (boot) from other test packages. */
public final class EngineSettingsAccess {

    private EngineSettingsAccess() {
    }

    public static void apply(EngineContext context) {
        EngineSettings.apply(context);
    }
}
