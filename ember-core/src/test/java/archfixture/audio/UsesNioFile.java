package archfixture.audio;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Violates rule 9: java.nio.file is not Android-safe. */
public class UsesNioFile {
    public Path path() {
        return Paths.get("x");
    }
}
