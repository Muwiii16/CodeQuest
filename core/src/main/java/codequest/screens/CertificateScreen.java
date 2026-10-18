package codequest.screens;

import codequest.Main;
import codequest.certificate.CertificateData;
import codequest.certificate.CertificatePdf;
import codequest.data.DatabaseManager;
import codequest.data.Lesson;
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
 * DFD process 8.0 (Issue Certification), student side. After a run it records the
 * certificate in D7, builds the PDF, shows it and lets the student save it; from
 * the Certificates list it shows one that was already earned. Nothing is shown
 * if the record can't be saved. The database work and the PDF are done in the
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

    /** Right after a run: issue (or upgrade) the certificate, then show it. */
    public CertificateScreen(Main game, String username, Lesson lesson, String rank, double accuracy) {
        setBackground(Art.BG_MENU);
        addCenteredTitle("YOUR CERTIFICATE", 70f, 4f, Color.WHITE);
        Runnable back = () -> game.setScreen(new CampaignScreen(game, username));
        load("Preparing your certificate...", () -> {
            var record = db.issueCertificate(username, lesson.stageId(), rank, accuracy * 100);
            if (record.isEmpty()) {
                return null;
            }
            return prepare(username, lesson.number(), lesson.title(), record.get().rank(),
                    record.get().scorePercentage(), LocalDate.now());
        }, prepared -> show(prepared, back, "Back to lessons"));
    }

    /** From the Certificates list: show one the student already earned. */
    public CertificateScreen(Main game, String username, DatabaseManager.CertificateListing listing) {
        setBackground(Art.BG_MENU);
        addCenteredTitle("YOUR CERTIFICATE", 70f, 4f, Color.WHITE);
        Runnable back = () -> game.setScreen(new CertificatesScreen(game, username));
        load("Preparing your certificate...", () -> {
            LocalDate issued;
            try {
                issued = LocalDate.parse(listing.issuedAt().substring(0, 10));
            } catch (RuntimeException e) {
                issued = LocalDate.now();
            }
            return prepare(username, listing.lessonNumber(), listing.lessonTitle(), listing.rank(),
                    listing.scorePercentage(), issued);
        }, prepared -> show(prepared, back, "Back to certificates"));
    }

    /** Looks up the student's name, then builds the PDF and its preview image (no graphics calls here). */
    private Prepared prepare(String username, int lessonNumber, String lessonTitle, String rank, double percent,
            LocalDate date) {
        try {
            var profile = db.getProfile(username);
            String name = profile.map(p -> p.fullName() != null && !p.fullName().isBlank() ? p.fullName() : p.username())
                    .orElse(username);
            String section = profile.map(DatabaseManager.Profile::section).orElse(null);
            CertificateData data = new CertificateData(name, section, "Lesson " + lessonNumber + ": " + lessonTitle, rank,
                    percent, date);
            byte[] pdfBytes = CertificatePdf.generate(data);
            BufferedImage img = CertificatePdf.preview(pdfBytes, 110f);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(img, "png", png);
            String file = "CodeQuest-Certificate-" + username.replaceAll("[^A-Za-z0-9_-]", "_") + "-lesson" + lessonNumber
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

        status = new Label("", new Label.LabelStyle(defaultFont(), new Color(0.8f, 0.9f, 0.7f, 1f)));
        status.setFontScale(1.2f);
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
