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
import java.time.LocalDate;
import java.util.Optional;

/**
 * DFD process 8.0 (Issue Certification), student side. After a run it records the
 * certificate in D7, builds the PDF, shows it and lets the student save it; from
 * the Certificates list it shows one that was already earned. Nothing is shown
 * if the record can't be saved.
 */
public class CertificateScreen extends BaseScreen {

    private final DatabaseManager db = new DatabaseManager();
    private Texture preview;
    private Label status;
    private byte[] pdf;
    private String fileName;

    /** Right after a run: issue (or upgrade) the certificate, then show it. */
    public CertificateScreen(Main game, String username, Lesson lesson, String rank, double accuracy) {
        setBackground(Art.BG_MENU);
        addCenteredTitle("YOUR CERTIFICATE", 70f, 4f, Color.WHITE);
        var record = db.issueCertificate(username, lesson.stageId(), rank, accuracy * 100);
        show(game, username, () -> game.setScreen(new CampaignScreen(game, username)), "Back to lessons",
                lesson.number(), lesson.title(), record.map(DatabaseManager.CertificateRecord::rank).orElse(null),
                record.map(DatabaseManager.CertificateRecord::scorePercentage).orElse(0.0), LocalDate.now());
    }

    /** From the Certificates list: show one the student already earned. */
    public CertificateScreen(Main game, String username, DatabaseManager.CertificateListing listing) {
        setBackground(Art.BG_MENU);
        addCenteredTitle("YOUR CERTIFICATE", 70f, 4f, Color.WHITE);
        LocalDate issued;
        try {
            issued = LocalDate.parse(listing.issuedAt().substring(0, 10));
        } catch (RuntimeException e) {
            issued = LocalDate.now();
        }
        show(game, username, () -> game.setScreen(new CertificatesScreen(game, username)), "Back to certificates",
                listing.lessonNumber(), listing.lessonTitle(), listing.rank(), listing.scorePercentage(), issued);
    }

    private void show(Main game, String username, Runnable back, String backLabel, int lessonNumber, String lessonTitle,
            String rank, double percent, LocalDate date) {
        if (rank == null) {
            addCenteredTitle("Could not record your certificate. Check your connection and try again.",
                    480f, 1.8f, new Color(1f, 0.6f, 0.55f, 1f));
            addButton("Back", 760f, 640f, 400f, 80f, new Color(0.3f, 0.1f, 0.1f, 1f), back);
            return;
        }

        var profile = db.getProfile(username);
        String name = profile.map(p -> p.fullName() != null && !p.fullName().isBlank() ? p.fullName() : p.username())
                .orElse(username);
        String section = profile.map(DatabaseManager.Profile::section).orElse(null);
        String label = "Lesson " + lessonNumber + ": " + lessonTitle;
        CertificateData data = new CertificateData(name, section, label, rank, percent, date);
        fileName = "CodeQuest-Certificate-" + username.replaceAll("[^A-Za-z0-9_-]", "_") + "-lesson" + lessonNumber
                + ".pdf";

        try {
            pdf = CertificatePdf.generate(data);
            BufferedImage img = CertificatePdf.preview(pdf, 110f);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(img, "png", png);
            byte[] bytes = png.toByteArray();
            Pixmap pixmap = new Pixmap(bytes, 0, bytes.length);
            preview = new Texture(pixmap);
            pixmap.dispose();
            preview.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        } catch (Exception e) {
            Gdx.app.error("CertificateScreen", "Could not build the certificate PDF", e);
            addCenteredTitle("Could not build the certificate PDF.", 480f, 1.8f, new Color(1f, 0.6f, 0.55f, 1f));
            addButton("Back", 760f, 640f, 400f, 80f, new Color(0.3f, 0.1f, 0.1f, 1f), back);
            return;
        }

        Image view = new Image(new TextureRegionDrawable(new TextureRegion(preview)));
        placeTopLeft(view, 80f, 140f, 1200f, 848f);
        stage.addActor(view);

        addButton("Save PDF", 1360f, 240f, 480f, 90f, new Color(0.12f, 0.3f, 0.12f, 1f), this::savePdf);
        addButton(backLabel, 1360f, 360f, 480f, 90f, new Color(0.3f, 0.1f, 0.1f, 1f), back);

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

    private void addButton(String text, float x, float yTop, float w, float h, Color color, Runnable action) {
        solidButton(text, x, yTop, w, h, color, action);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (preview != null) {
            preview.dispose();
        }
    }
}
