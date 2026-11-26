package dev.ember2d.data;

import dev.ember2d.core.PublicApi;

/**
 * A game's component registrations (F-010), in one place: the engine runs it at boot ({@code EngineConfig.components})
 * and the build runs it to generate R8 keep rules ({@link KeepRules}), so both always see the same components. Needs a
 * public no-argument constructor.
 */
@PublicApi
@FunctionalInterface
public interface ComponentSetup {

    void register(ComponentRegistry registry);
}
