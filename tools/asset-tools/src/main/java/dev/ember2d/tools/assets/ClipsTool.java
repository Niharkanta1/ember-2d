package dev.ember2d.tools.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Writes clip sets ({@code anim/<name>.clips.json}, F-007) from a folder of sprite strips or an Aseprite export
 * (F-028), after adding the source files to the manifest.
 */
public final class ClipsTool {

    private static final Pattern SIZE = Pattern.compile("\\((\\d+)\\s*x\\s*(\\d+)\\)");

    private ClipsTool() {
    }

    /** The written file (relative to the assets folder) and its clip names. */
    public record Result(String file, List<String> clips) {
    }

    /** One looping clip per image in {@code folder}; {@code frame} is {@code {w, h}} or null. */
    public static Result fromFolder(Path assetsDir, String folder, String name, int[] frame, float duration)
            throws IOException {
        String dir = ManifestTool.normalise(folder.replace('\\', '/'));
        Path folderPath = assetsDir.resolve(dir);
        if (!Files.isDirectory(folderPath)) {
            throw new IOException("Folder not found in the assets: " + dir);
        }
        List<Path> images;
        try (Stream<Path> list = Files.list(folderPath)) {
            images = list.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted()
                    .toList();
        }
        if (images.isEmpty()) {
            throw new IOException("No .png files in " + dir);
        }
        String setName = name == null || name.isBlank() ? clean(folderPath.getFileName().toString()) : clean(name);
        String out = "anim/" + setName + ".clips.json";
        refuseExisting(assetsDir, out);

        // Measure and check every image before writing anything.
        List<String> problems = new ArrayList<>();
        Map<String, Map<String, Object>> clips = new LinkedHashMap<>();
        Map<String, String> clipImage = new LinkedHashMap<>();
        for (Path image : images) {
            String file = image.getFileName().toString();
            BufferedImage img = ImageIO.read(image.toFile());
            if (img == null) {
                problems.add(file + ": not a readable PNG");
                continue;
            }
            int fw;
            int fh;
            Matcher m = SIZE.matcher(file);
            if (m.find()) {   // a size in the file name is the most specific
                fw = Integer.parseInt(m.group(1));
                fh = Integer.parseInt(m.group(2));
            } else if (frame != null) {
                fw = frame[0];
                fh = frame[1];
            } else {
                fw = img.getHeight();
                fh = img.getHeight();
            }
            if (fw <= 0 || fh <= 0 || img.getWidth() % fw != 0 || img.getHeight() % fh != 0) {
                problems.add(file + ": " + img.getWidth() + "x" + img.getHeight() + " is not a whole number of "
                        + fw + "x" + fh + " frames (pass -Pframe=WxH)");
                continue;
            }
            int frames = (img.getWidth() / fw) * (img.getHeight() / fh);
            String clip = clipName(file);
            if (clips.containsKey(clip)) {
                problems.add(file + ": another image also makes a clip called " + clip);
                continue;
            }
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("sheet", null);   // filled with the image's ID below
            c.put("frameWidth", fw);
            c.put("frameHeight", fh);
            c.put("frames", List.of(0, frames - 1));
            clips.put(clip, c);
            clipImage.put(clip, dir + "/" + file);
        }
        if (!problems.isEmpty()) {
            throw new IOException("Cannot make clips:\n  " + String.join("\n  ", problems));
        }

        ManifestTool.update(assetsDir, false);
        Map<String, String> ids = idsByPath(assetsDir);
        for (Map.Entry<String, Map<String, Object>> e : clips.entrySet()) {
            String path = clipImage.get(e.getKey());
            String id = ids.get(path);
            if (id == null) {
                throw new IOException(path + " is loaded by another asset (an atlas, map or effect), so it has no "
                        + "texture ID of its own; move it or use the atlas instead");
            }
            e.getValue().put("sheet", id);
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("frameDuration", duration);
        write(assetsDir, out, defaults, clips);
        return new Result(out, new ArrayList<>(clips.keySet()));
    }

    /** One clip per frame tag of an Aseprite JSON export; the engine reads timing and direction from the export. */
    public static Result fromAseprite(Path assetsDir, String exportPath, String name) throws IOException {
        String export = ManifestTool.normalise(exportPath.replace('\\', '/'));
        Path file = assetsDir.resolve(export);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Aseprite export not found in the assets: " + export);
        }
        JsonValue root;
        try {
            root = new JsonReader().parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IOException(export + " is not valid JSON: " + e.getMessage(), e);
        }
        JsonValue meta = root.get("meta");
        JsonValue tags = meta == null ? null : meta.get("frameTags");
        if (root.get("frames") == null || tags == null || tags.size == 0) {
            throw new IOException(export + " has no frame tags; export with --list-tags (Aseprite: File > Export "
                    + "Sprite Sheet > Output > JSON Data, Meta: Tags)");
        }
        String image = meta.getString("image", null);
        if (image == null) {
            throw new IOException(export + ": meta.image is missing (export the sheet image too)");
        }
        String folder = export.contains("/") ? export.substring(0, export.lastIndexOf('/') + 1) : "";
        String imagePath = ManifestTool.normalise(folder + image.replace('\\', '/'));
        if (!Files.isRegularFile(assetsDir.resolve(imagePath))) {
            throw new IOException("The export's image " + imagePath + " is not in the assets");
        }
        String fileName = file.getFileName().toString();
        String base = fileName.substring(0, fileName.lastIndexOf('.'));
        String setName = name == null || name.isBlank() ? clean(base) : clean(name);
        String out = "anim/" + setName + ".clips.json";
        refuseExisting(assetsDir, out);

        ManifestTool.update(assetsDir, false);
        Map<String, String> ids = idsByPath(assetsDir);
        String exportId = ids.get(export);
        String textureId = ids.get(imagePath);
        if (exportId == null || textureId == null) {
            throw new IOException("The export or its image has no manifest entry of its own (it is loaded by "
                    + "another asset)");
        }
        Map<String, Map<String, Object>> clips = new LinkedHashMap<>();
        for (JsonValue t = tags.child; t != null; t = t.next) {
            String tag = t.getString("name", "");
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("aseprite", exportId);
            c.put("texture", textureId);
            c.put("tag", tag);
            clips.put(clean(tag), c);
        }
        write(assetsDir, out, null, clips);
        return new Result(out, new ArrayList<>(clips.keySet()));
    }

    static String clipName(String file) {
        String base = file.substring(0, file.lastIndexOf('.'));
        base = SIZE.matcher(base).replaceAll("");
        return clean(base);
    }

    static String clean(String s) {
        String out = s.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s\\-]+", "_").replaceAll("[^a-z0-9_.]", "");
        return out.replaceAll("_+", "_").replaceAll("^_|_$", "");
    }

    private static void refuseExisting(Path assetsDir, String out) throws IOException {
        if (Files.exists(assetsDir.resolve(out))) {
            throw new IOException(out + " already exists; delete it or pass another -Pname (hand edits are never "
                    + "overwritten)");
        }
    }

    private static Map<String, String> idsByPath(Path assetsDir) throws IOException {
        JsonValue root = new JsonReader().parse(Files.readString(assetsDir.resolve(ManifestTool.MANIFEST),
                StandardCharsets.UTF_8));
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonValue e = root.get("assets").child; e != null; e = e.next) {
            out.put(e.getString("path", ""), e.getString("id", ""));
        }
        return out;
    }

    private static void write(Path assetsDir, String out, Map<String, Object> defaults,
            Map<String, Map<String, Object>> clips) throws IOException {
        StringBuilder s = new StringBuilder("{\n  \"version\": 1,\n");
        if (defaults != null) {
            s.append("  \"defaults\": ").append(Json.write(defaults)).append(",\n");
        }
        s.append("  \"clips\": {\n");
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : clips.entrySet()) {
            lines.add("    " + Json.quote(e.getKey()) + ": " + Json.write(e.getValue()));
        }
        s.append(String.join(",\n", lines)).append("\n  }\n}\n");
        Path file = assetsDir.resolve(out);
        Files.createDirectories(file.getParent());
        Files.writeString(file, s.toString(), StandardCharsets.UTF_8);
        ManifestTool.update(assetsDir, false);   // adds the clip set itself
    }
}
