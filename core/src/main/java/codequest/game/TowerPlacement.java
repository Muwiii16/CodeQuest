package codequest.game;

import java.util.List;

/**
 * Chooses where a tower stands. The rule is strict: a tower stands beside the path, never on it. A tower's
 * picture must stay at least {@code clearance} away from every point of the path (the path is half a cell wide
 * on each side of its centre line, plus a little air), inside the playable area, and clear of other towers.
 * Pure geometry, so it is unit tested without the game running.
 */
public final class TowerPlacement {

    private TowerPlacement() {
    }

    /** The area a tower's picture covers, in the same top-left pixel coordinates as the path. */
    public record Box(float left, float top, float right, float bottom) {

        boolean overlaps(Box other, float gap) {
            return left < other.right + gap && right > other.left - gap && top < other.bottom + gap && bottom > other.top - gap;
        }

        boolean inside(Box area) {
            return left >= area.left && right <= area.right && top >= area.top && bottom <= area.bottom;
        }
    }

    /** Points along the path every few pixels, so distances to the path are easy to measure. */
    public static List<float[]> sample(List<java.awt.Point> waypoints, float step) {
        List<float[]> out = new java.util.ArrayList<>();
        for (int i = 1; i < waypoints.size(); i++) {
            java.awt.Point a = waypoints.get(i - 1);
            java.awt.Point b = waypoints.get(i);
            float length = (float) a.distance(b);
            int n = Math.max(1, (int) Math.ceil(length / step));
            for (int k = 0; k <= n; k++) {
                float t = k / (float) n;
                out.add(new float[] { a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t });
            }
        }
        if (out.isEmpty() && !waypoints.isEmpty()) {
            out.add(new float[] { waypoints.get(0).x, waypoints.get(0).y });
        }
        return out;
    }

    /** True if no point of the path comes closer than {@code clearance} to the box. */
    public static boolean clearOfPath(List<float[]> samples, Box box, float clearance) {
        float limit = clearance * clearance;
        for (float[] p : samples) {
            float dx = Math.max(Math.max(box.left() - p[0], 0f), p[0] - box.right());
            float dy = Math.max(Math.max(box.top() - p[1], 0f), p[1] - box.bottom());
            if (dx * dx + dy * dy < limit) {
                return false;
            }
        }
        return true;
    }

    /**
     * The first spot that satisfies the rule, trying the preferred side of the path first, then the other side, at
     * growing distances and with small shifts along the path; or null if there is none.
     *
     * @param on        the point on the path the tower belongs to
     * @param dir       the direction of the path there (unit length)
     * @param side      +1 or -1: the preferred side
     * @param boxAbove  how far the picture reaches above the tower's spot
     * @param boxBelow  how far it reaches below
     * @param halfWidth half the picture's width
     */
    public static float[] choose(List<float[]> samples, float onX, float onY, float dirX, float dirY, int side, float halfWidth,
            float boxAbove, float boxBelow, float clearance, Box area, List<Box> taken) {
        // the picture reaches about 60 px above and below its spot, so the spot must be that much farther away than the clearance
        float[] distances = { 104f, 112f, 122f, 134f, 148f, 164f, 184f };
        float[] shifts = { 0f, 30f, -30f, 60f, -60f, 90f, -90f };
        for (float distance : distances) {
            for (float shift : shifts) {
                for (int s = 0; s < 2; s++) {
                    float sign = s == 0 ? side : -side;
                    float x = onX - dirY * distance * sign + dirX * shift;
                    float y = onY + dirX * distance * sign + dirY * shift;
                    Box box = new Box(x - halfWidth, y - boxAbove, x + halfWidth, y + boxBelow);
                    if (!box.inside(area) || !clearOfPath(samples, box, clearance)) {
                        continue;
                    }
                    boolean free = true;
                    for (Box other : taken) {
                        if (box.overlaps(other, 6f)) {
                            free = false;
                            break;
                        }
                    }
                    if (free) {
                        return new float[] { x, y };
                    }
                }
            }
        }
        return null;
    }
}
