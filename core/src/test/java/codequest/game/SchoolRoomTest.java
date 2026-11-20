package codequest.game;

import codequest.game.SchoolRoom.Piece;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchoolRoomTest {

    private static final Path ASSETS = Path.of("..", "assets");

    private static List<float[]> spots(int layout) throws Exception {
        List<float[]> out = new ArrayList<>();
        String text = Files.readString(ASSETS.resolve("game/maps/map" + layout + "_slots.txt"));
        for (TowerSlots.Slot s : TowerSlots.parse(text, 1f, 0f)) {
            out.add(new float[] { s.x(), s.y() });
        }
        return out;
    }

    /** The cells a standing piece's foot (its bottom row of pixels) is in. */
    private static List<int[]> footCells(Piece p) {
        List<int[]> out = new ArrayList<>();
        int row = (p.bottom() - 1) / SchoolRoom.CELL;
        for (int c = p.dx() / SchoolRoom.CELL; c <= (p.dx() + p.src().w() - 1) / SchoolRoom.CELL; c++) {
            out.add(new int[] { c, row });
        }
        return out;
    }

    @Test
    void themesAreReadFromTheirKeyAndOlderStagesAreCampus() {
        assertEquals(MapTheme.CLASSROOM, MapTheme.fromKey("classroom"));
        assertEquals(MapTheme.LIBRARY, MapTheme.fromKey(" Library "));
        assertEquals(MapTheme.CAMPUS, MapTheme.fromKey(null));
        assertEquals(MapTheme.CAMPUS, MapTheme.fromKey("old_background.png"));
        assertFalse(MapTheme.CAMPUS.isSchool());
    }

    @Test
    void furnitureNeverStandsOnThePathOrWhereATowerGoes() throws Exception {
        for (MapTheme theme : MapTheme.values()) {
            if (!theme.isSchool()) {
                continue;
            }
            for (int layout = 1; layout <= 3; layout++) {
                boolean[][] taken = new boolean[MapPaths.COLS][MapPaths.ROWS];
                for (int[] c : MapPaths.cells(layout)) {
                    taken[c[0]][c[1]] = true;
                }
                for (float[] s : spots(layout)) {
                    for (int[] c : SchoolRoom.towerCells(s[0], s[1])) {
                        taken[c[0]][c[1]] = true;
                    }
                }
                int furniture = 0;
                for (Piece p : SchoolRoom.build(theme, layout, spots(layout))) {
                    if (p.sheet() != SchoolRoom.Sheet.OBJECTS || p.bottom() <= SchoolRoom.FACE) {
                        continue; // floor, walls and things hung on the wall
                    }
                    furniture++;
                    for (int[] c : footCells(p)) {
                        assertFalse(taken[c[0]][c[1]], theme + " layout " + layout + ": " + p + " stands on the path or a tower");
                    }
                }
                assertTrue(furniture >= 4, theme + " layout " + layout + " has almost no furniture: " + furniture);
            }
        }
    }

    @Test
    void everyPieceIsInsideItsSheetAndTheRoom() throws Exception {
        for (MapTheme theme : List.of(MapTheme.CLASSROOM, MapTheme.LIBRARY, MapTheme.HALLWAY)) {
            for (int layout = 1; layout <= 3; layout++) {
                for (Piece p : SchoolRoom.build(theme, layout, spots(layout))) {
                    assertTrue(p.dx() >= 0 && p.dy() >= 0 && p.dx() + p.src().w() <= SchoolRoom.WIDTH
                            && p.bottom() <= SchoolRoom.HEIGHT, "outside the room: " + p);
                    if (p.sheet() != null) {
                        var img = javax.imageio.ImageIO.read(ASSETS.resolve("game/school/16/" + p.sheet().file() + ".png").toFile());
                        assertTrue(p.src().x() + p.src().w() <= img.getWidth() && p.src().y() + p.src().h() <= img.getHeight(),
                                "outside " + p.sheet() + ": " + p);
                    }
                }
            }
        }
    }

    @Test
    void theSameThemeAndLayoutAlwaysGiveTheSameRoom() throws Exception {
        assertEquals(SchoolRoom.build(MapTheme.CLASSROOM, 2, spots(2)), SchoolRoom.build(MapTheme.CLASSROOM, 2, spots(2)));
        assertNotEquals(SchoolRoom.build(MapTheme.CLASSROOM, 2, spots(2)), SchoolRoom.build(MapTheme.LIBRARY, 2, spots(2)));
    }

    @Test
    void theRoomHasTheSameShapeAsTheCampusMaps() {
        // the campus pictures are 1640 x 902; GameMap draws the room in the same box, so it must not be stretched
        assertEquals(1640.0 / 902.0, (double) SchoolRoom.WIDTH / SchoolRoom.HEIGHT, 0.01);
    }
}
