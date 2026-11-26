package dev.ember2d.animation;

import dev.ember2d.core.PublicApi;

/** A frame event (F-007): queued once each time the frame is entered. */
@PublicApi
public record AnimationEvent(int entity, String clip, String name) {
}
