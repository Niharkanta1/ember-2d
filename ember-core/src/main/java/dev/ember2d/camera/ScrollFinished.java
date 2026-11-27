package dev.ember2d.camera;

import dev.ember2d.core.PublicApi;

/** A {@link CameraScroll} reached the edge of its bounds (F-004, I-310); published once. */
@PublicApi
public record ScrollFinished(int camera) {
}
