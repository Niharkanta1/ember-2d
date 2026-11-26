package dev.ember2d.assets.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.badlogic.gdx.graphics.Texture;
import dev.ember2d.assets.AssetLoadException;
import org.junit.jupiter.api.Test;

class AssetManifestTest {

    @Test
    void parsesEntriesGroupsAndOptionalFlag() {
        AssetManifest manifest = AssetManifest.parse("""
                { "version": 1, "assets": [
                  { "id": "tex.hero", "path": "gfx/hero.png", "type": "texture", "group": "global" },
                  { "id": "sfx.jump", "path": "sfx/jump.ogg", "type": "sound", "group": ["level1", "level2"], "optional": true }
                ] }
                """, "manifest.json");

        AssetManifest.Entry hero = manifest.find("tex.hero");
        assertThat(hero.path()).isEqualTo("gfx/hero.png");
        assertThat(hero.type().assetClass()).isEqualTo(Texture.class);
        assertThat(hero.optional()).isFalse();
        assertThat(manifest.find("sfx.jump").groups()).containsExactly("level1", "level2");
        assertThat(manifest.find("sfx.jump").optional()).isTrue();
        assertThat(manifest.groupNames()).containsExactly("global", "level1", "level2");
        assertThat(manifest.group("level2")).extracting(AssetManifest.Entry::id).containsExactly("sfx.jump");
        assertThat(manifest.group("nope")).isNull();
    }

    @Test
    void reportsEveryProblemWithEntryAndField() {
        AssetLoadException e = catchThrowableOfType(AssetLoadException.class, () -> AssetManifest.parse("""
                { "version": 2, "assets": [
                  { "id": "a", "path": "a.png", "type": "texture", "group": "global" },
                  { "id": "a", "path": "b.png", "type": "texture", "group": "global" },
                  { "path": "c.png", "type": "sprite", "group": "global" },
                  { "id": "d", "type": "sound", "group": [] , "optional": "yes" },
                  42
                ] }
                """, "manifest.json"));

        assertThat(e.getMessage()).startsWith("Invalid asset manifest manifest.json");
        assertThat(e.problems()).containsExactly(
                "\"version\" must be 1 (was 2)",
                "assets[1] (\"a\"): duplicate id \"a\"",
                "assets[2]: \"id\" is required",
                "assets[2]: unknown \"type\" \"sprite\"; valid types: [texture, atlas, font, sound, music, json, map, prefab, clips, asm]",
                "assets[3] (\"d\"): \"path\" is required",
                "assets[3] (\"d\"): \"group\" must be a name or a non-empty list of names",
                "assets[3] (\"d\"): \"optional\" must be true or false",
                "assets[4] must be an object");
    }

    @Test
    void invalidJsonAndMissingAssetsArrayAreReported() {
        assertThat(catchThrowableOfType(AssetLoadException.class, () -> AssetManifest.parse("{ nope", "m.json"))
                .problems()).singleElement().asString().startsWith("not valid JSON");
        assertThat(catchThrowableOfType(AssetLoadException.class, () -> AssetManifest.parse("{ \"version\": 1 }", "m.json"))
                .problems()).containsExactly("\"assets\" must be an array");
    }

    @Test
    void closestIdsByEditDistance() {
        AssetManifest manifest = AssetManifest.parse("""
                { "version": 1, "assets": [
                  { "id": "tex.hero", "path": "a", "type": "texture", "group": "g" },
                  { "id": "tex.herb", "path": "b", "type": "texture", "group": "g" },
                  { "id": "music.title", "path": "c", "type": "music", "group": "g" },
                  { "id": "tex.zero", "path": "d", "type": "texture", "group": "g" }
                ] }
                """, "m.json");

        assertThat(manifest.closestIds("tex.heor", 2)).containsExactly("tex.hero", "tex.herb");
        assertThat(AssetManifest.distance("kitten", "sitting")).isEqualTo(3);
        assertThat(AssetManifest.empty("x").closestIds("a", 3)).isEmpty();
    }
}
