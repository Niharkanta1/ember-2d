package dev.ember2d.assets.internal;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.maps.tiled.BaseTiledMapLoader;
import com.badlogic.gdx.maps.tiled.TmjMapLoader;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.XmlReader;
import java.util.ArrayList;
import java.util.List;

/**
 * libGDX's Tiled map loaders (F-009) with Ember2D's checks: only finite orthogonal maps, and errors that name the map
 * and, for class properties, the missing {@code EngineConfig.tiledProject}. Used through the {@code AssetManager}.
 */
final class MapLoaders {

    private MapLoaders() {
    }

    /** TMX maps ({@code .tmx}). */
    static final class Tmx extends TmxMapLoader {
        Tmx(FileHandleResolver resolver) {
            super(resolver);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, Parameters parameter) {
            try {
                return super.getDependencies(fileName, file, parameter);
            } catch (RuntimeException e) {
                throw fail(fileName, e);
            }
        }

        @Override
        public void loadAsync(AssetManager manager, String fileName, FileHandle file, Parameters parameter) {
            check(fileName, root.getAttribute("orientation", null), root.getIntAttribute("infinite", 0) == 1);
            List<String> old = new ArrayList<>();
            classAttributes(root, old);
            rejectClassAttributes(fileName, old);
            try {
                super.loadAsync(manager, fileName, file, parameter);
            } catch (RuntimeException e) {
                throw fail(fileName, e);
            }
        }
    }

    /** Tiled JSON maps ({@code .tmj}); their tilesets and templates must be JSON too ({@code .tsj}, {@code .tj}). */
    static final class Tmj extends TmjMapLoader {
        Tmj(FileHandleResolver resolver) {
            super(resolver);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, Parameters parameter) {
            try {
                return super.getDependencies(fileName, file, parameter);
            } catch (RuntimeException e) {
                throw fail(fileName, e);
            }
        }

        @Override
        public void loadAsync(AssetManager manager, String fileName, FileHandle file, Parameters parameter) {
            check(fileName, root.getString("orientation", null), root.getBoolean("infinite", false));
            List<String> old = new ArrayList<>();
            classKeys(root.get("layers"), old);
            rejectClassAttributes(fileName, old);
            try {
                super.loadAsync(manager, fileName, file, parameter);
            } catch (RuntimeException e) {
                throw fail(fileName, e);
            }
        }
    }

    /** Loader parameters for a map: nearest filtering (libGDX default) and the Tiled project file, if any. */
    static BaseTiledMapLoader.Parameters parameters(String tiledProject) {
        BaseTiledMapLoader.Parameters parameters = new BaseTiledMapLoader.Parameters();
        parameters.projectFilePath = tiledProject;
        return parameters;
    }

    static void check(String fileName, String orientation, boolean infinite) {
        if (!"orthogonal".equals(orientation)) {
            throw new GdxRuntimeException("Tiled map " + fileName + ": orientation \"" + orientation
                    + "\" is not supported; Ember2D loads orthogonal maps only");
        }
        if (infinite) {
            throw new GdxRuntimeException("Tiled map " + fileName + ": infinite maps are not supported; "
                    + "turn off Map > Map Properties > Infinite in Tiled");
        }
    }

    /** TMX objects with the Tiled 1.9 {@code class} attribute and no {@code type}, which libGDX would drop. */
    private static void classAttributes(XmlReader.Element element, List<String> out) {
        for (int i = 0; i < element.getChildCount(); i++) {
            XmlReader.Element child = element.getChild(i);
            if (child.getName().equals("object") && child.hasAttribute("class") && !child.hasAttribute("type")) {
                out.add(describe(child.getAttribute("name", null), child.getAttribute("id", "?")));
            }
            classAttributes(child, out);
        }
    }

    /** TMJ objects with {@code "class"} and no {@code "type"}. */
    private static void classKeys(JsonValue layers, List<String> out) {
        if (layers == null) {
            return;
        }
        for (JsonValue layer = layers.child; layer != null; layer = layer.next) {
            classKeys(layer.get("layers"), out);
            JsonValue objects = layer.get("objects");
            for (JsonValue o = objects == null ? null : objects.child; o != null; o = o.next) {
                String cls = o.getString("class", "");
                if (!cls.isEmpty() && o.getString("type", "").isEmpty()) {
                    out.add(describe(o.getString("name", null), o.getString("id", "?")));
                }
            }
        }
    }

    private static String describe(String name, String id) {
        return (name == null || name.isEmpty() ? "(unnamed)" : "\"" + name + "\"") + " (id " + id + ")";
    }

    private static void rejectClassAttributes(String fileName, List<String> objects) {
        if (!objects.isEmpty()) {
            throw new GdxRuntimeException("Tiled map " + fileName + ": object(s) " + String.join(", ", objects)
                    + " use the Tiled 1.9 \"class\" attribute, which libGDX ignores; open and save the map with "
                    + "Tiled 1.10 or later (it writes \"type\")");
        }
    }

    static GdxRuntimeException fail(String fileName, RuntimeException e) {
        String message = String.valueOf(e.getMessage());
        if (message.startsWith("Tiled map " + fileName)) {
            return e instanceof GdxRuntimeException g ? g : new GdxRuntimeException(message, e);
        }
        String hint = message.contains("projectFilePath")
                ? " (the map uses class properties: set EngineConfig.tiledProject to the game's .tiled-project file)"
                : "";
        return new GdxRuntimeException("Tiled map " + fileName + ": " + message + hint, e);
    }
}
