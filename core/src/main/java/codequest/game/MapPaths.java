package codequest.game;

import java.util.ArrayList;
import java.util.List;

/**
 * The path of each map layout on the 20 x 11 cell grid: the corners it turns at, and every cell it crosses in walking
 * order. The campus pictures and the school rooms share these paths, so a lesson plays the same way in either.
 */
public final class MapPaths {

    public static final int COLS = 20;
    public static final int ROWS = 11;

    private static final int[][][] CORNERS = {
            { { 0, 8 }, { 11, 8 }, { 11, 6 }, { 8, 6 }, { 8, 3 }, { 13, 3 }, { 13, 0 } },
            { { 0, 6 }, { 7, 6 }, { 7, 2 }, { 12, 2 }, { 12, 6 }, { 16, 6 }, { 16, 9 } },
            { { 0, 3 }, { 6, 3 }, { 6, 8 }, { 10, 8 }, { 10, 2 }, { 14, 2 }, { 14, 6 }, { 19, 6 } } };

    private MapPaths() {
    }

    /** The corners of a layout (1, 2, 3, ... wrap around). */
    public static int[][] corners(int layout) {
        return CORNERS[(Math.max(1, layout) - 1) % CORNERS.length];
    }

    /** Every cell on the path, {col, row}, from the start to the end. */
    public static List<int[]> cells(int layout) {
        int[][] corners = corners(layout);
        List<int[]> out = new ArrayList<>();
        out.add(new int[] { corners[0][0], corners[0][1] });
        for (int i = 1; i < corners.length; i++) {
            int c = corners[i - 1][0];
            int r = corners[i - 1][1];
            int dc = Integer.signum(corners[i][0] - c);
            int dr = Integer.signum(corners[i][1] - r);
            while (c != corners[i][0] || r != corners[i][1]) {
                c += dc;
                r += dr;
                out.add(new int[] { c, r });
            }
        }
        return out;
    }
}
