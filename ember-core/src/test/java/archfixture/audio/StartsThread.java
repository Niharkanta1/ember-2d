package archfixture.audio;

/** Violates the threading rule. */
public class StartsThread {
    public void start() {
        new Thread(() -> { }).start();
    }
}
