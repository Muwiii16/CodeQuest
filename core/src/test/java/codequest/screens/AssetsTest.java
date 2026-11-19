package codequest.screens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AssetsTest {

    @Test
    void onlyPicturesAreListedAndFontAtlasesAreLeftToTheFontLoader() {
        String list = "assets.txt\nui/fonts/ui_16.fnt\nui/fonts/ui_16.png\ngame/hero/run1.png\r\nui/buttons/icon_campaign.png\n"
                + "game/maps/map1_slots.txt\nsupabase.properties\n\n";
        assertEquals(List.of("game/hero/run1.png", "ui/buttons/icon_campaign.png"), Assets.pictureFiles(list));
    }

    @Test
    void anEmptyListMeansNothingToPreload() {
        assertTrue(Assets.pictureFiles("").isEmpty());
    }

    @Test
    void heroTowersMapsAndSoftEffectsAreSmoothedPixelArtIsNot() {
        assertTrue(Assets.smooth("game/hero/run1.png"));
        assertTrue(Assets.smooth("game/towers/t1.png"));
        assertTrue(Assets.smooth("game/maps/map1.png"));
        assertTrue(Assets.smooth("game/glow.png"));
        assertFalse(Assets.smooth("ui/backgrounds/menu.png"));
        assertFalse(Assets.smooth("game/hats/cap.png"));
    }
}
