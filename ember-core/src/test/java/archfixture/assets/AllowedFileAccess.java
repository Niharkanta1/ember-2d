package archfixture.assets;

import com.badlogic.gdx.Gdx;

/** Correct: ember.assets may use Gdx.files. */
public class AllowedFileAccess {
    public Object read() {
        return Gdx.files.internal("a.txt");
    }
}
