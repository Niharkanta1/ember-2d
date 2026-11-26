package dev.ember2d.animation;

import dev.ember2d.core.PublicApi;

/** A state machine left a state (F-007, I-135); queued. */
@PublicApi
public record AnimationStateExited(int entity, String state) {
}
