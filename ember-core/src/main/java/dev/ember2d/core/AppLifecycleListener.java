package dev.ember2d.core;

/**
 * Callbacks for application lifecycle changes. Every service registered in the {@link EngineContext} that
 * implements this interface receives them automatically, in registration order. An exception thrown by one
 * listener is logged and does not stop the others.
 */
@PublicApi
public interface AppLifecycleListener {

    /** The platform suspended the app (window minimised, Android app sent to the background). */
    default void onSuspend() {
    }

    /** The app is running again after {@link #onSuspend()}. */
    default void onResume() {
    }

    /** The drawable area changed size, in pixels. */
    default void onResize(int width, int height) {
    }
}
