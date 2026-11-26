package dev.ember2d.animation;

import dev.ember2d.core.PublicApi;

/** A state machine entered a state (F-007, I-135); queued. */
@PublicApi
public record AnimationStateEntered(int entity, String state) {
}
