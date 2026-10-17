package codequest.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import codequest.screens.Art;
import codequest.screens.BaseScreen;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * A stage's campus map: the background picture plus the path the avatar walks
 * (one waypoint per grid cell, in top-left pixel coordinates). The path is laid
 * out on a 20-column grid that the map picture has to match.
 */
public class GameMap {

    private static final int COLS = 20;

    private static final int[][][] CORNERS = {
            { { 0, 8 }, { 11, 8 }, { 11, 6 }, { 8, 6 }, { 8, 3 }, { 13, 3 }, { 13, 0 } },
            { { 0, 6 }, { 7, 6 }, { 7, 2 }, { 12, 2 }, { 12, 6 }, { 16, 6 }, { 16, 9 } },
            { { 0, 3 }, { 6, 3 }, { 6, 8 }, { 10, 8 }, { 10, 2 }, { 14, 2 }, { 14, 6 }, { 19, 6 } } };

    private Texture mapImage;
    private Texture labImage;
    private final int cellSize;
    private final int mapWidth;
    private final int mapHeight;
    private final int offsetY;
    private final List<Point> waypoints = new ArrayList<>();
    private final int stageNumber;

    public GameMap(int stageNumber, int mapWidth) {
        this.stageNumber = stageNumber;
        this.mapWidth = mapWidth;
        this.mapHeight = (int) (mapWidth * (941.0 / 1672.0));
        this.offsetY = ((int) BaseScreen.VIRTUAL_HEIGHT - mapHeight) / 2;
        this.cellSize = mapWidth / COLS;

        mapImage = load(Art.map(stageNumber));
        labImage = load(Art.LAB);
        buildPath();
    }

    private Texture load(String path) {
        try {
            return new Texture(Gdx.files.internal(path));
        } catch (Exception e) {
            Gdx.app.error("GameMap", "Could not load " + path, e);
            return null;
        }
    }

    private void buildPath() {
        int[][] corners = CORNERS[(stageNumber - 1) % CORNERS.length];
        waypoints.add(cellToPixel(corners[0][0], corners[0][1]));
        for (int i = 1; i < corners.length; i++) {
            int c = corners[i - 1][0];
            int r = corners[i - 1][1];
            int dc = Integer.signum(corners[i][0] - c);
            int dr = Integer.signum(corners[i][1] - r);
            while (c != corners[i][0] || r != corners[i][1]) {
                c += dc;
                r += dr;
                waypoints.add(cellToPixel(c, r));
            }
        }
    }

    /** Textures only — call inside the screen's SpriteBatch begin/end block. */
    public void draw(Batch batch) {
        if (mapImage != null) {
            batch.draw(mapImage, 0, BaseScreen.VIRTUAL_HEIGHT - offsetY - mapHeight, mapWidth, mapHeight);
        }
        if (labImage != null && !waypoints.isEmpty()) {
            Point last = waypoints.get(waypoints.size() - 1);
            float size = cellSize * 1.6f;
            float x = Math.min(last.x - size / 2f, mapWidth - size);
            float y = BaseScreen.VIRTUAL_HEIGHT - last.y - size / 2f;
            batch.draw(labImage, x, y, size, size);
        }
    }

    public Point cellToPixel(int col, int row) {
        return new Point(col * cellSize + cellSize / 2, row * cellSize + cellSize / 2 + offsetY);
    }

    public List<Point> getWaypoints() {
        return waypoints;
    }

    public int getCellSize() {
        return cellSize;
    }

    public int getMapWidth() {
        return mapWidth;
    }

    public int getMapHeight() {
        return mapHeight;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public void dispose() {
        if (mapImage != null) {
            mapImage.dispose();
        }
        if (labImage != null) {
            labImage.dispose();
        }
    }
}
