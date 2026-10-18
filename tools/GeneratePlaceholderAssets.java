import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Draws CodeQuest's TEMPORARY placeholder art (assets/ui and assets/game) with
 * plain java.awt — no dependencies, so it runs anywhere:
 *
 *     java -Djava.awt.headless=true tools/GeneratePlaceholderAssets.java assets
 *
 * Every file here is meant to be overwritten with real art later: keep the same
 * file names and the game picks the new files up with no code changes.
 */
public class GeneratePlaceholderAssets {

    static File out;

    static final Color BG_TOP = new Color(0x0B1026);
    static final Color BG_BOTTOM = new Color(0x1B2A5E);
    static final Color PANEL = new Color(0x141C3D);
    static final Color PANEL_HOVER = new Color(0x22306A);
    static final Color ACCENT = new Color(0x2EC4B6);
    static final Color AMBER = new Color(0xFFB703);
    static final Color RED = new Color(0xE5484D);
    static final Color GREEN = new Color(0x46C46E);
    static final Color TEXT = new Color(0xEAF0FF);
    static final Color MUTED = new Color(0x9AA8D6);

    public static void main(String[] args) throws IOException {
        out = new File(args.length > 0 ? args[0] : "assets");

        save(background(1), "ui/backgrounds/menu.png");
        save(introBackground(), "ui/backgrounds/intro.png");
        save(professorBackground(), "ui/backgrounds/professor.png");
        save(forestBackground(), "ui/backgrounds/login_forest.png");
        save(logoMark(), "ui/titles/logo_mark.png");
        save(loginCard(), "ui/login/card.png");

        save(logo(), "ui/titles/logo.png");

        String[][] menu = { { "campaign", "CAMPAIGN" }, { "class_test", "CLASS TEST" }, { "cosmetics", "COSMETICS" },
                { "certificates", "CERTIFICATES" }, { "back", "BACK" } };
        for (String[] m : menu) {
            save(button(m[1], 720, 180, false, ACCENT), "ui/buttons/" + m[0] + "_def.png");
            save(button(m[1], 720, 180, true, ACCENT), "ui/buttons/" + m[0] + "_hover.png");
        }

        int[][][] paths = {
                { { 0, 8 }, { 11, 8 }, { 11, 6 }, { 8, 6 }, { 8, 3 }, { 13, 3 }, { 13, 0 } },
                { { 0, 6 }, { 7, 6 }, { 7, 2 }, { 12, 2 }, { 12, 6 }, { 16, 6 }, { 16, 9 } },
                { { 0, 3 }, { 6, 3 }, { 6, 8 }, { 10, 8 }, { 10, 2 }, { 14, 2 }, { 14, 6 }, { 19, 6 } } };
        for (int i = 0; i < 3; i++) {
            save(map(paths[i], i + 1), "game/maps/map" + (i + 1) + ".png");
        }
        save(lab(), "game/lab.png");
        save(avatar(), "game/avatar.png");
        save(projectile(), "game/projectile.png");
        String[][] towers = { { "syntax", ";" }, { "variables", "x" }, { "control_structures", "if" },
                { "functions", "f()" }, { "arrays", "[ ]" }, { "pointers", "*p" } };
        Color[] towerColors = { new Color(0xF2C14E), new Color(0x5AA9E6), new Color(0xB48AF2),
                new Color(0x4FD1B0), new Color(0x86D95C), new Color(0xF2796B) };
        for (int i = 0; i < towers.length; i++) {
            save(tower(towers[i][1], towerColors[i]), "game/towers/tower_" + towers[i][0] + ".png");
        }
        System.out.println("Placeholder assets written to " + out.getAbsolutePath());
    }

    // ── helpers ──────────────────────────────────────────

    static BufferedImage img(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    static Graphics2D g(BufferedImage im) {
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    static void save(BufferedImage im, String path) throws IOException {
        File f = new File(out, path);
        f.getParentFile().mkdirs();
        ImageIO.write(im, "png", f);
    }

    static Font sans(int size) {
        return new Font("SansSerif", Font.BOLD, size);
    }

    static Font mono(int size) {
        return new Font("Monospaced", Font.BOLD, size);
    }

    static void centered(Graphics2D g, String s, float cx, float baselineY) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2f, baselineY);
    }

    static void centeredIn(Graphics2D g, String s, int w, int h) {
        FontMetrics fm = g.getFontMetrics();
        centered(g, s, w / 2f, (h - fm.getHeight()) / 2f + fm.getAscent());
    }

    // ── backgrounds ──────────────────────────────────────

    static BufferedImage background(int variant) {
        BufferedImage im = img(1920, 1080);
        Graphics2D g = g(im);
        Color top = variant == 1 ? BG_TOP : new Color(0x120B2E);
        Color bottom = variant == 1 ? BG_BOTTOM : new Color(0x2C1B5E);
        g.setPaint(new GradientPaint(0, 0, top, 0, 1080, bottom));
        g.fillRect(0, 0, 1920, 1080);
        g.setColor(new Color(255, 255, 255, 14));
        for (int x = 24; x < 1920; x += 48) {
            for (int y = 24; y < 1080; y += 48) {
                g.fillOval(x, y, 4, 4);
            }
        }
        String[] glyphs = { "{", "}", ";", "int", "[ ]", "*p", "if", "for", "0x", "()", "&x", "main" };
        Random r = new Random(7 + variant);
        for (int i = 0; i < 42; i++) {
            g.setFont(mono(40 + r.nextInt(80)));
            g.setColor(new Color(255, 255, 255, 14 + r.nextInt(14)));
            g.drawString(glyphs[r.nextInt(glyphs.length)], r.nextInt(1850), 60 + r.nextInt(1000));
        }
        g.dispose();
        return im;
    }

    static BufferedImage professorBackground() {
        BufferedImage im = img(1920, 1080);
        Graphics2D g = g(im);
        g.setPaint(new GradientPaint(0, 0, new Color(0x161A24), 0, 1080, new Color(0x0D0F16)));
        g.fillRect(0, 0, 1920, 1080);
        g.setColor(new Color(245, 165, 36, 14));
        g.setStroke(new BasicStroke(2));
        for (int x = 0; x < 1920; x += 120) {
            g.drawLine(x, 0, x, 1080);
        }
        for (int y = 0; y < 1080; y += 120) {
            g.drawLine(0, y, 1920, y);
        }
        g.dispose();
        return im;
    }

    /** Top-down forest with a winding dirt path and a few stone towers (stand-in for the real login art). */
    static BufferedImage forestBackground() {
        BufferedImage im = img(1920, 1080);
        Graphics2D g = g(im);
        g.setPaint(new GradientPaint(0, 0, new Color(0x24502F), 0, 1080, new Color(0x1B3E27)));
        g.fillRect(0, 0, 1920, 1080);
        Random r = new Random(11);
        for (int i = 0; i < 700; i++) {
            int x = r.nextInt(1920);
            int y = r.nextInt(1080);
            g.setColor(new Color(255, 255, 255, 5 + r.nextInt(8)));
            g.fillOval(x, y, 20 + r.nextInt(60), 14 + r.nextInt(30));
        }
        Path2D.Double path = new Path2D.Double();
        path.moveTo(-60, 900);
        path.curveTo(300, 760, 560, 980, 820, 780);
        path.curveTo(1060, 600, 700, 480, 900, 330);
        path.curveTo(1060, 200, 1500, 360, 1980, 120);
        g.setStroke(new BasicStroke(120, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x6E4B2A));
        g.draw(path);
        g.setStroke(new BasicStroke(96, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xA2723E));
        g.draw(path);
        g.setStroke(new BasicStroke(30, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xB98550));
        g.draw(path);

        List<Point2D.Double> samples = new ArrayList<>();
        for (PathIterator it = path.getPathIterator(null, 2.0); !it.isDone(); it.next()) {
            double[] c = new double[6];
            if (it.currentSegment(c) != PathIterator.SEG_CLOSE) {
                samples.add(new Point2D.Double(c[0], c[1]));
            }
        }
        for (int i = 0; i < 520; i++) {
            double x = r.nextDouble() * 1920;
            double y = r.nextDouble() * 1080;
            double best = Double.MAX_VALUE;
            for (Point2D.Double p : samples) {
                best = Math.min(best, p.distance(x, y));
            }
            if (best < 95) {
                continue;
            }
            double rad = 30 + r.nextDouble() * 38;
            g.setColor(new Color(0, 0, 0, 70));
            g.fill(new Ellipse2D.Double(x - rad + 8, y - rad + 12, rad * 2, rad * 2));
            g.setColor(new Color(0x1F5A33));
            g.fill(new Ellipse2D.Double(x - rad, y - rad, rad * 2, rad * 2));
            g.setColor(new Color(0x2F7A45));
            g.fill(new Ellipse2D.Double(x - rad * 0.7, y - rad * 0.75, rad * 1.2, rad * 1.2));
            g.setColor(new Color(0x45A15F));
            g.fill(new Ellipse2D.Double(x - rad * 0.4, y - rad * 0.55, rad * 0.6, rad * 0.6));
        }
        int[][] towers = { { 560, 640 }, { 1010, 330 }, { 1180, 640 }, { 300, 330 } };
        for (int[] t : towers) {
            g.setColor(new Color(0, 0, 0, 90));
            g.fill(new Ellipse2D.Double(t[0] - 50, t[1] + 28, 130, 40));
            g.setColor(new Color(0x7B7F86));
            g.fill(new RoundRectangle2D.Double(t[0] - 42, t[1] - 40, 84, 90, 14, 14));
            g.setColor(new Color(0x9AA0A8));
            g.fill(new RoundRectangle2D.Double(t[0] - 52, t[1] - 62, 104, 36, 12, 12));
            g.setColor(new Color(0x5B5F66));
            for (int k = 0; k < 4; k++) {
                g.fill(new Rectangle2D.Double(t[0] - 46 + k * 28, t[1] - 76, 18, 16));
            }
        }
        g.setPaint(new GradientPaint(0, 0, new Color(8, 24, 14, 215), 1000, 0, new Color(8, 24, 14, 0)));
        g.fillRect(0, 0, 1100, 1080);
        g.setPaint(new GradientPaint(0, 880, new Color(8, 24, 14, 0), 0, 1080, new Color(8, 24, 14, 200)));
        g.fillRect(0, 880, 1920, 200);
        g.dispose();
        return im;
    }

    static BufferedImage logoMark() {
        BufferedImage im = img(140, 140);
        Graphics2D g = g(im);
        g.setColor(new Color(0xBFE08A));
        g.fill(new RoundRectangle2D.Float(4, 4, 132, 132, 40, 40));
        g.setColor(new Color(0x1F3D2A));
        g.setFont(mono(54));
        centeredIn(g, "</>", 140, 140);
        g.dispose();
        return im;
    }

    /** Cream sign-in card with a pale-green header band (tabs are drawn on top in code). */
    static BufferedImage loginCard() {
        int w = 1320;
        int h = 1720;
        BufferedImage im = img(w, h);
        Graphics2D g = g(im);
        RoundRectangle2D card = new RoundRectangle2D.Float(6, 6, w - 12, h - 12, 56, 56);
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(new RoundRectangle2D.Float(6, 16, w - 12, h - 12, 56, 56));
        g.setColor(new Color(0xF8F4E6));
        g.fill(card);
        Shape old = g.getClip();
        g.setClip(card);
        g.setColor(new Color(0xE3EBD6));
        g.fillRect(0, 0, w, 140);
        g.setColor(new Color(0xEEE9D8));
        g.fillRect(0, h - 120, w, 120);
        g.setClip(old);
        g.setColor(new Color(0xD8D2BC));
        g.setStroke(new BasicStroke(3));
        g.draw(card);
        g.dispose();
        return im;
    }

    static BufferedImage introBackground() {
        BufferedImage im = background(1);
        Graphics2D g = g(im);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(960, 460), 760f, new float[] { 0f, 1f },
                new Color[] { new Color(46, 196, 182, 70), new Color(46, 196, 182, 0) }));
        g.fillRect(0, 0, 1920, 1080);
        g.dispose();
        return im;
    }

    // ── titles ───────────────────────────────────────────

    static BufferedImage logo() {
        BufferedImage im = img(1100, 300);
        Graphics2D g = g(im);
        g.setFont(sans(150));
        FontMetrics fm = g.getFontMetrics();
        String left = "{ ";
        String mid = "CodeQuest";
        String right = " }";
        int total = fm.stringWidth(left + mid + right);
        float x = (1100 - total) / 2f;
        float y = 175;
        g.setColor(ACCENT);
        g.drawString(left, x, y);
        x += fm.stringWidth(left);
        g.setColor(TEXT);
        g.drawString(mid, x, y);
        x += fm.stringWidth(mid);
        g.setColor(AMBER);
        g.drawString(right, x, y);
        g.setFont(sans(38));
        g.setColor(MUTED);
        centered(g, "learn C by beating the towers", 550, 245);
        g.dispose();
        return im;
    }

    // ── buttons & panels ────────────────────────────────

    static BufferedImage button(String text, int w, int h, boolean hover, Color accent) {
        BufferedImage im = img(w, h);
        Graphics2D g = g(im);
        float arc = h * 0.3f;
        RoundRectangle2D shape = new RoundRectangle2D.Float(5, 5, w - 10, h - 10, arc, arc);
        g.setPaint(new GradientPaint(0, 0, hover ? PANEL_HOVER.brighter() : PANEL_HOVER, 0, h,
                hover ? PANEL_HOVER : PANEL));
        g.fill(shape);
        g.setStroke(new BasicStroke(hover ? 7 : 4));
        g.setColor(hover ? accent : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 150));
        g.draw(shape);
        g.setFont(sans(Math.round(h * 0.34f)));
        g.setColor(hover ? Color.WHITE : TEXT);
        centeredIn(g, text, w, h);
        g.dispose();
        return im;
    }

    // ── how-to-play pages ───────────────────────────────

    // ── gameplay art ────────────────────────────────────

    static double distToPolyline(double x, double y, List<Point2D.Double> pts) {
        double best = Double.MAX_VALUE;
        for (int i = 1; i < pts.size(); i++) {
            Point2D.Double a = pts.get(i - 1);
            Point2D.Double b = pts.get(i);
            best = Math.min(best, Line2D.ptSegDist(a.x, a.y, b.x, b.y, x, y));
        }
        return best;
    }

    static BufferedImage map(int[][] corners, int number) {
        int w = 1672;
        int h = 941;
        double cell = w / 20.0;
        BufferedImage im = img(w, h);
        Graphics2D g = g(im);
        g.setPaint(new GradientPaint(0, 0, new Color(0x1F3B33), 0, h, new Color(0x2B4D3E)));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 255, 255, 10));
        for (int c = 0; c < 20; c++) {
            for (int r = 0; r < 12; r++) {
                if ((c + r) % 2 == 0) {
                    g.fill(new Rectangle2D.Double(c * cell, r * cell, cell, cell));
                }
            }
        }

        List<Point2D.Double> pts = new ArrayList<>();
        for (int[] c : corners) {
            pts.add(new Point2D.Double((c[0] + 0.5) * cell, (c[1] + 0.5) * cell));
        }

        Random rnd = new Random(100 + number);
        for (int c = 0; c < 20; c++) {
            for (int r = 0; r < 11; r++) {
                double cx = (c + 0.5) * cell;
                double cy = (r + 0.5) * cell;
                if (distToPolyline(cx, cy, pts) < cell * 1.9 || rnd.nextInt(3) == 0) {
                    continue;
                }
                double px = cx + (rnd.nextDouble() - 0.5) * cell * 0.3;
                double py = cy + (rnd.nextDouble() - 0.5) * cell * 0.3;
                if (rnd.nextInt(3) == 0) {
                    Color roof = new Color[] { new Color(0x4A6FA5), new Color(0x7A5C99), new Color(0x3E8E8B),
                            new Color(0xA5654A) }[rnd.nextInt(4)];
                    double bw = cell * (1.0 + rnd.nextDouble() * 0.5);
                    double bh = cell * (0.8 + rnd.nextDouble() * 0.4);
                    g.setColor(new Color(0, 0, 0, 70));
                    g.fill(new RoundRectangle2D.Double(px - bw / 2 + 6, py - bh / 2 + 8, bw, bh, 10, 10));
                    g.setColor(roof);
                    g.fill(new RoundRectangle2D.Double(px - bw / 2, py - bh / 2, bw, bh, 10, 10));
                    g.setColor(new Color(255, 255, 255, 60));
                    g.fill(new Rectangle2D.Double(px - bw / 2 + 8, py - bh / 2 + 8, bw - 16, 10));
                } else {
                    double rad = cell * (0.28 + rnd.nextDouble() * 0.12);
                    g.setColor(new Color(0, 0, 0, 60));
                    g.fill(new Ellipse2D.Double(px - rad + 5, py - rad + 7, rad * 2, rad * 2));
                    g.setColor(new Color(0x2F7A4B));
                    g.fill(new Ellipse2D.Double(px - rad, py - rad, rad * 2, rad * 2));
                    g.setColor(new Color(0x3F9A5F));
                    g.fill(new Ellipse2D.Double(px - rad * 0.6, py - rad * 0.7, rad * 1.1, rad * 1.1));
                }
            }
        }

        Path2D.Double route = new Path2D.Double();
        route.moveTo(pts.get(0).x - cell, pts.get(0).y);
        for (Point2D.Double p : pts) {
            route.lineTo(p.x, p.y);
        }
        g.setStroke(new BasicStroke((float) (cell * 0.78), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x2A3050));
        g.draw(route);
        g.setStroke(new BasicStroke((float) (cell * 0.62), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x3B4470));
        g.draw(route);
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] { 26f, 22f }, 0f));
        g.setColor(new Color(255, 255, 255, 90));
        g.draw(route);

        g.setFont(sans(30));
        g.setColor(new Color(255, 255, 255, 120));
        g.drawString("START", 14, (float) (pts.get(0).y - cell * 0.5));
        g.dispose();
        return im;
    }

    static BufferedImage lab() {
        BufferedImage im = img(256, 256);
        Graphics2D g = g(im);
        g.setColor(new Color(0, 0, 0, 80));
        g.fill(new RoundRectangle2D.Float(30, 60, 210, 180, 20, 20));
        g.setColor(new Color(0xE8EEF9));
        g.fill(new RoundRectangle2D.Float(20, 50, 210, 180, 20, 20));
        g.setColor(new Color(0x3B4470));
        g.fill(new Rectangle2D.Float(20, 50, 210, 40));
        g.setColor(AMBER);
        g.setFont(sans(34));
        centered(g, "COMP LAB", 125, 80);
        g.setColor(new Color(0x2B3560));
        g.fill(new RoundRectangle2D.Float(55, 110, 140, 90, 12, 12));
        g.setColor(ACCENT);
        g.setFont(mono(34));
        g.drawString("> _", 70, 160);
        g.dispose();
        return im;
    }

    static BufferedImage avatar() {
        BufferedImage im = img(128, 128);
        Graphics2D g = g(im);
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Ellipse2D.Float(30, 108, 68, 14));
        g.setColor(AMBER);
        g.fill(new RoundRectangle2D.Float(78, 58, 28, 40, 10, 10));
        g.setColor(new Color(0x2F6FE4));
        g.fill(new RoundRectangle2D.Float(36, 56, 56, 52, 18, 18));
        g.setColor(new Color(0x2B2F4A));
        g.fill(new RoundRectangle2D.Float(44, 100, 18, 20, 6, 6));
        g.fill(new RoundRectangle2D.Float(68, 100, 18, 20, 6, 6));
        g.setColor(new Color(0xF2C9A0));
        g.fill(new Ellipse2D.Float(40, 12, 48, 48));
        g.setColor(new Color(0x3A2A20));
        g.fill(new Arc2D.Float(38, 8, 52, 44, 0, 180, Arc2D.CHORD));
        g.setColor(new Color(0x20243A));
        g.fill(new Ellipse2D.Float(54, 34, 6, 8));
        g.fill(new Ellipse2D.Float(70, 34, 6, 8));
        g.dispose();
        return im;
    }

    static BufferedImage tower(String glyph, Color color) {
        BufferedImage im = img(128, 160);
        Graphics2D g = g(im);
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Ellipse2D.Float(18, 140, 92, 16));
        g.setColor(new Color(0x2A3050));
        g.fill(new RoundRectangle2D.Float(28, 50, 72, 100, 14, 14));
        g.setColor(new Color(0x3B4470));
        g.fill(new RoundRectangle2D.Float(28, 50, 72, 30, 14, 14));
        g.setColor(color);
        g.fill(new RoundRectangle2D.Float(16, 14, 96, 54, 18, 18));
        g.setColor(new Color(255, 255, 255, 70));
        g.fill(new RoundRectangle2D.Float(22, 18, 84, 12, 10, 10));
        g.setColor(new Color(0x14182E));
        g.setFont(mono(glyph.length() > 2 ? 26 : 34));
        centered(g, glyph, 64, 52);
        g.dispose();
        return im;
    }

    static BufferedImage projectile() {
        BufferedImage im = img(96, 96);
        Graphics2D g = g(im);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(48, 48), 46f, new float[] { 0f, 1f },
                new Color[] { new Color(255, 120, 110, 255), new Color(229, 72, 77, 0) }));
        g.fill(new Ellipse2D.Float(2, 2, 92, 92));
        g.setColor(RED);
        g.fill(new Ellipse2D.Float(22, 22, 52, 52));
        g.setColor(Color.WHITE);
        g.setFont(sans(40));
        centered(g, "!", 48, 62);
        g.dispose();
        return im;
    }
}
