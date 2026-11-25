package archfixture.render;

import com.badlogic.gdx.files.FileHandle;
import dev.ember2d.core.PublicApi;
import java.util.List;

/** Violates rule 11: a public API type exposes FileHandle (directly and as a generic argument). */
@PublicApi
public class ExposesFileHandle {
    public FileHandle file() {
        return null;
    }

    public void load(List<FileHandle> files) {
    }

    @SuppressWarnings("unused")
    private FileHandle hiddenIsFine() {
        return null;
    }
}
