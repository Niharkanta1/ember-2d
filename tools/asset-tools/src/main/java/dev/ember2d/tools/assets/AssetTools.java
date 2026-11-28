package dev.ember2d.tools.assets;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Command line for the asset tools (F-028): {@code manifest --assets= [--prune=]} and {@code clips --assets= --from= ...}. */
public final class AssetTools {

    private AssetTools() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: manifest --assets=<dir> [--prune=true] | clips --assets=<dir> --from=<path> "
                    + "[--name=] [--frame=WxH] [--duration=]");
            System.exit(1);
        }
        Map<String, String> o = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (args[i].startsWith("--") && eq > 2) {
                o.put(args[i].substring(2, eq), args[i].substring(eq + 1));
            }
        }
        Path assets = Path.of(o.getOrDefault("assets", ""));
        try {
            if (args[0].equals("manifest")) {
                ManifestTool.Result r = ManifestTool.update(assets, Boolean.parseBoolean(o.get("prune")));
                r.added().forEach(a -> System.out.println("added    " + a));
                r.removed().forEach(a -> System.out.println("removed  " + a));
                r.missing().forEach(a -> System.out.println("missing  " + a + "  (run with -Pprune=true to remove)"));
                System.out.println(r.written() ? "manifest.json updated" : "manifest.json is up to date");
            } else if (args[0].equals("clips")) {
                String from = o.getOrDefault("from", "");
                if (from.isBlank()) {
                    throw new IOException("Pass -Pfrom=<folder of images or Aseprite .json export in the assets>");
                }
                ClipsTool.Result r = from.toLowerCase().endsWith(".json")
                        ? ClipsTool.fromAseprite(assets, from, o.get("name"))
                        : ClipsTool.fromFolder(assets, from, o.get("name"), frame(o.get("frame")),
                                duration(o.get("duration")));
                System.out.println("wrote " + r.file() + " with clips " + r.clips());
                System.out.println("manifest.json updated; the clip set's ID is anim."
                        + r.file().substring("anim/".length(), r.file().length() - ".clips.json".length()));
            } else {
                throw new IOException("Unknown command " + args[0] + " (manifest or clips)");
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    static int[] frame(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] p = text.toLowerCase().split("x");
        if (p.length != 2) {
            throw new IllegalArgumentException("-Pframe must look like 32x32 (was " + text + ")");
        }
        try {
            return new int[] {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("-Pframe must look like 32x32 (was " + text + ")", e);
        }
    }

    static float duration(String text) {
        if (text == null || text.isBlank()) {
            return 0.1f;
        }
        float d = Float.parseFloat(text);
        if (!(d > 0f) || Float.isInfinite(d)) {
            throw new IllegalArgumentException("-Pduration must be a number of seconds > 0 (was " + text + ")");
        }
        return d;
    }
}
