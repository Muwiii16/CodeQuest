package codequest.game;

import codequest.game.TowerSlots.Slot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TowerSlotsTest {

    @Test
    void theSlotFileIsReadInCellUnitsAndScaledToThePictureOnScreen() {
        List<Slot> slots = TowerSlots.parse("# comment\n2.0 3.0 4.0 1\n\n1.0 1.5 2.5 -1\nbroken line\n", 70f, 100f);
        assertEquals(2, slots.size());
        assertEquals(70f, slots.get(0).s(), 0.001f); // sorted by distance along the path
        assertEquals(105f, slots.get(0).x(), 0.001f);
        assertEquals(275f, slots.get(0).y(), 0.001f);
        assertEquals(140f, slots.get(1).s(), 0.001f);
    }

    @Test
    void eachTowerGetsADifferentSlotInPathOrder() {
        List<Slot> slots = TowerSlots.parse("1 0 0 1\n2 0 0 1\n3 0 0 1\n4 0 0 1\n5 0 0 1\n", 1f, 0f);
        List<Slot> given = TowerSlots.assign(slots, List.of(1.1f, 2.9f, 5.2f));
        assertEquals(3, given.size());
        assertEquals(1f, given.get(0).s(), 0.001f);
        assertEquals(3f, given.get(1).s(), 0.001f);
        assertEquals(5f, given.get(2).s(), 0.001f);
    }

    @Test
    void towersAreSpreadOutInsteadOfBunchedWhenTheyWantTheSameSpot() {
        List<Slot> slots = TowerSlots.parse("1 0 0 1\n2 0 0 1\n3 0 0 1\n4 0 0 1\n", 1f, 0f);
        List<Slot> given = TowerSlots.assign(slots, List.of(2.4f, 2.5f, 2.6f));
        long distinct = given.stream().distinct().count();
        assertEquals(3, distinct);
        for (int i = 1; i < given.size(); i++) {
            assertTrue(given.get(i).s() > given.get(i - 1).s());
        }
    }

    @Test
    void whenThereAreTooFewSlotsTheLastTowersGetNone() {
        List<Slot> slots = TowerSlots.parse("1 0 0 1\n2 0 0 1\n", 1f, 0f);
        List<Slot> given = TowerSlots.assign(slots, List.of(1f, 2f, 3f));
        assertNotNull(given.get(0));
        assertNotNull(given.get(1));
        assertNull(given.get(2));
    }

    @Test
    void noSlotsGivesNobodyAnything() {
        List<Slot> given = TowerSlots.assign(List.of(), List.of(1f, 2f));
        assertEquals(2, given.size());
        assertNull(given.get(0));
        assertNull(given.get(1));
    }
}
