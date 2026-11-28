package dev.ember2d.tools.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Updates a game's {@code manifest.json} from the files in its assets folder (F-028): existing entries stay as they are,
 * new files are appended with a type and ID from the folder conventions, missing files are reported (and removed with
 * {@code prune}).
 */
public final class ManifestTool {

    static final String MANIFEST = "manifest.json";

    private static final Set<String> IMAGES = Set.of("png", "jpg", "jpeg");
    private static final Set<String> AUDIO = Set.of("wav", "ogg", "mp3");
    private static final Set<String> IGNORED = Set.of("txt", "md", "tsx", "tiled-project", "tiled-session", "ase",
            "aseprite");
    /** Folders of string tables (F-025): their files get the ID {@code strings.<language>}. */
    private static final Set<String> STRING_FOLDERS = Set.of("i18n", "strings", "lang");
    /** A frame size in a file name, as asset packs write it: {@code Run (32x32).png}. */
    private static final Pattern FRAME_SIZE = Pattern.compile("\\s*\\(\\s*\\d+\\s*[xX]\\s*\\d+\\s*\\)");
    private static final Pattern XML_IMAGE = Pattern.compile("<image[^>]*\\ssource=\"([^\"]+)\"");
    /** A BMFont page line: {@code page id=0 file="pixel.png"}. */
    private static final Pattern FNT_PAGE = Pattern.compile("^page\\s.*\\sfile=\"([^\"]+)\"", Pattern.MULTILINE);

    private ManifestTool() {
    }

    /** What an update did. */
    public record Result(List<String> added, List<String> missing, List<String> removed, boolean written) {
    }

    /** Adds new files to {@code assetsDir/manifest.json}; with {@code prune} removes entries whose file is gone. */
    public static Result update(Path assetsDir, boolean prune) throws IOException {
        if (!Files.isDirectory(assetsDir)) {
            throw new IOException("Assets folder not found: " + assetsDir);
        }
        Path manifestFile = assetsDir.resolve(MANIFEST);
        List<JsonValue> entries = new ArrayList<>();
        int version = 1;
        if (Files.exists(manifestFile)) {
            JsonValue root;
            try {
                root = new JsonReader().parse(Files.readString(manifestFile, StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                throw new IOException(MANIFEST + " cannot be read (" + e.getMessage() + "); fix it first", e);
            }
            version = root.getInt("version", 1);
            JsonValue assets = root.get("assets");
            if (assets != null) {
                for (JsonValue e = assets.child; e != null; e = e.next) {
                    entries.add(e);
                }
            }
        }

        List<String> files = files(assetsDir);
        Set<String> loadedByOthers = loadedByOthers(assetsDir, files);
        Set<String> known = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue e : entries) {
            known.add(e.getString("path", ""));
            ids.add(e.getString("id", ""));
        }

        List<String> missing = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        Set<String> present = new HashSet<>(files);
        List<JsonValue> kept = new ArrayList<>();
        for (JsonValue e : entries) {
            String path = e.getString("path", "");
            if (present.contains(path)) {
                kept.add(e);
            } else if (prune) {
                removed.add(e.getString("id", "?") + " (" + path + ")");
            } else {
                missing.add(e.getString("id", "?") + " (" + path + ")");
                kept.add(e);
            }
        }

        List<String> added = new ArrayList<>();
        List<Map<String, Object>> newEntries = new ArrayList<>();
        for (String path : files) {
            if (known.contains(path) || loadedByOthers.contains(path)) {
                continue;
            }
            Map<String, Object> entry = entryFor(assetsDir, path);
            if (entry == null) {
                continue;
            }
            String id = (String) entry.get("id");
            String unique = id;
            for (int n = 2; ids.contains(unique); n++) {
                unique = id + "_" + n;
            }
            if (!unique.equals(id)) {
                System.out.println("warning: ID " + id + " is taken; " + path + " gets " + unique);
                entry.put("id", unique);
            }
            ids.add(unique);
            newEntries.add(entry);
            added.add(unique + " (" + path + ", " + entry.get("type") + ")");
        }

        boolean changed = !added.isEmpty() || !removed.isEmpty() || !Files.exists(manifestFile);
        if (changed) {
            StringBuilder out = new StringBuilder();
            out.append("{ \"version\": ").append(version).append(", \"assets\": [\n");
            List<String> lines = new ArrayList<>();
            for (JsonValue e : kept) {
                lines.add("  " + Json.write(e));
            }
            for (Map<String, Object> e : newEntries) {
                lines.add("  " + Json.write(e));
            }
            out.append(String.join(",\n", lines));
            out.append("\n] }\n");
            Files.writeString(manifestFile, out.toString(), StandardCharsets.UTF_8);
        }
        return new Result(added, missing, removed, changed);
    }

    /** The manifest entry for a new file, or null when the file is not an asset of its own. */
    static Map<String, Object> entryFor(Path assetsDir, String path) {
        String file = path.substring(path.lastIndexOf('/') + 1);
        String lower = file.toLowerCase(Locale.ROOT);
        String ext = extension(lower);
        if (path.equals(MANIFEST) || ext.isEmpty() || IGNORED.contains(ext) || lower.startsWith("ofl")
                || lower.startsWith("license") || lower.startsWith("licence")) {
            return null;
        }
        String type;
        String id;
        String group = "global";
        Map<String, Object> extra = new LinkedHashMap<>();
        if (path.startsWith("prefabs/") && ext.equals("json")) {
            type = "prefab";
            id = path.substring("prefabs/".length(), path.length() - ".json".length());
        } else if (lower.endsWith(".clips.json")) {
            type = "clips";
            id = "anim." + name(file, ".clips.json");
        } else if (lower.endsWith(".asm.json")) {
            type = "asm";
            id = "asm." + name(file, ".asm.json");
        } else if (ext.equals("json") && Files.exists(assetsDir.resolve(withoutExtension(path) + ".atlas"))) {
            type = "skin";
            id = "skin." + name(file, ".json");
        } else if (lower.endsWith(".ink.json")) {
            type = "json";
            id = "ink." + name(file, ".ink.json");
        } else if (ext.equals("json") && STRING_FOLDERS.contains(String.valueOf(topFolder(path)))) {
            type = "json";
            id = "strings." + name(file, ".json");   // string tables (F-025): strings.<language>
        } else if (ext.equals("json")) {
            type = "json";
            id = generalId(path);
        } else if (ext.equals("tmx") || ext.equals("tmj")) {
            type = "map";
            id = "map." + name(file, "." + ext);
            group = name(file, "." + ext);
        } else if (ext.equals("atlas")) {
            type = "atlas";
            id = "atlas." + name(file, ".atlas");
        } else if (ext.equals("p")) {
            type = "particle";
            id = "fx." + name(file, ".p");
        } else if (ext.equals("ttf") || ext.equals("otf")) {
            type = "freetype";
            id = "font." + name(file, "." + ext);
            Map<String, Object> font = new LinkedHashMap<>();
            font.put("size", 16);
            extra.put("font", font);
        } else if (ext.equals("fnt")) {
            type = "font";
            id = "font." + name(file, ".fnt");
        } else if (AUDIO.contains(ext)) {
            boolean music = ("/" + path).contains("/music/");
            type = music ? "music" : "sound";
            String top = topFolder(path);
            id = (music ? "music" : top == null || top.equals("sfx") || top.equals("sounds") ? "sfx" : singular(top))
                    + "." + rest(path, music ? "music" : top);
        } else if (IMAGES.contains(ext)) {
            type = "texture";
            id = generalId(path);
        } else {
            return null;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("path", path);
        entry.put("type", type);
        entry.put("group", group);
        entry.putAll(extra);
        return entry;
    }

    /** {@code <singular top folder>.<rest>}; {@code gfx} becomes {@code tex}; top-level files use their name. */
    static String generalId(String path) {
        String top = topFolder(path);
        String file = path.substring(path.lastIndexOf('/') + 1);
        if (top == null) {
            return name(file, "." + extension(file.toLowerCase(Locale.ROOT)));
        }
        String prefix = top.equals("gfx") || top.equals("images") || top.equals("textures") ? "tex" : singular(top);
        return prefix + "." + rest(path, top);
    }

    private static String rest(String path, String top) {
        String rest = top == null ? path : path.substring(path.indexOf(top + "/") + top.length() + 1);
        String noExt = rest;
        String lower = noExt.toLowerCase(Locale.ROOT);
        for (String compound : List.of(".ink.json", ".clips.json", ".asm.json")) {
            if (lower.endsWith(compound)) {
                noExt = noExt.substring(0, noExt.length() - compound.length());
                return clean(noExt);
            }
        }
        return clean(withoutExtension(noExt));
    }

    private static String clean(String s) {
        String out = FRAME_SIZE.matcher(s).replaceAll("").trim().toLowerCase(Locale.ROOT).replace('/', '_')
                .replace(' ', '_').replaceAll("[^a-z0-9_.-]", "");
        return out.replaceAll("_+", "_").replaceAll("^_|_$", "");
    }

    static String singular(String folder) {
        String f = folder.toLowerCase(Locale.ROOT);
        return f.length() > 2 && f.endsWith("s") && !f.endsWith("ss") ? f.substring(0, f.length() - 1) : f;
    }

    private static String topFolder(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? null : path.substring(0, slash);
    }

    private static String name(String file, String suffix) {
        return clean(file.substring(0, file.length() - suffix.length()));
    }

    static String extension(String file) {
        int dot = file.lastIndexOf('.');
        return dot < 0 ? "" : file.substring(dot + 1);
    }

    private static String withoutExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < path.lastIndexOf('/') + 1 ? path : path.substring(0, dot);
    }

    /** Every file under {@code dir} as a '/'-separated relative path, sorted; dot files and folders are left out. */
    static List<String> files(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> dir.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> !p.startsWith(".") && !p.contains("/."))
                    .sorted()
                    .toList();
        }
    }

    /** Images another asset loads itself: atlas pages, particle images, map and tileset images, skin atlases. */
    static Set<String> loadedByOthers(Path assetsDir, List<String> files) throws IOException {
        Set<String> out = new HashSet<>();
        for (String path : files) {
            String lower = path.toLowerCase(Locale.ROOT);
            String folder = path.contains("/") ? path.substring(0, path.lastIndexOf('/') + 1) : "";
            if (lower.endsWith(".atlas")) {
                for (String line : Files.readAllLines(assetsDir.resolve(path), StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (!t.contains(":") && IMAGES.contains(extension(t.toLowerCase(Locale.ROOT)))) {
                        out.add(normalise(folder + t));
                    }
                }
            } else if (lower.endsWith(".p")) {
                boolean images = false;
                for (String line : Files.readAllLines(assetsDir.resolve(path), StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (t.startsWith("- ")) {
                        images = t.startsWith("- Image Path");
                    } else if (images && !t.isEmpty()) {
                        String image = t.replace('\\', '/');
                        out.add(normalise(folder + image.substring(image.lastIndexOf('/') + 1)));
                    }
                }
            } else if (lower.endsWith(".fnt")) {   // a bitmap font loads its page images
                Matcher m = FNT_PAGE.matcher(Files.readString(assetsDir.resolve(path), StandardCharsets.UTF_8));
                while (m.find()) {
                    out.add(normalise(folder + m.group(1)));
                }
            } else if (lower.endsWith(".tmx") || lower.endsWith(".tsx")) {
                Matcher m = XML_IMAGE.matcher(Files.readString(assetsDir.resolve(path), StandardCharsets.UTF_8));
                while (m.find()) {
                    out.add(normalise(folder + m.group(1)));
                }
            }
        }
        return out;
    }

    /** Resolves {@code ..} and {@code .} in a '/'-separated path. */
    static String normalise(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.remove(parts.size() - 1);
                }
            } else if (!part.isEmpty() && !part.equals(".")) {
                parts.add(part);
            }
        }
        return String.join("/", parts);
    }
}
