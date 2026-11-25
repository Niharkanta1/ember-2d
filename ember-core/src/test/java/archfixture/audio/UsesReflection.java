package archfixture.audio;

/** Violates rule 10: reflection outside ember.data. */
public class UsesReflection {
    public Class<?> find() throws ClassNotFoundException {
        return Class.forName("java.lang.String");
    }
}
