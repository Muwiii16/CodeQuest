package codequest.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

/**
 * The reward roll for finishing a lesson: a rarity is picked by weight (common is far likelier than
 * rare, epic or legendary), then one item of that rarity. Duplicates are possible. A pity rule keeps
 * bad luck from lasting: after {@link #PITY_ROLLS} - 1 rolls in a row with nothing rare or better, the next
 * roll is guaranteed to be rare or better. Pure logic, so the odds and the pity can be unit tested.
 */
public final class GachaRoll {

    /** One rarity and its chance in percent (the percents add up to 100). */
    public record Odds(String rarity, int percent) {
    }

    public static final List<Odds> ODDS = List.of(new Odds("common", 60), new Odds("rare", 28), new Odds("epic", 9),
            new Odds("legendary", 3));

    /** Within this many rolls a rare or better item is guaranteed: roll number PITY_ROLLS is forced if the earlier ones were all common. */
    public static final int PITY_ROLLS = 5;

    private GachaRoll() {
    }

    /** 0 for common up to 3 for legendary; anything unknown counts as common. */
    public static int rank(String rarity) {
        for (int i = 0; i < ODDS.size(); i++) {
            if (ODDS.get(i).rarity().equalsIgnoreCase(rarity)) {
                return i;
            }
        }
        return 0;
    }

    /** "Common 60%   Rare 28%   Epic 9%   Legendary 3%" for showing the player the odds. */
    public static String oddsText() {
        StringBuilder sb = new StringBuilder();
        for (Odds o : ODDS) {
            sb.append(sb.length() == 0 ? "" : "     ").append(Character.toUpperCase(o.rarity().charAt(0)))
                    .append(o.rarity().substring(1)).append(' ').append(o.percent()).append('%');
        }
        return sb.toString();
    }

    /** How many rolls in a row, newest first, came up common (stops at the first rare or better). */
    public static int rollsSinceRare(List<String> raritiesNewestFirst) {
        int count = 0;
        for (String r : raritiesNewestFirst) {
            if (rank(r) >= 1) {
                break;
            }
            count++;
        }
        return count;
    }

    /** True when the next roll must be rare or better. */
    public static boolean pityDue(int rollsSinceRare) {
        return rollsSinceRare >= PITY_ROLLS - 1;
    }

    /** After a roll of this rarity, how many rolls until a rare or better one is guaranteed (1 = the very next roll). */
    public static int rollsUntilGuarantee(int rollsSinceRareBefore, String rolledRarity) {
        int since = rank(rolledRarity) >= 1 ? 0 : rollsSinceRareBefore + 1;
        return Math.max(1, PITY_ROLLS - since);
    }

    /** Picks one candidate by the normal odds; empty when there are none. */
    public static <T> Optional<T> roll(List<T> candidates, Function<T, String> rarityOf, Random rng) {
        return roll(candidates, rarityOf, rng, false);
    }

    /**
     * Picks one candidate. With {@code guaranteeRareOrBetter} only rare, epic and legendary items can come
     * up (at their normal odds relative to each other), as long as the pool has any.
     */
    public static <T> Optional<T> roll(List<T> candidates, Function<T, String> rarityOf, Random rng,
            boolean guaranteeRareOrBetter) {
        Map<Integer, List<T>> byRank = new LinkedHashMap<>();
        for (T c : candidates) {
            byRank.computeIfAbsent(rank(rarityOf.apply(c)), k -> new ArrayList<>()).add(c);
        }
        if (guaranteeRareOrBetter && byRank.keySet().stream().anyMatch(r -> r >= 1)) {
            byRank.keySet().removeIf(r -> r < 1);
        }
        int total = 0;
        for (int r : byRank.keySet()) {
            total += ODDS.get(r).percent();
        }
        if (total == 0) {
            return Optional.empty();
        }
        int pick = rng.nextInt(total);
        for (Map.Entry<Integer, List<T>> e : byRank.entrySet()) {
            pick -= ODDS.get(e.getKey()).percent();
            if (pick < 0) {
                List<T> pool = e.getValue();
                return Optional.of(pool.get(rng.nextInt(pool.size())));
            }
        }
        return Optional.empty();
    }
}
