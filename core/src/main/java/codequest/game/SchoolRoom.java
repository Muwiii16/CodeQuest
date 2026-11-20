package codequest.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * A school room built from the School Assets tiles (assets/game/school/16): a floor, the lesson's path in a different
 * floor, a wall along the top with a door where the path leaves, windows and a board, and furniture placed so that it is
 * never on the path or where a tower stands. Pure layout: it only lists which part of which sheet goes where (in art
 * pixels, 32 per map cell); {@link SchoolRoomPainter} turns that into a picture. The same theme and layout always give
 * the same room.
 */
public final class SchoolRoom {

    /** Art pixels per map cell (two 16 px tiles). */
    public static final int CELL = 32;
    public static final int WIDTH = MapPaths.COLS * CELL;
    public static final int HEIGHT = MapPaths.ROWS * CELL;
    /** Height of the wall face along the top (it covers row 0 and half of row 1). */
    public static final int FACE = 48;
    /** Thickness of the wall seen from above on the left, right and bottom. */
    public static final int RIM = 16;

    /** The sheets in assets/game/school/16. */
    public enum Sheet {
        FLOORS, WALL, DOORS, WINDOWS, OBJECTS;

        public String file() {
            return name().toLowerCase();
        }
    }

    /** A part of a sheet. */
    public record R(int x, int y, int w, int h) {
    }

    /**
     * One thing to draw at (dx, dy) in art pixels: part {@code src} of {@code sheet}, or, when sheet is null, a plain
     * rectangle of {@code src}'s size in {@code color} (RGBA8888).
     */
    public record Piece(Sheet sheet, R src, int dx, int dy, int color) {
        public int bottom() {
            return dy + src.h();
        }
    }

    // parts of objects.png (16 px sheet)
    static final R LOCKER = new R(3, 2, 26, 42);
    static final R SHELF_BOOKS = new R(83, 2, 26, 42);
    static final R SHELF_WIDE = new R(115, 2, 42, 42);
    static final R CHAIR_BLUE = new R(178, 7, 13, 22);
    static final R CHAIR_WOOD = new R(178, 40, 12, 20);
    static final R DESK_GRAY = new R(4, 81, 26, 31);
    static final R TABLE_WOOD = new R(82, 55, 42, 25);
    static final R CHALKBOARD = new R(16, 128, 48, 32);
    static final R BULLETIN = new R(16, 176, 48, 32);
    static final R CLOCK = new R(80, 128, 16, 16);
    static final R BOOKS = new R(144, 130, 16, 13);
    static final R TRASH = new R(208, 134, 16, 20);
    static final R PLANT = new R(82, 182, 10, 18);

    private enum Furniture {
        DESKS, TABLES, NONE
    }

    private record Style(int wallX, R floor, R path, int edge, R door, R window, R board, Furniture furniture,
            List<R> wallItems, List<R> extras) {
    }

    private static Style style(MapTheme theme) {
        return switch (theme) {
            case LIBRARY -> new Style(96, new R(48, 48, 16, 16), new R(112, 48, 16, 16), 0x7a4a28ff, new R(82, 20, 25, 44),
                    new R(64, 51, 16, 28), null, Furniture.TABLES, List.of(SHELF_WIDE, SHELF_BOOKS, SHELF_WIDE, SHELF_BOOKS),
                    List.of(PLANT, PLANT, TRASH));
            case HALLWAY -> new Style(176, new R(48, 16, 16, 16), new R(16, 80, 16, 16), 0x6e6040ff, new R(50, 20, 25, 44),
                    new R(96, 25, 16, 18), BULLETIN, Furniture.NONE, List.of(LOCKER, LOCKER, LOCKER, LOCKER),
                    List.of(PLANT, TRASH, PLANT, TRASH, PLANT));
            default -> new Style(16, new R(48, 80, 16, 16), new R(80, 16, 16, 16), 0x6c7480ff, new R(50, 116, 25, 44),
                    new R(16, 19, 32, 28), CHALKBOARD, Furniture.DESKS, List.of(SHELF_BOOKS, LOCKER, SHELF_BOOKS, LOCKER),
                    List.of(PLANT, TRASH));
        };
    }

    private final List<Piece> base = new ArrayList<>();
    private final List<Piece> walls = new ArrayList<>();
    private final List<Piece> standing = new ArrayList<>();
    /** Things lying on top of furniture (books on a table): drawn after everything that stands. */
    private final List<Piece> onTop = new ArrayList<>();
    private final boolean[][] blocked = new boolean[MapPaths.COLS][MapPaths.ROWS];
    private final boolean[][] path = new boolean[MapPaths.COLS][MapPaths.ROWS];
    private final boolean[] faceUsed = new boolean[WIDTH];

    private SchoolRoom() {
    }

    /**
     * The room for a theme and map layout. {@code towerSpots} are the tower slots in cell units ({x, y} of each tower's
     * spot, as in map&lt;N&gt;_slots.txt): nothing is placed where a tower will stand.
     */
    public static List<Piece> build(MapTheme theme, int layout, List<float[]> towerSpots) {
        SchoolRoom room = new SchoolRoom();
        room.make(style(theme), layout, towerSpots, new Random(layout * 1009L + theme.ordinal()));
        List<Piece> out = new ArrayList<>(room.base);
        out.addAll(room.walls);
        room.standing.sort(Comparator.comparingInt(Piece::bottom)); // lower on screen is in front
        out.addAll(room.standing);
        out.addAll(room.onTop);
        return out;
    }

    /** The cells a tower at (x, y) (cell units) covers: the tower picture is about 1.1 cells wide and 1.7 tall. */
    static List<int[]> towerCells(float x, float y) {
        List<int[]> out = new ArrayList<>();
        for (int c = (int) Math.floor(x - 0.6f); c <= (int) Math.floor(x + 0.6f); c++) {
            for (int r = (int) Math.floor(y - 0.9f); r <= (int) Math.floor(y + 0.9f); r++) {
                if (c >= 0 && r >= 0 && c < MapPaths.COLS && r < MapPaths.ROWS) {
                    out.add(new int[] { c, r });
                }
            }
        }
        return out;
    }

    private void make(Style s, int layout, List<float[]> towerSpots, Random rng) {
        List<int[]> cells = new ArrayList<>(MapPaths.cells(layout));
        // carry the path on to the edge of the room in its last direction, so it visibly leaves through a door or gap
        int[] last = cells.get(cells.size() - 1);
        int[] before = cells.get(cells.size() - 2);
        int dc = last[0] - before[0];
        int dr = last[1] - before[1];
        int c = last[0];
        int r = last[1];
        while (c + dc >= 0 && c + dc < MapPaths.COLS && r + dr >= 0 && r + dr < MapPaths.ROWS) {
            c += dc;
            r += dr;
            cells.add(new int[] { c, r });
        }
        for (int[] cell : cells) {
            path[cell[0]][cell[1]] = true;
            blocked[cell[0]][cell[1]] = true;
        }
        for (float[] spot : towerSpots) {
            for (int[] cell : towerCells(spot[0], spot[1])) {
                blocked[cell[0]][cell[1]] = true;
            }
        }
        for (int col = 0; col < MapPaths.COLS; col++) {
            blocked[col][0] = true; // the wall face
        }

        floor(s);
        int[] start = cells.get(0);
        int[] end = cells.get(cells.size() - 1);
        walls(s, start, end);
        // the door where the path goes out through the top wall
        if (end[1] == 0) {
            int x = end[0] * CELL + (CELL - s.door().w()) / 2;
            face(new Piece(Sheet.DOORS, s.door(), x, FACE - s.door().h(), 0), x, s.door().w());
        }
        if (s.board() != null) {
            board(s.board());
        }
        wallItems(s, rng);
        windows(s);
        clock();
        switch (s.furniture()) {
            case DESKS -> desks();
            case TABLES -> tables();
            default -> {
            }
        }
        extras(s, rng);
    }

    private void floor(Style s) {
        for (int col = 0; col < MapPaths.COLS; col++) {
            for (int row = 0; row < MapPaths.ROWS; row++) {
                R tile = path[col][row] ? s.path() : s.floor();
                for (int ty = 0; ty < CELL; ty += 16) {
                    for (int tx = 0; tx < CELL; tx += 16) {
                        base.add(new Piece(Sheet.FLOORS, tile, col * CELL + tx, row * CELL + ty, 0));
                    }
                }
            }
        }
        // a thin darker line where the path meets the floor
        for (int col = 0; col < MapPaths.COLS; col++) {
            for (int row = 0; row < MapPaths.ROWS; row++) {
                if (!path[col][row]) {
                    continue;
                }
                int x = col * CELL;
                int y = row * CELL;
                if (col > 0 && !path[col - 1][row]) {
                    base.add(fill(x, y, 1, CELL, s.edge()));
                }
                if (col < MapPaths.COLS - 1 && !path[col + 1][row]) {
                    base.add(fill(x + CELL - 1, y, 1, CELL, s.edge()));
                }
                if (row > 0 && !path[col][row - 1]) {
                    base.add(fill(x, y, CELL, 1, s.edge()));
                }
                if (row < MapPaths.ROWS - 1 && !path[col][row + 1]) {
                    base.add(fill(x, y + CELL - 1, CELL, 1, s.edge()));
                }
            }
        }
    }

    private static Piece fill(int x, int y, int w, int h, int color) {
        return new Piece(null, new R(0, 0, w, h), x, y, color);
    }

    /** The wall face along the top and the wall rim around the other sides, open where the path comes in and goes out. */
    private void walls(Style s, int[] start, int[] end) {
        int wx = s.wallX();
        R faceStrip = new R(wx, 16, 16, FACE);
        for (int x = 0; x < WIDTH; x += 16) {
            walls.add(new Piece(Sheet.WALL, faceStrip, x, 0, 0));
        }
        R left = new R(wx, 160, 16, 16);
        R right = new R(wx + 32, 160, 16, 16);
        R bottom = new R(wx + 16, 176, 16, 16);
        int bottomY = HEIGHT - RIM;
        for (int y = FACE; y < bottomY; y += 16) {
            int row = y / CELL;
            if (!(start[0] == 0 && start[1] == row) && !(end[0] == 0 && end[1] == row)) {
                walls.add(new Piece(Sheet.WALL, left, 0, y, 0));
            }
            if (!(start[0] == MapPaths.COLS - 1 && start[1] == row) && !(end[0] == MapPaths.COLS - 1 && end[1] == row)) {
                walls.add(new Piece(Sheet.WALL, right, WIDTH - RIM, y, 0));
            }
        }
        for (int x = RIM; x < WIDTH - RIM; x += 16) {
            int col = x / CELL;
            boolean open = (end[1] == MapPaths.ROWS - 1 && end[0] == col) || (start[1] == MapPaths.ROWS - 1 && start[0] == col);
            if (!open) {
                walls.add(new Piece(Sheet.WALL, bottom, x, bottomY, 0));
            }
        }
        walls.add(new Piece(Sheet.WALL, new R(wx, 176, 16, 16), 0, bottomY, 0));
        walls.add(new Piece(Sheet.WALL, new R(wx + 32, 176, 16, 16), WIDTH - RIM, bottomY, 0));
    }

    /** Something hung on the wall face, if its span (with a small gap) is still free. */
    private boolean face(Piece p, int x, int w) {
        int from = Math.max(0, x - 4);
        int to = Math.min(WIDTH, x + w + 4);
        for (int i = from; i < to; i++) {
            if (faceUsed[i]) {
                return false;
            }
        }
        for (int i = from; i < to; i++) {
            faceUsed[i] = true;
        }
        if (p != null) {
            walls.add(p);
        }
        return true;
    }

    private void board(R board) {
        int[] centres = { WIDTH / 2, WIDTH / 4, WIDTH * 3 / 4, WIDTH / 2 - 96, WIDTH / 2 + 96 };
        for (int centre : centres) {
            int x = centre - board.w() / 2;
            if (face(new Piece(Sheet.OBJECTS, board, x, (FACE - board.h()) / 2, 0), x, board.w())) {
                return;
            }
        }
    }

    /** Shelves or lockers standing against the wall, in row 1. */
    private void wallItems(Style s, Random rng) {
        List<Integer> cols = new ArrayList<>();
        for (int col = 1; col < MapPaths.COLS - 1; col++) {
            cols.add(col);
        }
        Collections.shuffle(cols, rng);
        int placed = 0;
        int want = s.wallItems().size() + 1;
        for (int col : cols) {
            if (placed >= want) {
                break;
            }
            R item = s.wallItems().get(placed % s.wallItems().size());
            int span = (item.w() + CELL - 1) / CELL;
            if (col + span > MapPaths.COLS - 1 || !free(col, 1, span, 1)) {
                continue;
            }
            int x = col * CELL + (span * CELL - item.w()) / 2;
            if (!face(null, x, item.w())) {
                continue;
            }
            standing.add(new Piece(Sheet.OBJECTS, item, x, 2 * CELL - 2 - item.h(), 0));
            take(col, 1, span, 1);
            placed++;
        }
    }

    private void windows(Style s) {
        R w = s.window();
        for (int col = 1; col < MapPaths.COLS - 1; col += 2) {
            int x = col * CELL + (CELL - w.w()) / 2;
            face(new Piece(Sheet.WINDOWS, w, x, (FACE - w.h()) / 2, 0), x, w.w());
        }
    }

    private void clock() {
        for (int col = MapPaths.COLS / 2; col < MapPaths.COLS - 1; col++) {
            int x = col * CELL + (CELL - CLOCK.w()) / 2;
            if (face(new Piece(Sheet.OBJECTS, CLOCK, x, 8, 0), x, CLOCK.w())) {
                return;
            }
        }
    }

    /** Rows of desks with a chair behind each, facing the board. */
    private void desks() {
        for (int row : new int[] { 2, 5, 8 }) {
            for (int col = 2; col < MapPaths.COLS - 2; col += 2) {
                if (!free(col, row, 1, 2)) {
                    continue;
                }
                int x = col * CELL;
                standing.add(new Piece(Sheet.OBJECTS, DESK_GRAY, x + (CELL - DESK_GRAY.w()) / 2, row * CELL + CELL - DESK_GRAY.h(), 0));
                standing.add(new Piece(Sheet.OBJECTS, CHAIR_BLUE, x + (CELL - CHAIR_BLUE.w()) / 2, (row + 1) * CELL + 6, 0));
                take(col, row, 1, 2);
            }
        }
    }

    /** Reading tables two cells wide, with books on top and two chairs. */
    private void tables() {
        for (int row : new int[] { 3, 6 }) {
            for (int col = 2; col + 1 < MapPaths.COLS - 1; col += 3) {
                if (!free(col, row, 2, 2)) {
                    continue;
                }
                int x = col * CELL + (2 * CELL - TABLE_WOOD.w()) / 2;
                int y = row * CELL + CELL - TABLE_WOOD.h();
                standing.add(new Piece(Sheet.OBJECTS, TABLE_WOOD, x, y, 0));
                // the books lie on the table top, so they sort with it rather than in front of the chairs
                onTop.add(new Piece(Sheet.OBJECTS, BOOKS, x + (TABLE_WOOD.w() - BOOKS.w()) / 2, y + 2, 0));
                for (int k = 0; k < 2; k++) {
                    standing.add(new Piece(Sheet.OBJECTS, CHAIR_WOOD, (col + k) * CELL + (CELL - CHAIR_WOOD.w()) / 2,
                            (row + 1) * CELL + 6, 0));
                }
                take(col, row, 2, 2);
            }
        }
    }

    /** Plants and bins in a few free cells. */
    private void extras(Style s, Random rng) {
        List<int[]> spots = new ArrayList<>();
        for (int col = 1; col < MapPaths.COLS - 1; col++) {
            for (int row = 2; row < MapPaths.ROWS - 1; row++) {
                spots.add(new int[] { col, row });
            }
        }
        Collections.shuffle(spots, rng);
        int placed = 0;
        for (int[] spot : spots) {
            if (placed >= s.extras().size() * 2) {
                break;
            }
            // keep a free cell around each one so they do not crowd the furniture
            if (!free(spot[0] - 1, spot[1] - 1, 3, 3)) {
                continue;
            }
            R item = s.extras().get(placed % s.extras().size());
            standing.add(new Piece(Sheet.OBJECTS, item, spot[0] * CELL + (CELL - item.w()) / 2,
                    spot[1] * CELL + CELL - 4 - item.h(), 0));
            take(spot[0], spot[1], 1, 1);
            placed++;
        }
    }

    private boolean free(int col, int row, int w, int h) {
        for (int c = col; c < col + w; c++) {
            for (int r = row; r < row + h; r++) {
                if (c < 0 || r < 0 || c >= MapPaths.COLS || r >= MapPaths.ROWS || blocked[c][r]) {
                    return false;
                }
            }
        }
        return true;
    }

    private void take(int col, int row, int w, int h) {
        for (int c = col; c < col + w; c++) {
            for (int r = row; r < row + h; r++) {
                blocked[c][r] = true;
            }
        }
    }
}
