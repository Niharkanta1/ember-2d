package dev.ember2d.camera;

import dev.ember2d.core.PublicApi;

/** Trauma-based camera shake (F-004): add trauma on hits and explosions; it decays over time. */
@PublicApi
public interface CameraShake {

    /** Adds trauma (0 to 1 in total; clamped). Shake strength grows with trauma squared. */
    void addTrauma(float amount);
}
