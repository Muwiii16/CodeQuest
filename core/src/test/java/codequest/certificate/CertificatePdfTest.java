package codequest.certificate;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CertificatePdfTest {

    private static CertificateData sample(String name) {
        return new CertificateData(name, "BSCS 1-A", "Stage 1", "B", 72.5, LocalDate.of(2026, 10, 16));
    }

    private static String textOf(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void certificateContainsTheRequiredFields() throws Exception {
        String text = textOf(CertificatePdf.generate(sample("Luigi Gonzales")));
        assertTrue(text.contains("Luigi Gonzales"));
        assertTrue(text.contains("Stage 1"));
        assertTrue(text.contains("BSCS 1-A"));
        assertTrue(text.contains("16 October 2026"));
        assertTrue(text.contains("73%"));
        assertTrue(text.contains("CERTIFICATE OF COMPLETION"));
    }

    @Test
    void namesWithCharactersTheFontCannotEncodeDoNotBreakTheExport() throws Exception {
        String text = textOf(CertificatePdf.generate(sample("José 张伟")));
        assertTrue(text.contains("Jos"));
    }

    @Test
    void blankSectionIsLeftOff() throws Exception {
        CertificateData d = new CertificateData("Ana", " ", "Stage 2", "A", 90, LocalDate.of(2026, 10, 16));
        assertFalse(textOf(CertificatePdf.generate(d)).contains("Section:"));
    }

    @Test
    void previewRendersTheFirstPage() throws Exception {
        BufferedImage img = CertificatePdf.preview(CertificatePdf.generate(sample("Ana")), 72f);
        assertTrue(img.getWidth() > img.getHeight(), "certificate is landscape");
    }
}
