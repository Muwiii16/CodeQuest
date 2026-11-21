package codequest.certificate;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgeImageTest {

    private static BadgeData badge(String rank, String title) {
        return new BadgeData("Ana Santos", "Programming 1", "BSCS 1-1", 3, title, "Prelims", rank, 92, LocalDate.of(2026, 11, 21));
    }

    @Test
    void theBadgeIsAValidPngOfTheExpectedSize() throws Exception {
        byte[] png = BadgeImage.png(badge("A", "Pointers"));
        assertTrue(png.length > 1000);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(img);
        assertTrue(img.getWidth() > 500);
        assertTrue(img.getHeight() > 500);
    }

    @Test
    void aVeryLongTitleStillDrawsAndEachRankHasItsOwnColour() throws Exception {
        assertTrue(BadgeImage.png(badge("S", "A very long lesson title ".repeat(20))).length > 1000);
        assertNotEquals(BadgeImage.rankColor("S"), BadgeImage.rankColor("B"));
        assertNotEquals(BadgeImage.rankColor("A"), BadgeImage.rankColor("D"));
    }
}
