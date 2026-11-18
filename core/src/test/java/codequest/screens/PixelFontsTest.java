package codequest.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PixelFontsTest {

    @Test
    void aScaleOfOneIsTheSizeOfTheOldDefaultFont() {
        assertEquals(16, PixelFonts.nearestSize(1f));
    }

    @Test
    void eachScaleGetsTheClosestBakedSize() {
        assertEquals(20, PixelFonts.nearestSize(1.3f)); // 19.5
        assertEquals(24, PixelFonts.nearestSize(1.6f)); // 24
        assertEquals(32, PixelFonts.nearestSize(2.2f)); // 33
        assertEquals(12, PixelFonts.nearestSize(0.5f)); // below the smallest
        assertEquals(96, PixelFonts.nearestSize(10f)); // above the largest
    }

    @Test
    void theBakedSizesAreSortedSoTheSearchIsSane() {
        for (int i = 1; i < PixelFonts.SIZES.length; i++) {
            assertTrue(PixelFonts.SIZES[i] > PixelFonts.SIZES[i - 1]);
        }
    }
}
