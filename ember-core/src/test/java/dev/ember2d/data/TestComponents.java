package dev.ember2d.data;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import dev.ember2d.ecs.EntityRef;

/** Components used by the data tests. */
final class TestComponents {

    private TestComponents() {
    }

    enum Mode { CALM, ANGRY }

    /** Every supported field type, plus unsupported ones. */
    public static final class Kitchen {
        public int i = 1;
        public long l;
        public float f;
        public double d;
        public boolean b;
        public String s = "x";
        public Mode mode = Mode.CALM;
        public Color color;
        public final Color tint = new Color(Color.WHITE);
        public final Vector2 v = new Vector2();
        public Rectangle r;
        public float[] path;
        public EntityRef ref;
        public final int fixed = 3;
        public Object anything;
        public static int counter;
    }

    public static final class Walker {
        public float speed;
        public EntityRef target;
        public float[] path;
        public final Vector2 home = new Vector2();
        public Rectangle zone;
    }

    public static final class Mover {
        public float speed;
    }

    public static final class Health {
        public int max = 1;
    }

    public static final class Coin {
        public int value;
    }

    /** The setup used by these tests (public no-arg constructor for KeepRules). */
    public static final class Setup implements ComponentSetup {
        @Override
        public void register(ComponentRegistry r) {
            r.register(Kitchen.class, Kitchen::new).register(Walker.class, Walker::new).register(Mover.class, Mover::new)
                    .register(Health.class, Health::new).register(Coin.class, Coin::new);
        }
    }
}
