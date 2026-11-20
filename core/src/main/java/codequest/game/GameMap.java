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

    private static final int COLS = MapPaths.COLS;

    private Texture mapImage;
    private final int cellSize;
    private final int mapWidth;
    private final int mapHeight;
    private final int offsetY;
    private final List<Point> waypoints = new ArrayList<>();
    private final int stageNumber;
    private List<TowerSlots.Slot> towerSlots = List.of();

    public GameMap(int stageNumber, int mapWidth) {
        this(stageNumber, mapWidth, MapTheme.CAMPUS);
    }

    /** The map in a theme: the campus picture, or a school room built from the School Assets on the same path. */
    public GameMap(int stageNumber, int mapWidth, MapTheme theme) {
        this.stageNumber = stageNumber;
        this.mapWidth = mapWidth;
        this.mapHeight = (int) (mapWidth * (902.0 / 1640.0)); // the map pictures are 1640 x 902 (20 x 11 cells)
        this.offsetY = ((int) BaseScreen.VIRTUAL_HEIGHT - mapHeight) / 2;
        this.cellSize = mapWidth / COLS;

        if (theme.isSchool()) {
            mapImage = room(theme, stageNumber);
        } else {
            mapImage = load(Art.map(stageNumber)); // the picture already shows the campus gate and the building at the path's end
        }
        buildPath();
        loadSlots();
    }

    /** The tower slots reserved in the map picture (map<N>_slots.txt), if the file exists. */
    private void loadSlots() {
        try {
            com.badlogic.gdx.files.FileHandle file = Gdx.files.internal(Art.slots(stageNumber));
            if (file.exists()) {
                towerSlots = TowerSlots.parse(file.readString(), cellSize, offsetY);
            }
        } catch (RuntimeException e) {
            Gdx.app.error("GameMap", "Could not read the tower slots", e);
        }
    }

    public List<TowerSlots.Slot> getTowerSlots() {
        return towerSlots;
    }

    private Texture load(String path) {
        try {
            return codequest.screens.Assets.texture(path); // shared: shown smaller than its size, so Linear
        } catch (Exception e) {
            Gdx.app.error("GameMap", "Could not load " + path, e);
            return null;
        }
    }

    private Texture room(MapTheme theme, int layout) {
        try {
            return SchoolRoomPainter.texture(theme, layout); // shared, like the campus pictures
        } catch (Exception e) {
            Gdx.app.error("GameMap", "Could not build the " + theme.key() + " room, using the campus map", e);
            return load(Art.map(layout));
        }
    }

    private void buildPath() {
        for (int[] cell : MapPaths.cells(stageNumber)) {
            waypoints.add(cellToPixel(cell[0], cell[1]));
        }
    }

    /** Textures only — call inside the screen's SpriteBatch begin/end block. */
    public void draw(Batch batch) {
        if (mapImage != null) {
            batch.draw(mapImage, 0, BaseScreen.VIRTUAL_HEIGHT - offsetY - mapHeight, mapWidth, mapHeight);
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
        // the map picture belongs to a shared cache (Assets or SchoolRoomPainter) and stays loaded
    }
}
