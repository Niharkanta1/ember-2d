import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import com.badlogic.gdx.utils.Array;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import javax.imageio.ImageIO;

/**
 * Writes the shooter demo's particle effects (I-510) into demos/demo-shooter/core/assets/fx: a hit burst, a dust puff
 * and the soft dot they use. The effects are built with libGDX's emitter API and saved in the particle editor's
 * format by libGDX itself, so they open in the editor too. Run from the repository root with the libGDX jar:
 *
 *     java -cp path/to/gdx-1.14.2.jar assets-src/demo-shooter/fx/MakeEffects.java
 */
public final class MakeEffects {

    public static void main(String[] args) throws IOException {
        File out = new File("demos/demo-shooter/core/assets/fx");
        out.mkdirs();
        dot(new File(out, "dot.png"));
        save(hit(), new File(out, "hit.p"));
        save(dust(), new File(out, "dust.p"));
    }

    /** An 8 x 8 soft white dot. */
    static void dot(File file) throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                double d = Math.hypot(x - 3.5, y - 3.5) / 4.0;
                int alpha = (int) Math.round(255 * Math.max(0, Math.min(1, 1.3 - d * 1.3)));
                image.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        ImageIO.write(image, "png", file);
        System.out.println("wrote " + file);
    }

    static ParticleEmitter base(String name) {
        ParticleEmitter e = new ParticleEmitter();
        e.setName(name);
        e.setImagePaths(Array.with("dot.png"));
        e.setContinuous(false);
        e.getAngle().setActive(true);
        e.getVelocity().setActive(true);
        e.getTransparency().setHigh(1f);   // the editor's default; a bare emitter starts at 0 (invisible)
        e.getTransparency().setScaling(new float[] {1f, 0f});
        e.getTransparency().setTimeline(new float[] {0f, 1f});
        return e;
    }

    /** Orange sparks flying out from a bullet hit. */
    static ParticleEffect hit() {
        ParticleEmitter e = base("sparks");
        e.setMinParticleCount(0);
        e.setMaxParticleCount(12);
        e.getDuration().setLow(60f);
        e.getEmission().setHigh(150f);
        e.getLife().setHigh(180f, 300f);
        e.getXScale().setHigh(5f, 8f);
        e.getXScale().setScaling(new float[] {1f, 0.3f});
        e.getXScale().setTimeline(new float[] {0f, 1f});
        e.getVelocity().setHigh(40f, 90f);
        e.getAngle().setHigh(0f, 360f);
        e.getTint().setColors(new float[] {1f, 0.75f, 0.25f, 1f, 0.3f, 0.1f});
        e.getTint().setTimeline(new float[] {0f, 1f});
        e.setAdditive(true);
        return effect(e);
    }

    /** A small grey puff kicked up by running feet. */
    static ParticleEffect dust() {
        ParticleEmitter e = base("puff");
        e.setMinParticleCount(0);
        e.setMaxParticleCount(4);
        e.getDuration().setLow(50f);
        e.getEmission().setHigh(60f);
        e.getLife().setHigh(300f, 450f);
        e.getXScale().setHigh(5f, 8f);
        e.getXScale().setScaling(new float[] {0.6f, 1f});
        e.getXScale().setTimeline(new float[] {0f, 1f});
        e.getVelocity().setHigh(6f, 14f);
        e.getAngle().setHigh(60f, 120f);
        e.getTint().setColors(new float[] {0.75f, 0.72f, 0.7f});
        e.getTint().setTimeline(new float[] {0f});
        e.setAdditive(false);
        return effect(e);
    }

    static ParticleEffect effect(ParticleEmitter emitter) {
        ParticleEffect effect = new ParticleEffect();
        effect.getEmitters().add(emitter);
        return effect;
    }

    static void save(ParticleEffect effect, File file) throws IOException {
        try (Writer w = new FileWriter(file)) {
            effect.save(w);
        }
        System.out.println("wrote " + file);
    }
}
