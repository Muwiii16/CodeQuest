package codequest.certificate;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * Draws a lesson badge as a picture (PNG) a student can save and show their professor as proof of what they accomplished.
 * Plain Java2D, so it needs no window and can be tested without the game running.
 */
public final class BadgeImage {

    public static final int SIZE = 900;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private BadgeImage() {
    }

    /** The shield-and-laurel frame (resources/ui/badge_frame.png), or null when it is not on the class path. */
    private static BufferedImage frame() {
        try (var in = BadgeImage.class.getResourceAsStream("/ui/badge_frame.png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static BufferedImage render(BadgeData d) {
        BufferedImage frame = frame();
        return frame == null ? renderMedal(d) : renderFramed(d, frame);
    }

    /** The badge inside the blue shield frame: rank, lesson, period, student, subject and date on the navy panel. */
    private static BufferedImage renderFramed(BadgeData d, BufferedImage frame) {
        int w = frame.getWidth();
        BufferedImage img = new BufferedImage(w, frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.drawImage(frame, 0, 0, null);
            Color gold = new Color(0xFFC83D);
            Color soft = new Color(0xBFD4FF);
            int inner = w - 220; // the room between the frame's borders
            centered(g, w, new Font(Font.SANS_SERIF, Font.BOLD, 34), "LESSON BADGE", 275, gold);
            centered(g, w, new Font(Font.SERIF, Font.BOLD, 210), d.rank(), 500, rankColor(d.rank()).brighter());
            centered(g, w, new Font(Font.SANS_SERIF, Font.BOLD, 32), fit(g, "Lesson " + d.lessonNumber() + ": " + d.lessonTitle(), inner, 32),
                    585, Color.WHITE);
            centered(g, w, new Font(Font.SANS_SERIF, Font.PLAIN, 28), d.period() + "   -   " + d.gradePercent() + "%", 635, gold);
            centered(g, w, new Font(Font.SANS_SERIF, Font.BOLD, 38), fit(g, d.studentName(), inner, 38), 725, Color.WHITE);
            String where = d.section() == null || d.section().isBlank() ? d.subject() : d.subject() + "  (" + d.section() + ")";
            centered(g, w, new Font(Font.SANS_SERIF, Font.PLAIN, 26), fit(g, where, inner, 26), 773, soft);
            centered(g, w, new Font(Font.SANS_SERIF, Font.PLAIN, 24), d.earnedOn().format(DATE), 818, soft);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static BufferedImage renderMedal(BadgeData d) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color main = rankColor(d.rank());
            Color dark = main.darker().darker();

            // the medal: a dark ring, a coloured disc, an inner ring
            g.setPaint(new GradientPaint(0, 0, dark, SIZE, SIZE, main.darker()));
            g.fill(new Ellipse2D.Float(20, 20, SIZE - 40, SIZE - 40));
            g.setPaint(new GradientPaint(0, 60, main.brighter(), 0, SIZE - 60, main));
            g.fill(new Ellipse2D.Float(60, 60, SIZE - 120, SIZE - 120));
            g.setColor(new Color(255, 255, 255, 170));
            g.setStroke(new BasicStroke(6f));
            g.draw(new Ellipse2D.Float(95, 95, SIZE - 190, SIZE - 190));

            Color ink = new Color(0x1B2233);
            centered(g, new Font(Font.SANS_SERIF, Font.BOLD, 34), "C O D E Q U E S T", 190, ink);
            centered(g, new Font(Font.SANS_SERIF, Font.BOLD, 46), "LESSON BADGE", 250, ink);
            centered(g, new Font(Font.SERIF, Font.BOLD, 230), d.rank(), 480, Color.WHITE);
            centered(g, new Font(Font.SANS_SERIF, Font.BOLD, 40), fit(g, "Lesson " + d.lessonNumber() + ": " + d.lessonTitle(), 560, 40),
                    560, ink);
            centered(g, new Font(Font.SANS_SERIF, Font.PLAIN, 30), d.period() + "   -   " + d.gradePercent() + "%", 610, ink);
            centered(g, new Font(Font.SANS_SERIF, Font.BOLD, 38), fit(g, d.studentName(), 560, 38), 680, Color.WHITE);
            String where = d.section() == null || d.section().isBlank() ? d.subject() : d.subject() + "  (" + d.section() + ")";
            centered(g, new Font(Font.SANS_SERIF, Font.PLAIN, 28), fit(g, where, 560, 28), 725, ink);
            centered(g, new Font(Font.SANS_SERIF, Font.PLAIN, 26), "Earned on " + d.earnedOn().format(DATE), 765, ink);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** The badge as PNG bytes. */
    public static byte[] png(BadgeData d) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(render(d), "png", out);
        return out.toByteArray();
    }

    static Color rankColor(String rank) {
        return switch (rank == null ? "" : rank) {
            case "S" -> new Color(0xE0A82E);
            case "A" -> new Color(0x3FA46A);
            case "B" -> new Color(0x3C7FD0);
            case "C" -> new Color(0x2FA3A0);
            case "D" -> new Color(0xD27A2C);
            default -> new Color(0x8A93A6);
        };
    }

    private static void centered(Graphics2D g, Font font, String text, int baseline, Color color) {
        centered(g, SIZE, font, text, baseline, color);
    }

    private static void centered(Graphics2D g, int width, Font font, String text, int baseline, Color color) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (width - fm.stringWidth(text)) / 2f, baseline);
    }

    /** The text, shortened with "..." when it is wider than the badge allows at this size. */
    private static String fit(Graphics2D g, String text, int maxWidth, int size) {
        FontMetrics fm = g.getFontMetrics(new Font(Font.SANS_SERIF, Font.BOLD, size));
        String t = text == null ? "" : text;
        while (fm.stringWidth(t) > maxWidth && t.length() > 4) {
            t = t.substring(0, t.length() - 4) + "...";
        }
        return t;
    }
}
