package dev.ember2d.demo.platformer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.TiledTypes;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The committed Tiled project matches the demo's components and prefabs (F-009, I-245). */
class TiledProjectTest {

    @Test
    void theProjectFileIsUpToDate() throws Exception {
        File project = new File("assets/maps/platformer.tiled-project");
        String committed = Files.readString(project.toPath(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        ComponentRegistry registry = new ComponentRegistry();
        new DemoComponents().register(registry);
        List<String> warnings = new ArrayList<>();
        String fresh = TiledTypes.update(registry, TiledTypes.readPrefabs("assets"), committed, warnings);
        assertThat(fresh).as("run ./gradlew :demos:demo-platformer:core:tiledTypes and commit the result")
                .isEqualTo(committed);
        assertThat(warnings).isEmpty();
        assertThat(fresh).contains("\"name\": \"enemy/pig\"", "\"name\": \"Patroller\"");
    }
}
