package codequest.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The places where a map has room for a tower. They are reserved when the map picture is made (nothing else is
 * drawn there, and every one is beside the path, never on it) and listed in {@code map<N>_slots.txt}. A tower takes
 * the slots that keep the towers in path order and spread along the whole route.
 */
public final class TowerSlots {

    private TowerSlots() {
    }

    /** A slot: how far along the path it lies and where the tower's spot is, in game pixels (top-left layout). */
    public record Slot(float s, float x, float y) {
    }

    /**
     * Reads a slot file. Lines are {@code s x y side} in cell units ("#" starts a comment); sizes are scaled by
     * {@code cellSize} and y is moved down by {@code offsetY} (the map's top edge on screen).
     */
    public static List<Slot> parse(String text, float cellSize, float offsetY) {
        List<Slot> slots = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            String[] f = t.split("\\s+");
            if (f.length < 3) {
                continue;
            }
            try {
                slots.add(new Slot(Float.parseFloat(f[0]) * cellSize, Float.parseFloat(f[1]) * cellSize,
                        Float.parseFloat(f[2]) * cellSize + offsetY));
            } catch (NumberFormatException e) {
                // a damaged line is skipped
            }
        }
        slots.sort((a, b) -> Float.compare(a.s(), b.s()));
        return slots;
    }

    /**
     * Gives each tower a different slot, keeping the towers in path order and choosing the set of slots that puts
     * every tower as close as possible (along the path) to where it belongs. Returns one entry per tower, null where
     * there were fewer slots than towers.
     *
     * @param towerS the path position of each tower, in increasing order
     */
    public static List<Slot> assign(List<Slot> slots, List<Float> towerS) {
        int n = towerS.size();
        int m = slots.size();
        List<Slot> result = new ArrayList<>(Arrays.asList(new Slot[n]));
        int usable = Math.min(n, m);
        if (usable == 0) {
            return result;
        }
        // match the first `usable` towers when slots are short; dp over (towers used, slots used)
        float inf = Float.MAX_VALUE / 4f;
        float[][] cost = new float[usable + 1][m + 1];
        for (float[] row : cost) {
            Arrays.fill(row, inf);
        }
        Arrays.fill(cost[0], 0f);
        for (int i = 1; i <= usable; i++) {
            for (int j = i; j <= m; j++) {
                float take = cost[i - 1][j - 1] + Math.abs(slots.get(j - 1).s() - towerS.get(i - 1));
                float skip = j > i ? cost[i][j - 1] : inf;
                cost[i][j] = Math.min(take, skip);
            }
        }
        int j = m;
        for (int i = usable; i >= 1; i--) {
            while (j > i && cost[i][j] == cost[i][j - 1]) {
                j--;
            }
            result.set(i - 1, slots.get(j - 1));
            j--;
        }
        return result;
    }
}
