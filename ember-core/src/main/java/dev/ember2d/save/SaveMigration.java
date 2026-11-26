package dev.ember2d.save;

import dev.ember2d.core.PublicApi;

/** Upgrades a slot's data by one version, in place (F-013). */
@PublicApi
@FunctionalInterface
public interface SaveMigration {

    void migrate(SaveData data);
}
