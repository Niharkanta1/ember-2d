package dev.ember2d.assets;

import dev.ember2d.core.PublicApi;

/**
 * Hot reload (dev builds, F-008): a changed file was rejected and the old version of the asset stays. Published on
 * the global event bus.
 *
 * @param id      the asset ID
 * @param path    the file path in the assets
 * @param problem what is wrong with the new version
 */
@PublicApi
public record AssetReloadFailed(String id, String path, String problem) {
}
