package codequest.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import codequest.screens.BaseScreen;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of the Swing prototype's GameMap. All the grid/path math (waypoints,
 * pathCells, pixelToCell/cellToPixel) is unchanged top-down pixel arithmetic;
 * only image loading and the two draw methods (draw = textures, drawHoverCell
 * = shapes) move to LibGDX APIs. ScreenUtils.HEIGHT becomes the fixed
 * BaseScreen.VIRTUAL_HEIGHT since the whole game now lives in that 1920x1080
 * design space instead of the real screen resolution.
 */
public class GameMap {

    private Texture mapImage;
    private Texture shrineImage;
    private final int cellSize;
    private final int cols;
    private final int rows;
    private final int mapWidth;
    private final int mapHeight;
    private final int offsetY;
    private final boolean[][] pathCells;
    private final List<Point> waypoints;
    private final int stageNumber;

    public GameMap(int stageNumber, int mapWidth) {
        this.stageNumber = stageNumber;
        this.mapWidth = mapWidth;
        this.mapHeight = (int) (mapWidth * (941.0 / 1672.0));
        this.offsetY = ((int) BaseScreen.VIRTUAL_HEIGHT - mapHeight) / 2;
        this.cellSize = mapWidth / 20;
        this.cols = 20;
        this.rows = mapHeight / cellSize;
        this.pathCells = new boolean[cols][rows];
        this.waypoints = new ArrayList<>();

        loadMap();
        buildPath();
    }

    private void loadMap() {
        try {
            mapImage = new Texture(Gdx.files.internal("images/gameplay/maps/map" + stageNumber + ".png"));
        } catch (Exception e) {
            Gdx.app.error("GameMap", "Could not load map: " + stageNumber, e);
        }
        try {
            shrineImage = new Texture(Gdx.files.internal("images/gameplay/shrine.png"));
        } catch (Exception e) {
            Gdx.app.error("GameMap", "Could not load shrine image", e);
        }
    }

    private void buildPath() {
        switch (stageNumber) {
            case 1:
                buildMap1Path();
                break;
            case 2:
                buildMap2Path();
                break;
            case 3:
                buildMap3Path();
                break;
        }
    }

    private void buildMap1Path() {
        int[][] gridPath = {
                { 0, 8 }, { 1, 8 }, { 2, 8 }, { 3, 8 }, { 4, 8 }, { 5, 8 }, { 6, 8 }, { 7, 8 }, { 8, 8 },
                { 9, 8 }, { 10, 8 }, { 11, 8 }, { 11, 7 }, { 11, 6 }, { 10, 6 }, { 9, 6 }, { 8, 6 },
                { 8, 5 }, { 8, 4 }, { 8, 3 }, { 9, 3 }, { 10, 3 }, { 11, 3 }, { 12, 3 }, { 13, 3 },
                { 13, 2 }, { 13, 1 }, { 13, 0 }
        };

        for (int[] cell : gridPath) {
            int col = cell[0];
            int row = cell[1];
            if (col < cols && row < rows) {
                pathCells[col][row] = true;
            }
            int px = col * cellSize + cellSize / 2;
            int py = row * cellSize + cellSize / 2 + offsetY;
            waypoints.add(new Point(px, py));
        }

        int[][] blockedCells = {
                { 0, 9 }, { 1, 9 }, { 2, 9 }, { 3, 9 }, { 4, 9 }, { 5, 9 }, { 6, 9 }, { 7, 9 }, { 8, 9 },
                { 0, 10 }, { 1, 10 }, { 2, 10 }, { 3, 10 }, { 4, 10 }, { 5, 10 }, { 6, 10 }, { 7, 10 }, { 8, 10 },
                { 9, 10 }
        };
        for (int[] cell : blockedCells) {
            int col = cell[0];
            int row = cell[1];
            if (col < cols && row < rows) {
                pathCells[col][row] = true;
            }
        }
    }

    private void buildMap2Path() {
        int[][] gridPath = {
                { 0, 6 }, { 1, 6 }, { 2, 6 }, { 3, 6 }, { 4, 6 }, { 5, 6 }, { 6, 6 }, { 7, 6 }, { 7, 5 },
                { 7, 4 }, { 7, 3 }, { 7, 2 }, { 8, 2 }, { 9, 2 }, { 10, 2 }, { 11, 2 }, { 12, 2 },
                { 12, 3 }, { 12, 4 }, { 12, 5 }, { 12, 6 }, { 13, 6 }, { 14, 6 }, { 15, 6 }, { 16, 6 },
                { 16, 7 }, { 16, 8 }, { 16, 9 }
        };

        for (int[] cell : gridPath) {
            int col = cell[0];
            int row = cell[1];
            if (col < cols && row < rows) {
                pathCells[col][row] = true;
            }
            int px = col * cellSize + cellSize / 2;
            int py = row * cellSize + cellSize / 2 + offsetY;
            waypoints.add(new Point(px, py));
        }

        int[][] blockedCells = {
                { 0, 0 }, { 0, 1 }, { 0, 2 }, { 1, 0 }, { 1, 1 }, { 1, 2 }, { 2, 0 }, { 2, 1 }, { 2, 2 }, { 3, 0 },
                { 3, 1 }, { 3, 2 }, { 4, 0 }, { 4, 1 }, { 4, 2 }, { 19, 0 }, { 19, 1 }, { 19, 2 },
                { 18, 0 }, { 18, 1 }, { 18, 2 }, { 17, 0 }, { 17, 1 }, { 17, 2 }, { 16, 1 }, { 2, 8 }, { 3, 8 },
                { 4, 8 }, { 2, 9 }, { 3, 9 }, { 4, 9 },
                { 17, 8 }, { 18, 8 }, { 19, 8 },
                { 16, 9 }, { 17, 9 }, { 18, 9 }, { 19, 9 },
                { 12, 10 }, { 13, 10 }, { 14, 10 }, { 15, 10 }, { 16, 10 }, { 17, 10 }, { 18, 10 }, { 19, 10 },
        };
        for (int[] cell : blockedCells) {
            int col = cell[0];
            int row = cell[1];
            if (col < cols && row < rows) {
                pathCells[col][row] = true;
            }
        }
    }

    private void buildMap3Path() {
        int[][] gridPath = {
                { 0, 3 }, { 1, 3 }, { 2, 3 }, { 3, 3 }, { 4, 3 }, { 5, 3 }, { 6, 3 },
                { 6, 4 }, { 6, 5 }, { 6, 6 }, { 6, 7 }, { 6, 8 },
                { 7, 8 }, { 8, 8 }, { 9, 8 }, { 10, 8 },
                { 10, 7 }, { 10, 6 }, { 10, 5 }, { 10, 4 }, { 10, 3 }, { 10, 2 },
                { 11, 2 }, { 12, 2 }, { 13, 2 }, { 14, 2 },
                { 14, 3 }, { 14, 4 }, { 14, 5 }, { 14, 6 },
                { 15, 6 }, { 16, 6 }, { 17, 6 }, { 18, 6 }, { 19, 6 },
        };

        waypoints.clear();

        for (int[] cell : gridPath) {
            int c = cell[0];
            int r = cell[1];
            if (c >= 0 && c < cols && r >= 0 && r < rows) {
                pathCells[c][r] = true;
                waypoints.add(cellToPixel(c, r));
            }
        }
    }

    /** Textures only — call inside the screen's SpriteBatch begin/end block. */
    public void draw(Batch batch) {
        if (mapImage != null) {
            batch.draw(mapImage, 0, BaseScreen.VIRTUAL_HEIGHT - offsetY - mapHeight, mapWidth, mapHeight);
        }

        if (shrineImage != null && !waypoints.isEmpty()) {
            Point lastWaypoint = waypoints.get(waypoints.size() - 1);
            float sx = lastWaypoint.x - cellSize / 2f;
            float topSy = lastWaypoint.y - cellSize / 2f;
            float sy = BaseScreen.VIRTUAL_HEIGHT - topSy - cellSize;
            batch.draw(shrineImage, sx, sy, cellSize, cellSize);
        }
    }

    /** Shapes only — call inside the screen's ShapeRenderer begin/end block (Filled then Line pass). */
    public void drawHoverCell(ShapeRenderer sr, int col, int row, boolean canPlace, boolean filled) {
        if (filled) {
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    if (pathCells[c][r])
                        continue;
                    float x = c * cellSize;
                    float topY = r * cellSize + offsetY;
                    float y = BaseScreen.VIRTUAL_HEIGHT - topY - cellSize;
                    sr.setColor(new Color(1f, 1f, 1f, 8 / 255f));
                    sr.rect(x, y, cellSize, cellSize);
                }
            }

            float x = col * cellSize;
            float topY = row * cellSize + offsetY;
            float y = BaseScreen.VIRTUAL_HEIGHT - topY - cellSize;
            if (canPlace) {
                sr.setColor(new Color(0, 1f, 0, 60 / 255f));
            } else {
                sr.setColor(new Color(1f, 0, 0, 60 / 255f));
            }
            sr.rect(x, y, cellSize, cellSize);
        } else {
            float x = col * cellSize;
            float topY = row * cellSize + offsetY;
            float y = BaseScreen.VIRTUAL_HEIGHT - topY - cellSize;
            if (canPlace) {
                sr.setColor(new Color(0, 1f, 0, 150 / 255f));
            } else {
                sr.setColor(new Color(1f, 0, 0, 150 / 255f));
            }
            sr.rect(x, y, cellSize, cellSize);
        }
    }

    public Point pixelToCell(int px, int py) {
        int col = px / cellSize;
        int row = (py - offsetY) / cellSize;
        if (col >= 0 && col < cols && row >= 0 && row < rows) {
            return new Point(col, row);
        }
        return null;
    }

    public Point cellToPixel(int col, int row) {
        return new Point(
                col * cellSize + cellSize / 2,
                row * cellSize + cellSize / 2 + offsetY);
    }

    public boolean canPlaceTower(int col, int row, boolean[][] occupiedCells) {
        if (col < 0 || col >= cols || row < 0 || row >= rows)
            return false;
        if (pathCells[col][row])
            return false;
        if (occupiedCells[col][row])
            return false;
        return true;
    }

    public List<Point> getWaypoints() {
        return waypoints;
    }

    public int getCellSize() {
        return cellSize;
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    public int getMapWidth() {
        return mapWidth;
    }

    public int getMapHeight() {
        return mapHeight;
    }

    public boolean[][] getPathCells() {
        return pathCells;
    }

    public int getStageNumber() {
        return stageNumber;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public void dispose() {
        if (mapImage != null)
            mapImage.dispose();
        if (shrineImage != null)
            shrineImage.dispose();
    }
}
