package dev.ember2d.collision;

import dev.ember2d.core.PublicApi;

/**
 * Solid and one-way cells on a regular grid (F-006, F-009): pure data, built once (for example from a tile map) and
 * read by collision code. The grid's origin is world (0, 0); cell (0, 0) is the bottom-left cell and world y grows
 * upwards. Cells outside the grid are neither solid nor one-way.
 */
@PublicApi
public final class TileGrid {

    private final int width;
    private final int height;
    private final float cellWidth;
    private final float cellHeight;
    private final boolean[] solid;
    private final boolean[] oneWay;

    /**
     * @throws IllegalArgumentException if a size is negative or a cell size is not a finite number > 0
     */
    public TileGrid(int width, int height, float cellWidth, float cellHeight) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Grid size must be >= 0 (was " + width + "x" + height + ")");
        }
        if (!(cellWidth > 0f) || !(cellHeight > 0f) || Float.isInfinite(cellWidth) || Float.isInfinite(cellHeight)) {
            throw new IllegalArgumentException("Cell size must be > 0 (was " + cellWidth + "x" + cellHeight + ")");
        }
        this.width = width;
        this.height = height;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.solid = new boolean[width * height];
        this.oneWay = new boolean[width * height];
    }

    /** Width in cells. */
    public int width() {
        return width;
    }

    /** Height in cells. */
    public int height() {
        return height;
    }

    /** Cell width in world units. */
    public float cellWidth() {
        return cellWidth;
    }

    /** Cell height in world units. */
    public float cellHeight() {
        return cellHeight;
    }

    /** True if the cell blocks from every side; false outside the grid. */
    public boolean solid(int x, int y) {
        return inside(x, y) && solid[y * width + x];
    }

    /** True if the cell blocks only from above; false outside the grid. */
    public boolean oneWay(int x, int y) {
        return inside(x, y) && oneWay[y * width + x];
    }

    /** @throws IndexOutOfBoundsException outside the grid */
    public void setSolid(int x, int y, boolean value) {
        solid[index(x, y)] = value;
    }

    /** @throws IndexOutOfBoundsException outside the grid */
    public void setOneWay(int x, int y, boolean value) {
        oneWay[index(x, y)] = value;
    }

    /** The column containing a world x (may be outside the grid). */
    public int cellX(float worldX) {
        return (int) Math.floor(worldX / cellWidth);
    }

    /** The row containing a world y (may be outside the grid). */
    public int cellY(float worldY) {
        return (int) Math.floor(worldY / cellHeight);
    }

    /** Number of solid cells (for logs and tests). */
    public int solidCount() {
        int count = 0;
        for (boolean s : solid) {
            if (s) {
                count++;
            }
        }
        return count;
    }

    /** Number of one-way cells (for logs and tests). */
    public int oneWayCount() {
        int count = 0;
        for (boolean o : oneWay) {
            if (o) {
                count++;
            }
        }
        return count;
    }

    private boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private int index(int x, int y) {
        if (!inside(x, y)) {
            throw new IndexOutOfBoundsException("Cell " + x + "," + y + " is outside the " + width + "x" + height + " grid");
        }
        return y * width + x;
    }
}
