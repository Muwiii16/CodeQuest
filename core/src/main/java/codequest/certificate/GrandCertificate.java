package codequest.certificate;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The certificate for finishing every lesson of a subject: a cream page with a navy and gold double frame and corner
 * ornaments, a navy header band, a gold medallion with the rank and a star seal. (A single grading period gets the plainer
 * certificate in {@link CertificatePdf}.)
 */
final class GrandCertificate {

    private static final float W = PDRectangle.A4.getHeight();
    private static final float H = PDRectangle.A4.getWidth();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private static final float[] GOLD = { 0.72f, 0.53f, 0.10f };
    private static final float[] DEEP_GOLD = { 0.55f, 0.40f, 0.06f };
    private static final float[] NAVY = { 0.09f, 0.13f, 0.30f };
    private static final float[] BRIGHT = { 0.95f, 0.80f, 0.35f };
    private static final float[] PAPER = { 0.995f, 0.97f, 0.90f };
    private static final float[] INK = { 0.12f, 0.12f, 0.12f };

    private GrandCertificate() {
    }

    static byte[] generate(CertificateData d) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(W, H));
            doc.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            PDType1Font italic = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);
            PDType1Font boldItalic = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD_ITALIC);
            float mid = W / 2f;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // cream paper
                fill(cs, PAPER);
                cs.addRect(0, 0, W, H);
                cs.fill();

                // navy and gold double frame
                stroke(cs, NAVY);
                cs.setLineWidth(9f);
                cs.addRect(20, 20, W - 40, H - 40);
                cs.stroke();
                stroke(cs, GOLD);
                cs.setLineWidth(2.5f);
                cs.addRect(33, 33, W - 66, H - 66);
                cs.stroke();
                cs.setLineWidth(0.8f);
                cs.addRect(41, 41, W - 82, H - 82);
                cs.stroke();

                // diamonds in the four corners
                fill(cs, GOLD);
                for (float[] c : new float[][] { { 37, 37 }, { W - 37, 37 }, { 37, H - 37 }, { W - 37, H - 37 } }) {
                    diamond(cs, c[0], c[1], 13);
                }

                // header band
                fill(cs, NAVY);
                cs.addRect(60, H - 108, W - 120, 52);
                cs.fill();
                fill(cs, BRIGHT);
                CertificatePdf.centered(cs, bold, 20, "C O D E Q U E S T", H - 88);

                // title
                fill(cs, NAVY);
                CertificatePdf.centered(cs, bold, 38, "CERTIFICATE OF ACHIEVEMENT", H - 160);
                fill(cs, DEEP_GOLD);
                CertificatePdf.centered(cs, italic, 15, "for completing an entire subject", H - 182);
                ornamentLine(cs, mid, H - 198, 170);

                // the person and what they did
                fill(cs, INK);
                CertificatePdf.centered(cs, italic, 16, "This certifies that", H - 232);
                fill(cs, DEEP_GOLD);
                CertificatePdf.centered(cs, boldItalic, 44, d.studentName(), H - 282);
                stroke(cs, GOLD);
                cs.setLineWidth(1.2f);
                cs.moveTo(mid - 230, H - 295);
                cs.lineTo(mid + 230, H - 295);
                cs.stroke();
                fill(cs, INK);
                CertificatePdf.centered(cs, regular, 18, "has completed " + d.stageTitle() + " of the CodeQuest Campaign", H - 325);
                CertificatePdf.centered(cs, regular, 18, "finishing every lesson, with an overall rank of", H - 348);

                // the rank medallion
                float cy = 150;
                fill(cs, GOLD);
                circle(cs, mid, cy, 56);
                cs.fill();
                fill(cs, PAPER);
                circle(cs, mid, cy, 49);
                cs.fill();
                fill(cs, NAVY);
                circle(cs, mid, cy, 44);
                cs.fill();
                fill(cs, BRIGHT);
                CertificatePdf.centered(cs, bold, 58, d.rank(), cy - 20);

                // a star seal on the right
                float sx = W - 150;
                fill(cs, GOLD);
                star(cs, sx, 120, 52, 40, 20);
                cs.fill();
                fill(cs, DEEP_GOLD);
                circle(cs, sx, 120, 33);
                cs.fill();
                fill(cs, PAPER);
                circle(cs, sx, 120, 29);
                cs.fill();
                fill(cs, NAVY);
                text(cs, bold, 9, "CODEQUEST", sx - 19f, 124);
                text(cs, italic, 8, "Certified", sx - 14f, 111);

                // the details, on the left
                fill(cs, INK);
                text(cs, regular, 15, "Overall accuracy: " + Math.round(d.scorePercentage()) + "%", 90, 150);
                if (d.section() != null && !d.section().isBlank()) {
                    text(cs, regular, 15, "Section: " + d.section(), 90, 128);
                }
                text(cs, regular, 15, "Issued on " + d.issuedOn().format(DATE), 90, 106);

                fill(cs, DEEP_GOLD);
                CertificatePdf.centered(cs, italic, 11,
                        "University of Batangas - College of Information and Communications Technology", 62);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void fill(PDPageContentStream cs, float[] c) throws IOException {
        cs.setNonStrokingColor(c[0], c[1], c[2]);
    }

    private static void stroke(PDPageContentStream cs, float[] c) throws IOException {
        cs.setStrokingColor(c[0], c[1], c[2]);
    }

    private static void diamond(PDPageContentStream cs, float x, float y, float r) throws IOException {
        cs.moveTo(x, y + r);
        cs.lineTo(x + r, y);
        cs.lineTo(x, y - r);
        cs.lineTo(x - r, y);
        cs.closePath();
        cs.fill();
    }

    /** A circle as a path (four curves); the caller fills or strokes it. */
    private static void circle(PDPageContentStream cs, float cx, float cy, float r) throws IOException {
        float k = 0.5523f * r;
        cs.moveTo(cx + r, cy);
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        cs.closePath();
    }

    /** A star with the given number of points as a path; the caller fills it. */
    private static void star(PDPageContentStream cs, float cx, float cy, float outer, float inner, int points)
            throws IOException {
        for (int i = 0; i < points * 2; i++) {
            double angle = Math.PI * i / points - Math.PI / 2;
            float radius = i % 2 == 0 ? outer : inner;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius;
            if (i == 0) {
                cs.moveTo(x, y);
            } else {
                cs.lineTo(x, y);
            }
        }
        cs.closePath();
    }

    /** A thin gold line with a diamond in the middle. */
    private static void ornamentLine(PDPageContentStream cs, float mid, float y, float halfWidth) throws IOException {
        stroke(cs, GOLD);
        cs.setLineWidth(1.2f);
        cs.moveTo(mid - halfWidth, y);
        cs.lineTo(mid - 12, y);
        cs.moveTo(mid + 12, y);
        cs.lineTo(mid + halfWidth, y);
        cs.stroke();
        fill(cs, GOLD);
        diamond(cs, mid, y, 6);
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, String text, float x, float y)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(CertificatePdf.encodable(font, text));
        cs.endText();
    }
}
