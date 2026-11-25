package archfixture.audio;

/** Violates rule 9: System.exit. */
public class CallsSystemExit {
    public void quit() {
        System.exit(0);
    }
}
