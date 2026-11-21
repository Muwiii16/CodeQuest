package codequest.screens;

import codequest.Main;
import codequest.data.DatabaseManager;
import codequest.data.DatabaseManager.JoinResult;
import codequest.data.JoinCode;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;

/**
 * A student joins a class by typing the code the professor gave them, so the professor does not have to type every
 * student's username. The server checks the code; every outcome is explained in a message.
 */
public class JoinClassScreen extends StudentScreen {

    private final DatabaseManager db = new DatabaseManager();
    private final TextField codeField;

    public JoinClassScreen(Main game, String username, Runnable back) {
        super(game, username, "Join a class", back);
        card(360, 190, 1200, 700);
        text("Enter your class code", 400, 230, 1120, 56, 2.6f, Color.WHITE, Align.center);
        text("The 6-character code from your professor, like WWU-RJC.", 450, 305, 1020, 90, 1.4f, MUTED, Align.center, PixelFonts.Face.SANS).setWrap(true);
        codeField = textField("CODE", false, 660, 430, 600, 96, PixelFonts.Face.MONO);
        codeField.setAlignment(Align.center);
        codeField.setMaxLength(10);
        codeField.setTextFieldFilter((field, c) -> JoinCode.typable(c));
        codeField.setTextFieldListener((field, c) -> {
            if (c == '\r' || c == '\n') {
                join();
            }
        });
        solidButton("Join class", 660, 570, 600, 80, GOOD, this::join);
        text("Codes never use 0, O, 1 or I.", 450, 690, 1020, 36, 1.2f, MUTED, Align.center,
                PixelFonts.Face.SANS);
        text("You can join more than one class.", 450, 735, 1020, 36, 1.2f, MUTED,
                Align.center, PixelFonts.Face.SANS);
        stage.setKeyboardFocus(codeField);
    }

    private void join() {
        String typed = codeField.getText();
        int count = JoinCode.normalize(typed).length();
        if (count != JoinCode.LENGTH) {
            showError("The code is not complete", count == 0 ? "Type the code your professor gave you first."
                    : "A class code has 6 letters and numbers, and you typed " + count + ". Check it with your professor.");
            return;
        }
        load("Joining the class...", () -> db.joinClass(typed), this::show);
    }

    private void show(JoinResult r) {
        String name = r.subject() != null && !r.subject().isBlank() ? r.subject() + "  (" + r.sectionName() + ")" : r.sectionName();
        Runnable toCampaign = () -> game.setScreen(new CampaignScreen(game, username, r.classId()));
        switch (r.status()) {
            case JOINED -> showNotice("You joined " + name,
                    "Press OK to open the class.", toCampaign);
            case REQUESTED -> showNotice("Request sent to your professor",
                    name + " appears in your campaign once your professor approves.", () -> game.setScreen(new CampaignScreen(game, username)));
            case PENDING -> showNotice("You already asked to join " + name,
                    "Waiting for your professor to approve.",
                    () -> game.setScreen(new CampaignScreen(game, username)));
            case ALREADY_IN -> showNotice("You are already in " + name, "Nothing changed. Press OK to open the class.", toCampaign);
            case INVALID_CODE -> showError("That code does not match a class",
                    "Check the code with your professor. A new code replaces the old one.");
            case ENDED -> showError("This class has ended", "Its professor closed it. Ask them to add you.");
            case NOT_A_STUDENT -> showError("Only students can join a class", "This account is not a student account.");
            case TOO_MANY -> showError("Too many wrong codes", "Wait 15 minutes, then check the code with your professor.");
            case NOT_SIGNED_IN -> showError("Please sign in again", "Your session has ended. Log out and log in again.");
            default -> showError("Could not reach the server", "Check your internet connection and try again.");
        }
    }
}
