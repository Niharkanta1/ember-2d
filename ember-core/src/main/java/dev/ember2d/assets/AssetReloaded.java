package dev.ember2d.assets;

import dev.ember2d.core.PublicApi;

/**
 * Hot reload (dev builds, F-008): an asset was replaced by a changed file. Published on the global event bus.
 *
 * @param id   the asset ID
 * @param type the manifest type ({@code "map"}, {@code "json"}, ...)
 * @param path the file path in the assets
 */
@PublicApi
public record AssetReloaded(String id, String type, String path) {
}
