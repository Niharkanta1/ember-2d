package dev.ember2d.assets.internal;

import com.badlogic.gdx.Gdx;
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
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import dev.ember2d.assets.AssetLoadException;
import dev.ember2d.assets.AssetReloadFailed;
import dev.ember2d.assets.AssetReloaded;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.core.Log;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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
                if (manager.isLoaded(entry.key(), entry.type().assetClass())) {
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
                manager.finishLoadingAsset(entry.key());
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
    private String tiledProject;
    private String rootPrefix = "";   // absolute assets folder + '/' (hot reload); dependency names start with it
    private int generation;

    public GdxAssets(AssetManifest manifest, FileHandleResolver resolver) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.manager = newManager();
    }

    private AssetManager newManager() {
        AssetManager m = new AssetManager(resolver);
        m.setLoader(JsonValue.class, new JsonValueLoader(resolver));
        m.setLoader(TiledMap.class, ".tmx", new MapLoaders.Tmx(resolver));
        m.setLoader(TiledMap.class, ".tmj", new MapLoaders.Tmj(resolver));
        FontLoading.register(m, resolver);
        return m;
    }

    /**
     * Sets the Tiled project file passed to every map load (F-009, {@code EngineConfig.tiledProject}); {@code null}
     * for none. Call before loading maps.
     *
     * @throws AssetLoadException if the file does not exist
     */
    public void setTiledProject(String path) {
        if (path != null && !resolver.resolve(path).exists()) {
            throw new AssetLoadException("Tiled project file not found",
                    List.of(path + " does not exist in the game's assets (EngineConfig.tiledProject)"));
        }
        this.tiledProject = path;
    }

    /**
     * Reads the manifest from the assets root ({@code Gdx.files.internal}).
     *
     * @throws AssetLoadException if the manifest is missing or invalid
     */
    public static GdxAssets load(String manifestPath) {
        return load(manifestPath, new InternalFileHandleResolver(), "");
    }

    /**
     * Reads the manifest and every asset from an absolute folder instead of the internal assets: dev builds with hot
     * reload ({@code EngineConfig.assetsDir}, F-008), so saved files are read rather than the build's copy.
     *
     * @throws AssetLoadException if the folder or the manifest is missing, or the manifest is invalid
     */
    public static GdxAssets load(String manifestPath, String assetsDir) {
        FileHandle root = Gdx.files.absolute(assetsDir);
        if (!root.isDirectory()) {
            throw new AssetLoadException("Assets folder not found",
                    List.of(assetsDir + " is not a folder (EngineConfig.assetsDir)"));
        }
        String folder = slashes(root.path());
        return load(manifestPath, folderResolver(folder), folder + "/");
    }

    /** Resolves paths relative to an absolute folder; absolute paths (dependencies of loaded assets) as they are. */
    public static FileHandleResolver folderResolver(String folder) {
        String prefix = slashes(folder) + "/";
        return path -> {
            String p = slashes(path);
            return Gdx.files.absolute(p.startsWith(prefix) ? p : prefix + p);
        };
    }

    private static String slashes(String path) {
        return path.replace('\\', '/');
    }

    private static GdxAssets load(String manifestPath, FileHandleResolver resolver, String rootPrefix) {
        FileHandle file = resolver.resolve(manifestPath);
        if (!file.exists()) {
            throw new AssetLoadException("Asset manifest not found", List.of(
                    manifestPath + " does not exist in the game's assets (EngineConfig.assetManifest)"));
        }
        GdxAssets assets = new GdxAssets(AssetManifest.parse(file.readString("UTF-8"), manifestPath), resolver);
        assets.rootPrefix = rootPrefix;
        return assets;
    }

    /** The absolute folder prefix (ending in '/') that dependency file names start with; for folder resolvers. */
    public void setRootPrefix(String rootPrefix) {
        this.rootPrefix = slashes(rootPrefix);
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
        if (!manager.isLoaded(entry.key(), entry.type().assetClass())) {
            throw new IllegalStateException("Asset \"" + id + "\" is not loaded; load its group first: "
                    + (entry.groups().size() == 1 ? "\"" + entry.groups().get(0) + "\"" : "one of " + entry.groups()));
        }
        Object asset = manager.get(entry.key(), entry.type().assetClass());
        if (asset instanceof BitmapFont font && entry.font() != null && entry.font().markup()) {
            font.getData().markupEnabled = true;   // colour tags such as [RED]...[] (F-004)
        }
        return type.cast(asset);
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
            queue(manager, entry);
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
            if (manager.contains(entry.key())) {
                manager.unload(entry.key());   // decrements the reference count; shared assets stay loaded
            }
        }
        regions.clear();
        LOG.debug("Unloaded asset group \"" + group + "\"");
    }

    private void queue(AssetManager m, AssetManifest.Entry entry) {
        if (entry.type() == AssetManifest.Type.MAP) {
            m.load(entry.path(), TiledMap.class, MapLoaders.parameters(tiledProject));
        } else if (entry.type() == AssetManifest.Type.FREETYPE) {
            m.load(entry.key(), BitmapFont.class, FontLoading.parameters(entry.path(), entry.font()));
        } else {
            m.load(entry.path(), entry.type().assetClass());
        }
    }

    @Override
    public int generation() {
        return generation;
    }

    /**
     * Hot reload (F-008): the loaded entries a changed file affects. {@code path} is relative to the assets folder,
     * with '/'. An entry matches by its path, by a file it was loaded with (an atlas page, a map's tileset image), and
     * every loaded map matches a {@code .tsx} tileset or {@code .tx} template. Entries that are not loaded, or replaced
     * by a fallback, never match.
     */
    public List<AssetManifest.Entry> entriesForChange(String path) {
        boolean tiledShared = path.endsWith(".tsx") || path.endsWith(".tx");
        List<AssetManifest.Entry> out = new ArrayList<>();
        for (AssetManifest.Entry entry : manifest.entries()) {
            if (fallbacks.containsKey(entry.id()) || !manager.isLoaded(entry.key(), entry.type().assetClass())) {
                continue;
            }
            if (entry.path().equals(path) || (tiledShared && entry.type() == AssetManifest.Type.MAP)
                    || dependsOn(entry.key(), path, 0)) {
                out.add(entry);
            }
        }
        return out;
    }

    private boolean dependsOn(String key, String path, int depth) {
        Array<String> dependencies = manager.getDependencies(key);
        if (dependencies == null || depth > 8) {
            return false;
        }
        for (String dependency : dependencies) {
            String d = slashes(dependency);
            if (!rootPrefix.isEmpty() && d.startsWith(rootPrefix)) {
                d = d.substring(rootPrefix.length());
            }
            if (d.equals(path) || dependsOn(dependency, path, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hot reload (F-008): loads each entry's file again and swaps it in, keeping its reference count (groups). Each
     * new version is first loaded on its own; one that fails is logged and the old version stays. Increases
     * {@link #generation()} when anything was replaced. Render thread only.
     *
     * @return one event per replaced entry
     */
    public List<AssetReloaded> reload(Collection<AssetManifest.Entry> entries) {
        return reload(entries, failure -> { });
    }

    /** As {@link #reload(Collection)}, reporting each rejected new version. */
    public List<AssetReloaded> reload(Collection<AssetManifest.Entry> entries, Consumer<AssetReloadFailed> failures) {
        List<AssetManifest.Entry> ok = new ArrayList<>();
        for (AssetManifest.Entry entry : entries) {
            String problem = check(entry);
            if (problem == null) {
                ok.add(entry);
            } else {
                LOG.error("Hot reload of " + entry.id() + " failed; keeping the old version: " + entry.path() + ": "
                        + problem);
                failures.accept(new AssetReloadFailed(entry.id(), entry.path(), problem));
            }
        }
        if (ok.isEmpty()) {
            return List.of();
        }
        long start = System.nanoTime();
        // Queued loads (a group still loading, or another group's reference to a loaded asset) must count before the
        // reference counts are read: unload() would otherwise just drop a queued request.
        manager.finishLoading();
        int[] references = new int[ok.size()];
        for (int i = 0; i < ok.size(); i++) {
            String key = ok.get(i).key();
            references[i] = manager.getReferenceCount(key);
            while (manager.contains(key)) {
                manager.unload(key);
            }
        }
        for (AssetManifest.Entry entry : ok) {
            queue(manager, entry);
        }
        List<AssetReloaded> events = new ArrayList<>(ok.size());
        for (int i = 0; i < ok.size(); i++) {
            AssetManifest.Entry entry = ok.get(i);
            manager.finishLoadingAsset(entry.key());
            manager.setReferenceCount(entry.key(), references[i]);
            events.add(new AssetReloaded(entry.id(), entry.type().manifestName(), entry.path()));
        }
        regions.clear();
        warnedRegions.clear();
        generation++;
        long ms = (System.nanoTime() - start) / 1_000_000;
        for (AssetReloaded e : events) {
            LOG.info("Reloaded " + e.id() + " (" + e.path() + ") in " + ms + " ms");
        }
        return events;
    }

    /** Loads the entry's file in a separate manager; null when it loads, otherwise the problem. */
    private String check(AssetManifest.Entry entry) {
        if (!resolver.resolve(entry.path()).exists()) {
            return "the file does not exist";
        }
        AssetManager trial = newManager();
        try {
            queue(trial, entry);
            trial.finishLoading();
            return null;
        } catch (RuntimeException e) {
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            return cause.getMessage() != null ? cause.getMessage() : cause.toString();
        } finally {
            trial.dispose();
        }
    }

    @Override
    public boolean isLoaded(String id) {
        AssetManifest.Entry entry = manifest.find(id);
        return entry != null && (fallbacks.containsKey(id) || manager.isLoaded(entry.key(), entry.type().assetClass()));
    }

    @Override
    public String path(String id) {
        AssetManifest.Entry entry = manifest.find(id);
        return entry == null ? id : entry.path();
    }

    @Override
    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (AssetManifest.Entry entry : manifest.entries()) {
            ids.add(entry.id());
        }
        return ids;
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
            case MAP -> new TiledMap();
            case FREETYPE -> fallbackFont();
            case PREFAB, CLIPS, ASM -> new JsonValue(JsonValue.ValueType.object);
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
