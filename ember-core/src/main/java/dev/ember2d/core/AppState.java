package dev.ember2d.core;

/**
 * Application lifecycle state of an {@link EmberGame}: {@code CREATED -> RUNNING <-> SUSPENDED -> DISPOSED}.
 * Suspension is the platform pausing the app (window minimised, Android app in the background); it is not
 * gameplay pause.
 */
@PublicApi
public enum AppState {
    CREATED, RUNNING, SUSPENDED, DISPOSED
}
