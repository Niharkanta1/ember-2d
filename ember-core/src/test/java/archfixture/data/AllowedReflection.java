package archfixture.data;

import java.lang.reflect.Field;

/** Correct: ember.data may use reflection. */
public class AllowedReflection {
    public Field[] fields(Object o) {
        return o.getClass().getDeclaredFields();
    }
}
