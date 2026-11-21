package codequest.screens;

import codequest.Main;
import codequest.certificate.CertificateData;
import codequest.certificate.CertificatePdf;
import codequest.data.AwardsData;
import codequest.data.DatabaseManager;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

/**
 * DFD process 8.0 (Issue Certification), student side. A certificate is earned by finishing every lesson of a subject; the
 * server records it in D7 (only after checking the completion), and this screen builds the PDF, shows it and lets the
 * student save it. Nothing is shown if the record is not there. The database work and the PDF are done in the
 * background; only the picture is made on the render thread.
 */
public class CertificateScreen extends BaseScreen {

    /** The finished certificate: file name, PDF bytes and a PNG preview, or null if it could not be made. */
    private record Prepared(String fileName, byte[] pdf, byte[] png) {
    }

    private final DatabaseManager db = new DatabaseManager();
    private Texture preview;
    private Label status;
    private byte[] pdf;
    private String fileName;

    /**
     * The certificate of a finished subject (earned by completing every lesson of it; the server issued it already).
     * {@code back} is what the button underneath does.
     */
    public CertificateScreen(Main game, String username, AwardsData.Subject subject, Runnable back, String backLabel) {
        this(game, username, subject, null, back, backLabel);
    }

    /**
     * The certificate of one grading period of a subject (every lesson of that period completed), or of the whole subject
     * when {@code period} is null (the fancier design).
     */
    public CertificateScreen(Main game, String username, AwardsData.Subject subject, codequest.data.Period period,
            Runnable back, String backLabel) {
        setBackground(Art.BG_MENU);
        addCenteredTitle(period == null ? "YOUR SUBJECT CERTIFICATE" : "YOUR " + period.label().toUpperCase() + " CERTIFICATE", 70f,
                4f, Color.WHITE);
        load("Preparing your certificate...", () -> {
            AwardsData.PeriodAward award = period == null ? null : subject.award(period);
            AwardsData.Certificate cert = period == null ? subject.certificate() : award == null ? null : award.certificate();
            if (cert == null) {
                return null;
            }
            LocalDate issued;
            try {
                issued = LocalDate.parse(cert.issuedAt().substring(0, 10));
            } catch (RuntimeException e) {
                issued = LocalDate.now();
            }
            return prepare(username, subject, period, cert, issued);
        }, prepared -> show(prepared, back, backLabel));
    }

    /** Looks up the student's name, then builds the PDF and its preview image (no graphics calls here). */
    private Prepared prepare(String username, AwardsData.Subject subject, codequest.data.Period period,
            AwardsData.Certificate cert, LocalDate date) {
        try {
            var profile = db.getProfile(username);
            String name = profile.map(p -> p.fullName() != null && !p.fullName().isBlank() ? p.fullName() : p.username())
                    .orElse(username);
            String what = period == null ? "the subject " + subject.name() + " (" + subject.courseCode() + ")"
                    : "the " + period.label() + " period of " + subject.name() + " (" + subject.courseCode() + ")";
            CertificateData data = new CertificateData(name, subject.section(), what, cert.rank(), cert.percent(), date,
                    period == null);
            byte[] pdfBytes = CertificatePdf.generate(data);
            BufferedImage img = CertificatePdf.preview(pdfBytes, 110f);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(img, "png", png);
            String file = "CodeQuest-Certificate-" + username.replaceAll("[^A-Za-z0-9_-]", "_") + "-"
                    + subject.name().replaceAll("[^A-Za-z0-9_-]", "_") + (period == null ? "-Subject" : "-" + period.label())
                    + ".pdf";
            return new Prepared(file, pdfBytes, png.toByteArray());
        } catch (IOException | RuntimeException e) {
            Gdx.app.error("CertificateScreen", "Could not build the certificate PDF", e);
            return null;
        }
    }

    private void show(Prepared prepared, Runnable back, String backLabel) {
        if (prepared == null) {
            addCenteredTitle("Could not make your certificate. Check your connection and try again.", 480f, 1.8f,
                    new Color(1f, 0.6f, 0.55f, 1f));
            solidButton("Back", 760f, 640f, 400f, 80f, new Color(0.3f, 0.1f, 0.1f, 1f), back);
            return;
        }
        pdf = prepared.pdf();
        fileName = prepared.fileName();
        Pixmap pixmap = new Pixmap(prepared.png(), 0, prepared.png().length);
        preview = new Texture(pixmap);
        pixmap.dispose();
        preview.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        Image view = new Image(new TextureRegionDrawable(new TextureRegion(preview)));
        placeTopLeft(view, 80f, 140f, 1200f, 848f);
        stage.addActor(view);

        solidButton("Save PDF", 1360f, 240f, 480f, 90f, new Color(0.12f, 0.3f, 0.12f, 1f), this::savePdf);
        solidButton(backLabel, 1360f, 360f, 480f, 90f, new Color(0.3f, 0.1f, 0.1f, 1f), back);

        status = new Label("", new Label.LabelStyle(PixelFonts.forScale(1.2f, true), new Color(0.8f, 0.9f, 0.7f, 1f)));
        status.setWrap(true);
        status.setAlignment(Align.topLeft);
        placeTopLeft(status, 1360f, 490f, 480f, 200f);
        stage.addActor(status);
    }

    private void savePdf() {
        try {
            FileHandle file = Gdx.files.external("CodeQuest/Certificates/" + fileName);
            file.writeBytes(pdf, false);
            status.setText("Saved to:\n" + file.file().getAbsolutePath());
        } catch (Exception e) {
            Gdx.app.error("CertificateScreen", "Could not save the PDF", e);
            status.setText("Could not save the file.");
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        if (preview != null) {
            preview.dispose();
        }
    }
}
