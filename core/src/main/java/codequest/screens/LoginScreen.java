package codequest.screens;

import codequest.Main;
import codequest.data.DatabaseManager;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

/**
 * Sign-in screen: hero text over the forest on the left, a log in / register card
 * on the right. The "I am a" choice only says which door you are knocking on —
 * the account's real role comes from the database, so picking Professor with a
 * student account just fails, and a new professor account needs the invite code
 * from assets/supabase.properties.
 */
public class LoginScreen extends BaseScreen {

    // the card is navy now (the picture kit), so the text is light
    private static final Color INK = new Color(0xFFFFFFFF);
    private static final Color SOFT_INK = new Color(0xA9BCE0FF);
    private static final Color BLUE = new Color(0.12f, 0.42f, 0.86f, 1f);
    private static final Color DARK = new Color(0.12f, 0.17f, 0.35f, 1f);
    private static final Color OK_TEXT = new Color(0.5f, 0.95f, 0.72f, 1f);
    private static final Color ERROR = new Color(1f, 0.58f, 0.52f, 1f);

    private static final float CARD_X = 1130f;
    private static final float CARD_Y = 140f;
    private static final float CARD_W = 660f;
    private static final float CARD_H = 860f;
    private static final float PAD = 40f;

    private final Main game;
    private final DatabaseManager dbManager = new DatabaseManager();
    private final int base;

    private boolean registerMode;
    private boolean professorRole;
    private String statusText = "";
    private boolean statusOk;

    private String typedUser = "";
    private String typedPass = "";
    private String typedCode = "";
    private TextField userField;
    private TextField passField;
    private TextField codeField;
    /** The Google sign-in in progress (the browser is open), or null. */
    private DatabaseManager.GoogleAttempt googleAttempt;
    private final java.util.List<com.badlogic.gdx.scenes.scene2d.Actor> waitingActors = new java.util.ArrayList<>();

    public LoginScreen(Main game) {
        this.game = game;
        setBackground(Art.BG_LOGIN_FOREST);

        Image mark = new Image(new TextureRegionDrawable(new TextureRegion(texture(Art.LOGO_MARK))));
        placeTopLeft(mark, 110, 48, 76, 76);
        stage.addActor(mark);
        outlinedText("CodeQuest", 205, 62, 400, 50, 2.8f, Color.WHITE, Align.left);

        outlinedText("-  LEARN C++. BEAT THE TOWERS.", 110, 262, 700, 34, 1.8f, new Color(0.85f, 0.95f, 0.6f, 1f), Align.left);
        outlinedText("Learn C++ by\nbeating the\ntowers.", 110, 310, 950, 360, 6.6f, Color.WHITE, Align.topLeft);
        outlinedText("The forest is crawling with enemy towers. Learn C++, and take them down.", 110, 700, 640, 90, 2.1f,
                new Color(0.88f, 0.94f, 0.85f, 1f), Align.topLeft).setWrap(true);

        outlinedText("*  ALL SYSTEMS OPERATIONAL      v1.0.0", 110, 1030, 700, 30, 1.5f, new Color(0.85f, 0.95f, 0.65f, 1f),
                Align.left);
        outlinedText("(c) 2026 CodeQuest", 0, 1030, VIRTUAL_WIDTH, 30, 1.5f, new Color(1f, 1f, 1f, 0.85f), Align.center);

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (googleAttempt != null) {
                    if (keycode == Input.Keys.ESCAPE) {
                        cancelGoogle();
                    }
                    return true; // the form is covered while the browser is open
                }
                if (keycode == Input.Keys.ENTER) {
                    submit();
                    return true;
                }
                return false;
            }
        });

        base = mark();
        rebuild();
    }

    private void remember() {
        if (userField != null) {
            typedUser = userField.getText();
            typedPass = passField.getText();
            typedCode = codeField != null ? codeField.getText() : typedCode;
        }
    }

    private void rebuild() {
        remember();
        clearFrom(base);
        codeField = null;

        Image card = new Image(UiKit.PANEL.drawable());
        placeTopLeft(card, CARD_X - 14, CARD_Y - 14, CARD_W + 28, CARD_H + 28);
        stage.addActor(card);

        float x = CARD_X + PAD;
        float w = CARD_W - PAD * 2;

        tab("Log in", CARD_X + 30, !registerMode, () -> switchMode(false));
        tab("Register", CARD_X + 190, registerMode, () -> switchMode(true));

        text(">  TOWER ACCESS", x, CARD_Y + 94, w, 28, 1.6f, SOFT_INK, Align.left);
        text(registerMode ? "Join the fight against the towers." : "Ready to take down the towers?", x, CARD_Y + 128,
                w, 100, 2.7f, INK, Align.topLeft).setWrap(true);
        text(registerMode ? "Create an account to start learning C++."
                : "Sign in to keep learning C++ and defeat the next enemy tower.", x, CARD_Y + 238, w, 66, 1.7f,
                SOFT_INK, Align.topLeft).setWrap(true);

        float segW = (w - 10) / 2f;
        float roleY = CARD_Y + 310;
        solidButton("I'm a student", x, roleY, segW, 56, professorRole ? DARK : BLUE, Color.WHITE, () -> switchRole(false));
        solidButton("I'm a professor", x + segW + 10, roleY, segW, 56, professorRole ? BLUE : DARK, Color.WHITE, () -> switchRole(true));

        Color box = new Color(0xFFFFFFFF);
        Color line = new Color(0xB9B49CFF);
        float y = roleY + 82;
        userField = textField("Username", false, x, y, w, 62, box, line, INK, SOFT_INK);
        padForIcon(userField, "user", x, y, 62);
        userField.setText(typedUser);
        y += 76;
        passField = textField("Password", true, x, y, w, 62, box, line, INK, SOFT_INK);
        padForIcon(passField, "lock", x, y, 62);
        passField.setText(typedPass);
        y += 76;
        if (registerMode && professorRole) {
            codeField = textField("Professor access code", true, x, y, w, 62, box, line, INK, SOFT_INK);
            padForIcon(codeField, "lock_small", x, y, 62);
            codeField.setText(typedCode);
            y += 76;
        }

        Image mainButton = solidButton(registerMode ? "Create account  >" : "Log in  >", x, y + 6, w, 68, new Color(0.1f, 0.74f, 0.64f, 1f), this::submit);
        rideAlong(mainButton, iconOn(registerMode ? "pencil" : "login", x + 22, y + 6, 68));
        y += 90;
        Image google = solidButton("", x, y, w, 56, new Color(0.06f, 0.12f, 0.28f, 1f), this::handleGoogle);
        Image googleIcon = iconOn("google", x + 18, y, 56);
        // the words go to the right of the G, so the two never overlap
        Label googleLabel = text("Continue with school Google account", x + 84, y, w - 100, 56, 1.3f, Color.WHITE, Align.center);
        googleLabel.setTouchable(Touchable.disabled);
        rideAlong(google, googleIcon, googleLabel);

        if (!statusText.isEmpty()) {
            Label status = text(statusText, x, y + 70, w, 56, 1.4f, statusOk ? OK_TEXT : ERROR, Align.topLeft);
            status.setWrap(true);
        }
    }

    private void tab(String label, float x, boolean selected, Runnable onClick) {
        Image picture = new Image((selected ? UiKit.TAB_ON : UiKit.TAB_OFF).drawable());
        placeTopLeft(picture, x - 14, CARD_Y + 4, 178, 66);
        stage.addActor(picture);
        picture.setTouchable(Touchable.disabled);
        Image hit = rect(x, CARD_Y + 14, 150, 52, new Color(0, 0, 0, 0));
        Label caption = text(label, x, CARD_Y + 14, 150, 52, 1.7f, selected ? INK : SOFT_INK, Align.center);
        caption.setTouchable(Touchable.disabled);
        float captionY = caption.getY();
        hit.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void enter(InputEvent event, float ex, float ey, int pointer, com.badlogic.gdx.scenes.scene2d.Actor from) {
                if (pointer == -1) {
                    caption.clearActions();
                    caption.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.moveTo(caption.getX(), captionY + 6f, 0.07f,
                            com.badlogic.gdx.math.Interpolation.fade));
                    Gdx.graphics.setSystemCursor(com.badlogic.gdx.graphics.Cursor.SystemCursor.Hand);
                }
            }

            @Override
            public void exit(InputEvent event, float ex, float ey, int pointer, com.badlogic.gdx.scenes.scene2d.Actor to) {
                if (pointer == -1) {
                    caption.clearActions();
                    caption.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.moveTo(caption.getX(), captionY, 0.07f,
                            com.badlogic.gdx.math.Interpolation.fade));
                    Gdx.graphics.setSystemCursor(com.badlogic.gdx.graphics.Cursor.SystemCursor.Arrow);
                }
            }

            @Override
            public void clicked(InputEvent event, float cx, float cy) {
                onClick.run();
            }
        });
    }

    /** Moves the typed text to the right of an icon picture drawn at the left of the box. */
    private void padForIcon(TextField field, String icon, float x, float y, float h) {
        com.badlogic.gdx.scenes.scene2d.utils.Drawable bg = field.getStyle().background;
        if (bg instanceof TextureRegionDrawable pad) {
            pad.setLeftWidth(76f);
        }
        iconOn(icon, x + 14, y + 6, h - 12);
    }

    /** An icon picture on top of what was just drawn (it ignores the mouse). */
    private Image iconOn(String name, float x, float yFromTop, float size) {
        Image image = new Image(texture(UiKit.icon(name)));
        placeTopLeft(image, x, yFromTop, size, size);
        image.setTouchable(Touchable.disabled);
        stage.addActor(image);
        return image;
    }

    private void switchMode(boolean register) {
        if (registerMode != register) {
            registerMode = register;
            statusText = "";
            rebuild();
        }
    }

    private void switchRole(boolean professor) {
        if (professorRole != professor) {
            professorRole = professor;
            statusText = "";
            rebuild();
        }
    }

    private void setStatus(String message, boolean ok) {
        statusText = message;
        statusOk = ok;
        rebuild();
    }

    private void submit() {
        remember();
        String user = typedUser.trim();
        String pass = typedPass;
        if (user.isEmpty()) {
            setStatus("Please enter your username.", false);
            return;
        }
        if (pass.isEmpty()) {
            setStatus("Please enter your password.", false);
            return;
        }
        if (registerMode) {
            handleRegister(user, pass);
        } else {
            handleLogin(user, pass);
        }
    }

    private void handleLogin(String user, String pass) {
        load("Signing in...", () -> dbManager.login(user, pass), attempt -> {
            if (attempt.locked()) {
                setStatus("Too many wrong tries. Wait 15 minutes, then try again.", false);
                return;
            }
            var account = attempt.account();
            if (account.isEmpty()) {
                setStatus("Invalid username or password.", false);
                return;
            }
            // The server decides the role; the toggle only has to agree with it.
            boolean isProfessor = account.get().isProfessor();
            if (isProfessor != professorRole) {
                setStatus(isProfessor ? "This is a professor account. Choose \"I'm a professor\"."
                        : "This account does not have professor access. Choose \"I'm a student\".", false);
                return;
            }
            if (isProfessor) {
                game.setScreen(new ProfessorDashboardScreen(game, user));
            } else {
                game.setScreen(new MainMenuScreen(game, user));
            }
        });
    }

    /**
     * Sign in with the @ub.edu.ph Google account. The browser opens; the server decides the role from the address
     * (digits = student, a name = professor), so the student/professor toggle is not used here.
     */
    private void handleGoogle() {
        if (googleAttempt != null) {
            return;
        }
        DatabaseManager.GoogleAttempt attempt = new DatabaseManager.GoogleAttempt();
        googleAttempt = attempt;
        showWaiting();
        Thread worker = new Thread(() -> {
            DatabaseManager.GoogleResult result = dbManager.googleLogin(attempt);
            Gdx.app.postRunnable(() -> {
                if (googleAttempt != attempt) {
                    return; // cancelled, or the screen was left
                }
                googleAttempt = null;
                hideWaiting();
                if (attempt.cancelled()) {
                    return;
                }
                if (result.error() != null) {
                    setStatus(result.error(), false);
                    return;
                }
                if (result.account().isProfessor()) {
                    game.setScreen(new ProfessorDashboardScreen(game, result.username()));
                } else {
                    game.setScreen(new MainMenuScreen(game, result.username()));
                }
            });
        }, "codequest-google-login");
        worker.setDaemon(true);
        worker.start();
    }

    /** The person changed their mind while the browser is open: stop waiting and give the form back. */
    private void cancelGoogle() {
        DatabaseManager.GoogleAttempt attempt = googleAttempt;
        if (attempt == null) {
            return;
        }
        googleAttempt = null;
        attempt.cancel();
        hideWaiting();
        setStatus("Google sign-in cancelled.", true);
    }

    /** A panel over the form while the browser is open, with a Cancel button (Esc does the same). */
    private void showWaiting() {
        int before = stage.getActors().size;
        rect(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, new Color(0f, 0.02f, 0.1f, 0.72f)); // takes every click meanwhile
        Image panel = new Image(UiKit.PANEL.drawable());
        placeTopLeft(panel, 560, 330, 800, 400);
        stage.addActor(panel);
        text("Finish signing in in your browser", 600, 380, 720, 60, 2.2f, Color.WHITE, Align.center);
        text("Pick your @ub.edu.ph account in the browser. Or Cancel to use a password.", 620, 460, 680, 120, 1.4f, SOFT_INK, Align.topLeft,
                PixelFonts.Face.SANS).setWrap(true);
        solidButton("Cancel", 810, 610, 300, 70, new Color(0.8f, 0.2f, 0.25f, 1f), this::cancelGoogle);
        waitingActors.clear();
        for (int i = before; i < stage.getActors().size; i++) {
            waitingActors.add(stage.getActors().get(i));
        }
    }

    private void hideWaiting() {
        waitingActors.forEach(com.badlogic.gdx.scenes.scene2d.Actor::remove);
        waitingActors.clear();
    }

    private void handleRegister(String user, String pass) {
        String role = professorRole ? "professor" : "student";
        String code = professorRole ? typedCode.trim() : null;
        load("Creating your account...", () -> dbManager.register(user, pass, role, code), result -> {
            switch (result) {
                case OK -> {
                    typedPass = "";
                    typedCode = "";
                    registerMode = false;
                    setStatus("Account created. You can log in now.", true);
                }
                case TAKEN -> setStatus("That username is taken. Choose a different name.", false);
                case INVALID -> setStatus("Username: 3+ characters. Password: 4+.", false);
                case BAD_INVITE -> setStatus("Wrong professor access code.", false);
                case PROFESSOR_DISABLED -> setStatus("Professor sign-up is turned off.", false);
                case TOO_MANY -> setStatus("Too many wrong access codes. Wait 15 minutes, then try again.", false);
                default -> setStatus("Could not reach the server. Check your connection.", false);
            }
        });
    }
}
