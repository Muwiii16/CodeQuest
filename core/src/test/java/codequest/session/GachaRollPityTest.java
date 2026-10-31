package codequest.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GachaRollPityTest {

    private record Item(String name, String rarity) {
    }

    private static final List<Item> POOL = List.of(
            new Item("c1", "common"), new Item("c2", "common"), new Item("c3", "common"),
            new Item("r1", "rare"), new Item("r2", "rare"),
            new Item("e1", "epic"),
            new Item("l1", "legendary"));

    @Test
    void duplicatesAreAllowed() {
        Random rng = new Random(3);
        Set<String> seen = new HashSet<>();
        boolean repeated = false;
        for (int i = 0; i < 200; i++) {
            String name = GachaRoll.roll(POOL, Item::rarity, rng).orElseThrow().name();
            repeated |= !seen.add(name);
        }
        assertTrue(repeated);
    }

    @Test
    void aGuaranteedRollIsAlwaysRareOrBetter() {
        Random rng = new Random(9);
        for (int i = 0; i < 5000; i++) {
            Item won = GachaRoll.roll(POOL, Item::rarity, rng, true).orElseThrow();
            assertTrue(GachaRoll.rank(won.rarity()) >= 1, won.toString());
        }
    }

    @Test
    void aGuaranteedRollKeepsTheRelativeOddsOfRareEpicAndLegendary() {
        Random rng = new Random(21);
        int rare = 0;
        int rolls = 40000;
        for (int i = 0; i < rolls; i++) {
            if (GachaRoll.roll(POOL, Item::rarity, rng, true).orElseThrow().rarity().equals("rare")) {
                rare++;
            }
        }
        assertEquals(28.0 / 40.0, rare / (double) rolls, 0.02);
    }

    @Test
    void aPoolWithNothingRareFallsBackToWhatItHasWhenGuaranteed() {
        List<Item> onlyCommon = List.of(new Item("c1", "common"));
        assertEquals("c1", GachaRoll.roll(onlyCommon, Item::rarity, new Random(1), true).orElseThrow().name());
    }

    @Test
    void theHistoryCountsCommonsInARowFromTheNewestRoll() {
        assertEquals(0, GachaRoll.rollsSinceRare(List.of()));
        assertEquals(0, GachaRoll.rollsSinceRare(List.of("rare", "common", "common")));
        assertEquals(2, GachaRoll.rollsSinceRare(List.of("common", "common", "epic", "common")));
        assertEquals(3, GachaRoll.rollsSinceRare(List.of("common", "common", "common")));
    }

    @Test
    void pityIsDueOnTheLastRollOfTheWindow() {
        assertFalse(GachaRoll.pityDue(GachaRoll.PITY_ROLLS - 2));
        assertTrue(GachaRoll.pityDue(GachaRoll.PITY_ROLLS - 1));
        assertTrue(GachaRoll.pityDue(GachaRoll.PITY_ROLLS + 3));
    }

    @Test
    void thePityCountdownResetsAfterARareAndCountsDownOtherwise() {
        assertEquals(GachaRoll.PITY_ROLLS, GachaRoll.rollsUntilGuarantee(3, "rare"));
        assertEquals(GachaRoll.PITY_ROLLS - 1, GachaRoll.rollsUntilGuarantee(0, "common"));
        assertEquals(1, GachaRoll.rollsUntilGuarantee(GachaRoll.PITY_ROLLS - 2, "common"));
    }

    @Test
    void neverMoreThanFourCommonsInARowOverAVeryLongRun() {
        Random rng = new Random(777);
        List<String> historyNewestFirst = new ArrayList<>();
        int worstStreak = 0;
        int streak = 0;
        for (int i = 0; i < 20000; i++) {
            boolean pity = GachaRoll.pityDue(GachaRoll.rollsSinceRare(historyNewestFirst));
            Item won = GachaRoll.roll(POOL, Item::rarity, rng, pity).orElseThrow();
            historyNewestFirst.add(0, won.rarity());
            if (GachaRoll.rank(won.rarity()) >= 1) {
                streak = 0;
            } else {
                streak++;
                worstStreak = Math.max(worstStreak, streak);
            }
            if (historyNewestFirst.size() > 50) {
                historyNewestFirst.remove(historyNewestFirst.size() - 1);
            }
        }
        assertTrue(worstStreak <= GachaRoll.PITY_ROLLS - 1, "longest run of commons was " + worstStreak);
    }
}
