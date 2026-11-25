package dev.ember2d.assets.internal;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import dev.ember2d.assets.AssetLoadException;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.core.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The engine's {@link Assets} over libGDX {@link AssetManager} (F-008). The engine calls {@link #update()} at the
 * start of every frame on the render thread and {@link #dispose()} at shutdown.
 */
public final class GdxAssets implements Assets, Disposable {

    private static final Log LOG = Log.get("assets");
    private static final int FALLBACK_SIZE = 16;

    private final class GroupLoad implements AsyncLoad {
        private final String group;
        private final List<AssetManifest.Entry> queued;

        GroupLoad(String group, List<AssetManifest.Entry> queued) {
            this.group = group;
            this.queued = queued;
        }

        @Override
        public String group() {
            return group;
        }

        @Override
        public float progress() {
            if (queued.isEmpty()) {
                return 1f;
            }
            int loaded = 0;
            for (AssetManifest.Entry entry : queued) {
                if (manager.isLoaded(entry.path(), entry.type().assetClass())) {
                    loaded++;
                }
            }
            return (float) loaded / queued.size();
        }

        @Override
        public boolean isDone() {
            return progress() >= 1f;
        }

        @Override
        public void finish() {
            for (AssetManifest.Entry entry : queued) {
                manager.finishLoadingAsset(entry.path());
            }
        }
    }

    private final AssetManifest manifest;
    private final FileHandleResolver resolver;
    private final AssetManager manager;
    private final ObjectMap<String, GroupLoad> loadedGroups = new ObjectMap<>();
    private final ObjectMap<String, Object> fallbacks = new ObjectMap<>();
    private final ObjectMap<String, TextureRegion> regions = new ObjectMap<>();
    private final ObjectSet<String> warnedRegions = new ObjectSet<>();
    private Pixmap magentaPixmap;
    private Texture magentaTexture;
    private BitmapFont fallbackFont;

    public GdxAssets(AssetManifest manifest, FileHandleResolver resolver) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.manager = new AssetManager(resolver);
        manager.setLoader(JsonValue.class, new JsonValueLoader(resolver));
    }

    /**
     * Reads the manifest from the assets root ({@code Gdx.files.internal}).
     *
     * @throws AssetLoadException if the manifest is missing or invalid
     */
    public static GdxAssets load(String manifestPath) {
        FileHandleResolver resolver = new InternalFileHandleResolver();
        FileHandle file = resolver.resolve(manifestPath);
        if (!file.exists()) {
            throw new AssetLoadException("Asset manifest not found", List.of(
                    manifestPath + " does not exist in the game's assets (EngineConfig.assetManifest)"));
        }
        return new GdxAssets(AssetManifest.parse(file.readString("UTF-8"), manifestPath), resolver);
    }

    public AssetManifest manifest() {
        return manifest;
    }

    /** Advances background loading; called by the engine every frame. */
    public void update() {
        manager.update();
    }

    @Override
    public <T> T get(String id, Class<T> type) {
        AssetManifest.Entry entry = entry(id);
        if (!type.isAssignableFrom(entry.type().assetClass())) {
            throw new IllegalArgumentException("Asset \"" + id + "\" is a " + entry.type().assetClass().getSimpleName()
                    + ", not a " + type.getSimpleName());
        }
        Object fallback = fallbacks.get(id);
        if (fallback != null) {
            return type.cast(fallback);
        }
        if (!manager.isLoaded(entry.path(), entry.type().assetClass())) {
            throw new IllegalStateException("Asset \"" + id + "\" is not loaded; load its group first: "
                    + (entry.groups().size() == 1 ? "\"" + entry.groups().get(0) + "\"" : "one of " + entry.groups()));
        }
        return type.cast(manager.get(entry.path(), entry.type().assetClass()));
    }

    @Override
    public TextureRegion region(String id) {
        TextureRegion cached = regions.get(id);
        if (cached != null) {
            return cached;
        }
        TextureRegion region;
        int slash = id.indexOf('/');
        if (slash < 0) {
            region = new TextureRegion(get(id, Texture.class));
        } else {
            String atlasId = id.substring(0, slash);
            String name = id.substring(slash + 1);
            if (fallbacks.containsKey(atlasId)) {
                region = magentaRegion();
            } else {
                region = get(atlasId, TextureAtlas.class).findRegion(name);
                if (region == null) {
                    if (warnedRegions.add(id)) {
                        LOG.warn("Region \"" + name + "\" not found in atlas \"" + atlasId + "\"; using the magenta fallback");
                    }
                    region = magentaRegion();
                }
            }
        }
        regions.put(id, region);
        return region;
    }

    @Override
    public AsyncLoad loadGroup(String group) {
        GroupLoad existing = loadedGroups.get(group);
        if (existing != null) {
            return existing;
        }
        List<AssetManifest.Entry> entries = manifest.group(group);
        if (entries == null) {
            throw new IllegalArgumentException("Unknown asset group \"" + group + "\" in " + manifest.source()
                    + "; known groups: " + manifest.groupNames());
        }
        List<String> missingRequired = new ArrayList<>();
        List<AssetManifest.Entry> present = new ArrayList<>();
        for (AssetManifest.Entry entry : entries) {
            if (resolver.resolve(entry.path()).exists()) {
                present.add(entry);
            } else if (entry.optional()) {
                if (!fallbacks.containsKey(entry.id())) {
                    LOG.warn("Optional asset \"" + entry.id() + "\" is missing (" + entry.path() + "); using a fallback");
                    fallbacks.put(entry.id(), fallback(entry.type()));
                }
            } else {
                missingRequired.add("\"" + entry.id() + "\": " + entry.path() + " does not exist");
            }
        }
        if (!missingRequired.isEmpty()) {
            throw new AssetLoadException("Cannot load asset group \"" + group + "\": required files are missing",
                    missingRequired);
        }
        for (AssetManifest.Entry entry : present) {
            manager.load(entry.path(), entry.type().assetClass());
        }
        GroupLoad load = new GroupLoad(group, List.copyOf(present));
        loadedGroups.put(group, load);
        LOG.debug("Loading asset group \"" + group + "\" (" + present.size() + " asset(s))");
        return load;
    }

    @Override
    public void unloadGroup(String group) {
        GroupLoad load = loadedGroups.remove(group);
        if (load == null) {
            return;
        }
        for (AssetManifest.Entry entry : load.queued) {
            if (manager.contains(entry.path())) {
                manager.unload(entry.path());   // decrements the reference count; shared assets stay loaded
            }
        }
        regions.clear();
        LOG.debug("Unloaded asset group \"" + group + "\"");
    }

    @Override
    public boolean isLoaded(String id) {
        AssetManifest.Entry entry = manifest.find(id);
        return entry != null && (fallbacks.containsKey(id) || manager.isLoaded(entry.path(), entry.type().assetClass()));
    }

    @Override
    public boolean contains(String id) {
        return manifest.find(id) != null;
    }

    @Override
    public void dispose() {
        manager.dispose();
        loadedGroups.clear();
        regions.clear();
        fallbacks.clear();
        if (magentaTexture != null) {
            magentaTexture.dispose();
            magentaPixmap.dispose();
        }
        if (fallbackFont != null) {
            fallbackFont.dispose();
        }
    }

    private AssetManifest.Entry entry(String id) {
        AssetManifest.Entry entry = manifest.find(id);
        if (entry == null) {
            List<String> closest = manifest.closestIds(id, 3);
            throw new IllegalArgumentException("Unknown asset id \"" + id + "\" in " + manifest.source()
                    + (closest.isEmpty() ? "" : "; closest: " + closest));
        }
        return entry;
    }

    private Object fallback(AssetManifest.Type type) {
        return switch (type) {
            case TEXTURE -> magentaTexture();
            case ATLAS -> new TextureAtlas();
            case FONT -> fallbackFont();
            case SOUND -> new NoOpSound();
            case MUSIC -> new NoOpMusic();
            case JSON -> new JsonValue(JsonValue.ValueType.object);
        };
    }

    private TextureRegion magentaRegion() {
        return new TextureRegion(magentaTexture());
    }

    private Texture magentaTexture() {
        if (magentaTexture == null) {
            magentaPixmap = new Pixmap(FALLBACK_SIZE, FALLBACK_SIZE, Pixmap.Format.RGBA8888);
            magentaPixmap.setColor(1f, 0f, 1f, 1f);
            magentaPixmap.fill();
            // Managed: libGDX re-uploads it from the pixmap after an Android graphics context loss.
            magentaTexture = new Texture(new PixmapTextureData(magentaPixmap, null, false, false, true));
        }
        return magentaTexture;
    }

    private BitmapFont fallbackFont() {
        if (fallbackFont == null) {
            fallbackFont = new BitmapFont();   // libGDX's built-in default font
        }
        return fallbackFont;
    }
}
