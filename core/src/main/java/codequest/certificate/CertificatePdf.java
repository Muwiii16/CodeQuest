package codequest.certificate;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds the downloadable stage completion certificate with Apache PDFBox and
 * renders it back to an image for the in-app view. Pure Java — no LibGDX.
 */
public final class CertificatePdf {

    private static final float PAGE_W = PDRectangle.A4.getHeight();
    private static final float PAGE_H = PDRectangle.A4.getWidth();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private CertificatePdf() {
    }

    public static byte[] generate(CertificateData d) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(PAGE_W, PAGE_H));
            doc.addPage(page);

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            PDType1Font italic = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setStrokingColor(0.55f, 0.42f, 0.10f);
                cs.setLineWidth(4f);
                cs.addRect(24, 24, PAGE_W - 48, PAGE_H - 48);
                cs.stroke();
                cs.setLineWidth(1f);
                cs.addRect(34, 34, PAGE_W - 68, PAGE_H - 68);
                cs.stroke();

                cs.setNonStrokingColor(0.55f, 0.42f, 0.10f);
                centered(cs, bold, 16, "C O D E Q U E S T", 500);

                cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
                centered(cs, bold, 34, "CERTIFICATE OF COMPLETION", 450);
                centered(cs, italic, 16, "This certifies that", 400);
                centered(cs, bold, 40, d.studentName(), 345);
                centered(cs, regular, 18, "has completed " + d.stageTitle()
                        + " of the CodeQuest Campaign with a rank of", 295);

                cs.setNonStrokingColor(0.70f, 0.50f, 0.10f);
                centered(cs, bold, 80, d.rank(), 205);

                cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
                centered(cs, regular, 16, "Overall accuracy: " + Math.round(d.scorePercentage()) + "%", 170);
                if (d.section() != null && !d.section().isBlank()) {
                    centered(cs, regular, 16, "Section: " + d.section(), 145);
                }
                centered(cs, regular, 16, "Issued on " + d.issuedOn().format(DATE), 120);

                centered(cs, italic, 11,
                        "University of Batangas - College of Information and Communications Technology", 62);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Renders page 1 of a generated PDF for the in-app viewer. */
    public static BufferedImage preview(byte[] pdf, float dpi) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFRenderer(doc).renderImageWithDPI(0, dpi);
        }
    }

    private static void centered(PDPageContentStream cs, PDFont font, float size, String text, float y)
            throws IOException {
        String s = encodable(font, text);
        float width = font.getStringWidth(s) / 1000f * size;
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset((PAGE_W - width) / 2f, y);
        cs.showText(s);
        cs.endText();
    }

    /** Standard PDF fonts only cover Latin text; anything else becomes '?' instead of crashing the export. */
    private static String encodable(PDFont font, String text) {
        StringBuilder sb = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (cp < 32) {
                sb.append(' ');
                return;
            }
            String ch = new String(Character.toChars(cp));
            try {
                font.encode(ch);
                sb.append(ch);
            } catch (IllegalArgumentException | IOException e) {
                sb.append('?');
            }
        });
        return sb.toString();
    }
}
