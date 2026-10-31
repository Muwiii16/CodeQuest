package codequest.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GachaRollTest {

    private record Item(String name, String rarity) {
    }

    private static final List<Item> POOL = List.of(
            new Item("c1", "common"), new Item("c2", "common"), new Item("c3", "common"),
            new Item("r1", "rare"), new Item("r2", "rare"),
            new Item("e1", "epic"),
            new Item("l1", "legendary"));

    @Test
    void theOddsAddUpToOneHundredPercent() {
        assertEquals(100, GachaRoll.ODDS.stream().mapToInt(GachaRoll.Odds::percent).sum());
    }

    @Test
    void commonIsFarLikelierThanRareAndRareThanEpicAndEpicThanLegendary() {
        Random rng = new Random(2026);
        Map<String, Integer> counts = new HashMap<>();
        int rolls = 40000;
        for (int i = 0; i < rolls; i++) {
            Item won = GachaRoll.roll(POOL, Item::rarity, rng).orElseThrow();
            counts.merge(won.rarity(), 1, Integer::sum);
        }
        assertEquals(0.60, counts.get("common") / (double) rolls, 0.02);
        assertEquals(0.28, counts.get("rare") / (double) rolls, 0.02);
        assertEquals(0.09, counts.get("epic") / (double) rolls, 0.015);
        assertEquals(0.03, counts.get("legendary") / (double) rolls, 0.01);
    }

    @Test
    void anItemIsPickedEvenlyWithinItsRarity() {
        Random rng = new Random(7);
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 30000; i++) {
            Item won = GachaRoll.roll(POOL, Item::rarity, rng).orElseThrow();
            if (won.rarity().equals("common")) {
                counts.merge(won.name(), 1, Integer::sum);
            }
        }
        int a = counts.get("c1");
        int b = counts.get("c2");
        int c = counts.get("c3");
        assertTrue(Math.abs(a - b) < 0.1 * a && Math.abs(b - c) < 0.1 * b);
    }

    @Test
    void aRarityWithNothingLeftIsSkippedAndTheOthersKeepTheirRelativeOdds() {
        List<Item> onlyCommonAndLegendary = List.of(new Item("c1", "common"), new Item("l1", "legendary"));
        Random rng = new Random(11);
        int legendary = 0;
        int rolls = 30000;
        for (int i = 0; i < rolls; i++) {
            if (GachaRoll.roll(onlyCommonAndLegendary, Item::rarity, rng).orElseThrow().rarity().equals("legendary")) {
                legendary++;
            }
        }
        assertEquals(3.0 / 63.0, legendary / (double) rolls, 0.01);
    }

    @Test
    void nothingToWinGivesNothing() {
        assertEquals(Optional.empty(), GachaRoll.roll(new ArrayList<Item>(), Item::rarity, new Random(1)));
    }

    @Test
    void theSameSeedGivesTheSameResult() {
        assertEquals(GachaRoll.roll(POOL, Item::rarity, new Random(5)), GachaRoll.roll(POOL, Item::rarity, new Random(5)));
    }

    @Test
    void rarityRankRunsFromCommonToLegendaryAndUnknownIsCommon() {
        assertEquals(0, GachaRoll.rank("common"));
        assertEquals(1, GachaRoll.rank("RARE"));
        assertEquals(2, GachaRoll.rank("epic"));
        assertEquals(3, GachaRoll.rank("legendary"));
        assertEquals(0, GachaRoll.rank("mythic"));
    }

    @Test
    void theOddsTextListsEveryRarity() {
        assertEquals("Common 60%     Rare 28%     Epic 9%     Legendary 3%", GachaRoll.oddsText());
    }
}
