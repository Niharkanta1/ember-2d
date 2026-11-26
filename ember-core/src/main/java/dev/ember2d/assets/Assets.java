package dev.ember2d.assets;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.core.PublicApi;

/**
 * Assets by logical ID (F-008), available as {@code context.assets()}. IDs, paths, types and groups come from
 * the manifest ({@code manifest.json} at the assets root). The engine loads the {@code global} group at boot;
 * other groups are loaded with {@link #loadGroup(String)} and released with {@link #unloadGroup(String)}.
 * Games never load by path and never dispose assets.
 */
@PublicApi
public interface Assets {

    /**
     * A loaded asset.
     *
     * @throws IllegalArgumentException for an unknown ID (with the closest IDs) or a wrong type
     * @throws IllegalStateException if the asset's group is not loaded (names the group)
     */
    <T> T get(String id, Class<T> type);

    /**
     * A region: {@code "atlasId/regionName"}, or a texture ID for the whole texture. A missing atlas region
     * returns a magenta region and logs one warning per ID. Results are cached.
     */
    TextureRegion region(String id);

    /**
     * Starts loading a group in the background; the engine advances loading every frame. Loading a group that
     * is already loaded or loading returns its existing load.
     *
     * @throws IllegalArgumentException for an unknown group (lists the known groups)
     * @throws AssetLoadException if required files are missing (lists every missing ID and path)
     */
    AsyncLoad loadGroup(String group);

    /** Releases a group's assets; assets also in another loaded group stay loaded. */
    void unloadGroup(String group);

    /** Whether the asset is loaded (or replaced by its fallback). */
    boolean isLoaded(String id);

    /** Whether the manifest lists this ID. */
    boolean contains(String id);

    /**
     * Increases with every hot reload (dev builds, F-008). Caches of resolved assets compare it to notice a reload;
     * it stays 0 when nothing is ever reloaded.
     */
    default int generation() {
        return 0;
    }

    /** The file path of an asset ID, for messages; the ID itself if the manifest does not list it. */
    default String path(String id) {
        return id;
    }
}
