package archfixture.audio;

import com.badlogic.gdx.Gdx;

/** Violates rule 9: Gdx.files outside assets/save. */
public class ReadsGdxFiles {
    public Object read() {
        return Gdx.files.internal("a.txt");
    }
}
