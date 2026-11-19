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
    void readableFontsPickTheClosestSizeToo() {
        assertEquals(16, PixelFonts.nearestReadSize(1f));
        assertEquals(30, PixelFonts.nearestReadSize(2f));
        assertEquals(14, PixelFonts.nearestReadSize(0.5f));
        assertEquals(48, PixelFonts.nearestReadSize(10f));
        for (int i = 1; i < PixelFonts.READ_SIZES.length; i++) {
            assertTrue(PixelFonts.READ_SIZES[i] > PixelFonts.READ_SIZES[i - 1]);
        }
    }

    @Test
    void theBakedSizesAreSortedSoTheSearchIsSane() {
        for (int i = 1; i < PixelFonts.SIZES.length; i++) {
            assertTrue(PixelFonts.SIZES[i] > PixelFonts.SIZES[i - 1]);
        }
    }
}
