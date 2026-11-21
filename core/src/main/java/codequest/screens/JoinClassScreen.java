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
        text("Your professor gives you a code of 6 letters and numbers, for example WWU-RJC. Capital letters do not matter "
                + "and the dash is optional.", 450, 305, 1020, 90, 1.4f, MUTED, Align.center, PixelFonts.Face.SANS).setWrap(true);
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
        text("Codes never use 0, O, 1 or I, so they cannot be mixed up.", 450, 690, 1020, 36, 1.2f, MUTED, Align.center,
                PixelFonts.Face.SANS);
        text("You can join more than one class, and you stay in a class after it ends.", 450, 735, 1020, 36, 1.2f, MUTED,
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
                    "You can now play this class's lessons and take its class tests. Press OK to open the class.", toCampaign);
            case REQUESTED -> showNotice("Request sent to your professor",
                    "The code is right. " + name + " will appear in your campaign as soon as your professor approves you. "
                            + "Ask them to check their join requests.", () -> game.setScreen(new CampaignScreen(game, username)));
            case PENDING -> showNotice("You already asked to join " + name,
                    "Your professor has not approved it yet. You do not need to type the code again.",
                    () -> game.setScreen(new CampaignScreen(game, username)));
            case ALREADY_IN -> showNotice("You are already in " + name, "Nothing changed. Press OK to open the class.", toCampaign);
            case INVALID_CODE -> showError("That code does not match a class",
                    "Check the 6 letters and numbers with your professor. Codes never use 0, O, 1 or I. If your professor made a "
                            + "new code, the old one no longer works.");
            case ENDED -> showError("This class has ended", "Its professor has closed it, so nobody can join it any more. "
                    + "Ask your professor if you need to be added another way.");
            case NOT_A_STUDENT -> showError("Only students can join a class", "This account is not a student account.");
            case TOO_MANY -> showError("Too many wrong codes", "You typed several codes that do not match a class. Wait 15 minutes "
                    + "and ask your professor to check the code.");
            case NOT_SIGNED_IN -> showError("Please sign in again", "Your session has ended. Log out and log in again.");
            default -> showError("Could not reach the server", "Check your internet connection and try again.");
        }
    }
}
