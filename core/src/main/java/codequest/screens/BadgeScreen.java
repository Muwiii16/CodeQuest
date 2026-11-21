package codequest.screens;

import codequest.Main;
import codequest.certificate.BadgeData;
import codequest.certificate.BadgeImage;
import codequest.data.AwardsData.Badge;
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

import java.io.IOException;
import java.time.LocalDate;

/**
 * One lesson badge: a picture with the student's name, the lesson, the subject, the rank and the date, which the student can
 * save as an image to show their professor as proof of what they accomplished. The picture is drawn in the background; only
 * the texture is made on the render thread.
 */
public class BadgeScreen extends BaseScreen {

    private record Prepared(String fileName, byte[] png) {
    }

    private final DatabaseManager db = new DatabaseManager();
    private Texture preview;
    private Label status;
    private byte[] png;
    private String fileName;

    public BadgeScreen(Main game, String username, Badge badge) {
        setBackground(Art.BG_MENU);
        addCenteredTitle("YOUR BADGE", 70f, 4f, Color.WHITE);
        Runnable back = () -> game.setScreen(new CertificatesScreen(game, username));
        load("Preparing your badge...", () -> {
            try {
                var profile = db.getProfile(username);
                String name = profile.map(p -> p.fullName() != null && !p.fullName().isBlank() ? p.fullName() : p.username())
                        .orElse(username);
                LocalDate earned;
                try {
                    earned = LocalDate.parse(badge.earnedAt().substring(0, 10));
                } catch (RuntimeException e) {
                    earned = LocalDate.now();
                }
                byte[] bytes = BadgeImage.png(new BadgeData(name, badge.subject(), badge.section(), badge.lessonNumber(),
                        badge.lessonTitle(), badge.period().label(), badge.rank(), badge.gradePercent(), earned));
                String file = "CodeQuest-Badge-" + username.replaceAll("[^A-Za-z0-9_-]", "_") + "-lesson"
                        + badge.lessonNumber() + ".png";
                return new Prepared(file, bytes);
            } catch (IOException | RuntimeException e) {
                Gdx.app.error("BadgeScreen", "Could not draw the badge", e);
                return null;
            }
        }, prepared -> show(prepared, back));
    }

    private void show(Prepared prepared, Runnable back) {
        if (prepared == null) {
            addCenteredTitle("Could not make your badge. Check your connection and try again.", 480f, 1.8f,
                    new Color(1f, 0.6f, 0.55f, 1f));
            solidButton("Back", 760f, 640f, 400f, 80f, new Color(0.3f, 0.1f, 0.1f, 1f), back);
            return;
        }
        png = prepared.png();
        fileName = prepared.fileName();
        Pixmap pixmap = new Pixmap(png, 0, png.length);
        preview = new Texture(pixmap);
        pixmap.dispose();
        preview.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        Image view = new Image(new TextureRegionDrawable(new TextureRegion(preview)));
        placeTopLeft(view, 260f, 140f, 848f, 848f);
        stage.addActor(view);

        solidButton("Save image", 1300f, 300f, 480f, 90f, new Color(0.12f, 0.3f, 0.12f, 1f), this::save);
        solidButton("Back", 1300f, 420f, 480f, 90f, new Color(0.3f, 0.1f, 0.1f, 1f), back);

        status = new Label("", new Label.LabelStyle(PixelFonts.forScale(1.2f, true), new Color(0.8f, 0.9f, 0.7f, 1f)));
        status.setWrap(true);
        status.setAlignment(Align.topLeft);
        placeTopLeft(status, 1300f, 550f, 520f, 200f);
        stage.addActor(status);
    }

    private void save() {
        try {
            FileHandle file = Gdx.files.external("CodeQuest/Badges/" + fileName);
            file.writeBytes(png, false);
            status.setText("Saved to:\n" + file.file().getAbsolutePath());
        } catch (Exception e) {
            Gdx.app.error("BadgeScreen", "Could not save the badge", e);
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
