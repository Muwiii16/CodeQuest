package codequest.game;

import codequest.game.TowerPlacement.Box;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TowerPlacementTest {

    private static final float HALF_W = 39f;
    private static final float ABOVE = 61f;
    private static final float BELOW = 60f;
    private static final float CLEARANCE = 43f;
    private static final Box AREA = new Box(0f, 0f, 800f, 700f);

    private static Box boxAt(float[] spot) {
        return new Box(spot[0] - HALF_W, spot[1] - ABOVE, spot[0] + HALF_W, spot[1] + BELOW);
    }

    /** A U-shaped path: along the top, down the right side and back along the bottom, 180 px between the two long runs. */
    private static List<Point> uPath() {
        return List.of(new Point(100, 200), new Point(700, 200), new Point(700, 380), new Point(100, 380));
    }

    @Test
    void aTowerNextToAStraightPathNeverTouchesIt() {
        List<float[]> samples = TowerPlacement.sample(List.of(new Point(100, 300), new Point(700, 300)), 8f);
        for (int side : new int[] { 1, -1 }) {
            float[] spot = TowerPlacement.choose(samples, 300f, 300f, 1f, 0f, side, HALF_W, ABOVE, BELOW, CLEARANCE, AREA, List.of());
            assertNotNull(spot);
            assertTrue(TowerPlacement.clearOfPath(samples, boxAt(spot), CLEARANCE));
        }
    }

    @Test
    void whenThePreferredSideIsTooCloseToTheOtherPartOfThePathTheTowerGoesToTheFreeSide() {
        List<float[]> samples = TowerPlacement.sample(uPath(), 8f);
        // below the top run there are only 180 px before the bottom run: no room for a tower there
        float[] spot = TowerPlacement.choose(samples, 400f, 200f, 1f, 0f, 1, HALF_W, ABOVE, BELOW, CLEARANCE, AREA, List.of());
        assertNotNull(spot);
        assertTrue(spot[1] < 200f, "the tower should be above the top run, was at y=" + spot[1]);
        assertTrue(TowerPlacement.clearOfPath(samples, boxAt(spot), CLEARANCE));
    }

    @Test
    void noSpotThatTouchesThePathIsEverReturnedAlongTheWholeRoute() {
        List<float[]> samples = TowerPlacement.sample(uPath(), 8f);
        List<Box> taken = new ArrayList<>();
        for (float x = 150f; x <= 650f; x += 100f) {
            for (int side : new int[] { 1, -1 }) {
                float[] spot = TowerPlacement.choose(samples, x, 200f, 1f, 0f, side, HALF_W, ABOVE, BELOW, CLEARANCE, AREA, taken);
                if (spot == null) {
                    continue;
                }
                assertTrue(TowerPlacement.clearOfPath(samples, boxAt(spot), CLEARANCE));
                assertTrue(boxAt(spot).inside(AREA));
                taken.add(boxAt(spot));
            }
        }
        assertFalse(taken.isEmpty());
    }

    @Test
    void towersDoNotStandOnEachOther() {
        List<float[]> samples = TowerPlacement.sample(List.of(new Point(100, 300), new Point(700, 300)), 8f);
        List<Box> taken = new ArrayList<>();
        float[] first = TowerPlacement.choose(samples, 300f, 300f, 1f, 0f, 1, HALF_W, ABOVE, BELOW, CLEARANCE, AREA, taken);
        assertNotNull(first);
        taken.add(boxAt(first));
        float[] second = TowerPlacement.choose(samples, 310f, 300f, 1f, 0f, 1, HALF_W, ABOVE, BELOW, CLEARANCE, AREA, taken);
        assertNotNull(second);
        assertFalse(boxAt(second).overlaps(taken.get(0), 0f));
    }

    @Test
    void aSpotOutsideThePlayableAreaIsRejected() {
        List<float[]> samples = TowerPlacement.sample(List.of(new Point(100, 150), new Point(700, 150)), 8f);
        Box area = new Box(0f, 40f, 800f, 700f);
        // above the path there is no room (the area starts at y = 40), below is fine
        float[] spot = TowerPlacement.choose(samples, 300f, 150f, 1f, 0f, -1, HALF_W, ABOVE, BELOW, CLEARANCE, area, List.of());
        assertNotNull(spot);
        assertTrue(spot[1] > 150f);
        assertTrue(boxAt(spot).inside(area));
    }
}
