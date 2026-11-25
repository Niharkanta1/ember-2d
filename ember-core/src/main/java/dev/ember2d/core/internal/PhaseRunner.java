package dev.ember2d.core.internal;

import dev.ember2d.core.Phase;

/** Receives each phase of a frame from the {@link GameLoop}, in order. */
@FunctionalInterface
public interface PhaseRunner {

    void run(Phase phase);
}
