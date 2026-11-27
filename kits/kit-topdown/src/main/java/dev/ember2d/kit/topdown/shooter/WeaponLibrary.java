package dev.ember2d.kit.topdown.shooter;

import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.Assets;
import dev.ember2d.core.PublicApi;
import dev.ember2d.kit.bullets.BulletTypes;
import java.util.HashMap;
import java.util.Map;

/** Weapon definitions by asset ID (F-016): parsed on first use and cached; a hot reload parses them again. */
@PublicApi
public final class WeaponLibrary {

    private final Assets assets;
    private final BulletTypes types;
    private final Map<String, WeaponDef> cache = new HashMap<>();
    private int generation;

    public WeaponLibrary(Assets assets, BulletTypes types) {
        this.assets = assets;
        this.types = types;
        this.generation = assets.generation();
    }

    /**
     * @throws IllegalArgumentException for an unknown ID or a bad file
     */
    public WeaponDef get(String id) {
        if (assets.generation() != generation) {
            generation = assets.generation();
            cache.clear();
        }
        WeaponDef def = cache.get(id);
        if (def == null) {
            if (!assets.contains(id)) {
                throw new IllegalArgumentException("Unknown weapon \"" + id + "\": not in the asset manifest");
            }
            def = WeaponDef.parse(id, assets.get(id, JsonValue.class), assets.path(id), types);
            cache.put(id, def);
        }
        return def;
    }
}
