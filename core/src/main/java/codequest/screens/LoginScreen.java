package codequest.screens;

import codequest.Main;
import codequest.data.DatabaseManager;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
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

    private static final Color INK = new Color(0x1F3D2AFF);
    private static final Color SOFT_INK = new Color(0x6B7A66FF);
    private static final Color GREEN = new Color(0x3C6E3FFF);
    private static final Color PALE = new Color(0xDCE5CCFF);
    private static final Color ERROR = new Color(0xB3382CFF);

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

    public LoginScreen(Main game) {
        this.game = game;
        setBackground(Art.BG_LOGIN_FOREST);

        Image mark = new Image(new TextureRegionDrawable(new TextureRegion(texture(Art.LOGO_MARK))));
        placeTopLeft(mark, 110, 48, 76, 76);
        stage.addActor(mark);
        text("CodeQuest", 205, 62, 400, 50, 2.8f, Color.WHITE, Align.left);

        text("-  LEARN C. BEAT THE TOWERS.", 110, 262, 700, 30, 1.3f, new Color(0.75f, 0.9f, 0.55f, 1f), Align.left);
        text("Learn C by\nbeating the\ntowers.", 110, 310, 950, 360, 6.6f, Color.WHITE, Align.topLeft);
        text("The forest is crawling with enemy towers. Learn C, and take them down.", 110, 700, 640, 90, 2.1f,
                new Color(0.88f, 0.94f, 0.85f, 1f), Align.topLeft).setWrap(true);

        text("*  ALL SYSTEMS OPERATIONAL      v1.0.0", 110, 1030, 700, 24, 1.1f, new Color(0.7f, 0.9f, 0.6f, 1f),
                Align.left);
        text("(c) 2026 CodeQuest", 0, 1030, VIRTUAL_WIDTH, 24, 1.1f, new Color(1f, 1f, 1f, 0.6f), Align.center);

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
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

        Image card = new Image(new TextureRegionDrawable(new TextureRegion(texture(Art.LOGIN_CARD))));
        placeTopLeft(card, CARD_X, CARD_Y, CARD_W, CARD_H);
        stage.addActor(card);

        float x = CARD_X + PAD;
        float w = CARD_W - PAD * 2;

        tab("Log in", CARD_X + 30, !registerMode, () -> switchMode(false));
        tab("Register", CARD_X + 190, registerMode, () -> switchMode(true));

        text(">  TOWER ACCESS", x, CARD_Y + 96, w, 24, 1.2f, SOFT_INK, Align.left);
        text(registerMode ? "Join the fight against the towers." : "Ready to take down the towers?", x, CARD_Y + 128,
                w, 100, 2.7f, INK, Align.topLeft).setWrap(true);
        text(registerMode ? "Create an account to start learning C."
                : "Sign in to keep learning C and defeat the next enemy tower.", x, CARD_Y + 238, w, 56, 1.5f,
                SOFT_INK, Align.topLeft).setWrap(true);

        float segW = (w - 10) / 2f;
        float roleY = CARD_Y + 310;
        solidButton("I'm a student", x, roleY, segW, 56, professorRole ? PALE : GREEN,
                professorRole ? INK : Color.WHITE, () -> switchRole(false));
        solidButton("I'm a professor", x + segW + 10, roleY, segW, 56, professorRole ? GREEN : PALE,
                professorRole ? Color.WHITE : INK, () -> switchRole(true));

        Color box = new Color(0xFFFFFFFF);
        Color line = new Color(0xB9B49CFF);
        float y = roleY + 82;
        userField = textField("Username", false, x, y, w, 62, box, line, INK, SOFT_INK);
        userField.setText(typedUser);
        y += 76;
        passField = textField("Password", true, x, y, w, 62, box, line, INK, SOFT_INK);
        passField.setText(typedPass);
        y += 76;
        if (registerMode && professorRole) {
            codeField = textField("Professor access code", true, x, y, w, 62, box, line, INK, SOFT_INK);
            codeField.setText(typedCode);
            y += 76;
        }

        solidButton(registerMode ? "Create account  >" : "Log in  >", x, y + 6, w, 68, GREEN, this::submit);
        y += 90;
        solidButton("Continue with Google  (coming later)", x, y, w, 56, Color.WHITE, INK,
                () -> setStatus("Google sign-in is coming in a later version.", false));

        if (!statusText.isEmpty()) {
            Label status = text(statusText, x, y + 70, w, 56, 1.4f, statusOk ? GREEN : ERROR, Align.topLeft);
            status.setWrap(true);
        }
    }

    private void tab(String label, float x, boolean selected, Runnable onClick) {
        Image hit = rect(x, CARD_Y + 14, 150, 52, new Color(0, 0, 0, 0));
        hit.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(InputEvent event, float cx, float cy) {
                onClick.run();
            }
        });
        text(label, x, CARD_Y + 14, 150, 52, 1.7f, selected ? INK : SOFT_INK, Align.center);
        if (selected) {
            rect(x + 20, CARD_Y + 64, 110, 4, INK).setTouchable(Touchable.disabled);
        }
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
        load("Signing in...", () -> dbManager.login(user, pass), account -> {
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
                case INVALID -> setStatus("Use a username of at least 3 characters and a password of at least 4.", false);
                case BAD_INVITE -> setStatus("Wrong professor access code.", false);
                case PROFESSOR_DISABLED -> setStatus("Professor sign-up is turned off.", false);
                default -> setStatus("Could not reach the server. Check your connection.", false);
            }
        });
    }
}
