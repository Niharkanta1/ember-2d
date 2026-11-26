package dev.ember2d.collision;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.debug.DebugOverlay;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.render.Renderer;
import java.util.Objects;

/**
 * Draws colliders through the debug overlay while it is visible (F-006, F3): solid tiles near the camera (grey),
 * one-way tiles and solids (orange top edge), solids (blue), actors (green), triggers (yellow) and each actor's last
 * contacts (red edge).
 */
@PublicApi
public final class CollisionDebugSystem extends EntitySystem {

    private static final Color TILE = new Color(0.6f, 0.6f, 0.6f, 1f);
    private static final Color ONE_WAY = new Color(1f, 0.6f, 0.1f, 1f);
    private static final Color SOLID = new Color(0.3f, 0.6f, 1f, 1f);
    private static final Color ACTOR = new Color(0.3f, 1f, 0.3f, 1f);
    private static final Color TRIGGER = new Color(1f, 0.9f, 0.2f, 1f);
    private static final Color CONTACT = new Color(1f, 0.2f, 0.2f, 1f);

    private final Collision collision;
    private final DebugOverlay debug;
    private final Renderer renderer;
    private final Rectangle box = new Rectangle();

    public CollisionDebugSystem(World world, Collision collision, DebugOverlay debug, Renderer renderer) {
        super(Phase.RENDER, world.family(Transform.class, Collider.class));
        this.collision = Objects.requireNonNull(collision, "collision");
        this.debug = Objects.requireNonNull(debug, "debug");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    protected void begin() {
        if (!debug.isVisible()) {
            return;
        }
        OrthographicCamera camera = renderer.worldCamera();
        float w = camera.viewportWidth * camera.zoom;
        float h = camera.viewportHeight * camera.zoom;
        float left = camera.position.x - w / 2;
        float bottom = camera.position.y - h / 2;
        for (TileGrid grid : collision.grids()) {
            int c0 = Math.max(0, grid.cellX(left));
            int c1 = Math.min(grid.width() - 1, grid.cellX(left + w));
            int r0 = Math.max(0, grid.cellY(bottom));
            int r1 = Math.min(grid.height() - 1, grid.cellY(bottom + h));
            float cw = grid.cellWidth();
            float ch = grid.cellHeight();
            for (int r = r0; r <= r1; r++) {
                for (int c = c0; c <= c1; c++) {
                    if (grid.solid(c, r)) {
                        debug.rect(c * cw, r * ch, cw, ch, TILE);
                    } else if (grid.oneWay(c, r)) {
                        debug.line(c * cw, (r + 1) * ch, (c + 1) * cw, (r + 1) * ch, ONE_WAY);
                    }
                }
            }
        }
    }

    @Override
    protected void process(int entity, float delta) {
        if (!debug.isVisible() || collision.bounds(entity, box) == null) {
            return;
        }
        World world = world();
        Collider c = world.get(entity, Collider.class);
        if (c.trigger) {
            debug.rect(box.x, box.y, box.width, box.height, TRIGGER);
        } else if (world.has(entity, Solid.class)) {
            debug.rect(box.x, box.y, box.width, box.height, SOLID);
            if (world.get(entity, Solid.class).oneWay) {
                debug.line(box.x, box.y + box.height, box.x + box.width, box.y + box.height, ONE_WAY);
            }
        } else if (world.has(entity, Actor.class)) {
            debug.rect(box.x, box.y, box.width, box.height, ACTOR);
            Actor a = world.get(entity, Actor.class);
            if (a.hitY) {
                float y = collision.isTouching(entity, Direction.DOWN) ? box.y : box.y + box.height;
                debug.line(box.x, y, box.x + box.width, y, CONTACT);
            }
            if (a.hitX) {
                float x = collision.isTouching(entity, Direction.LEFT) ? box.x : box.x + box.width;
                debug.line(x, box.y, x, box.y + box.height, CONTACT);
            }
        }
    }
}
