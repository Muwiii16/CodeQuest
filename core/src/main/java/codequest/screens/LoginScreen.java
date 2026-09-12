package codequest.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import codequest.Main;
import codequest.data.DatabaseManager;

/**
 * Port of LoginPanel. Scene2D's TextField already has placeholder text
 * (setMessageText) and password masking (setPasswordMode) built in, so this
 * doesn't need the manual focus-listener placeholder-swap hack the Swing
 * version used.
 */
public class LoginScreen extends BaseScreen {

    private final DatabaseManager dbManager = new DatabaseManager();

    public LoginScreen(Main game) {
        setBackground("images/loginbg.png");

        float frameWidth = 500f;
        float frameHeight = 730f;
        float frameX = (VIRTUAL_WIDTH - frameWidth) / 2f;
        float frameY = (VIRTUAL_HEIGHT - frameHeight) / 2f;

        Image frame = new Image(new TextureRegionDrawable(new TextureRegion(texture("images/login/atc_brd.png"))));
        placeTopLeft(frame, frameX, VIRTUAL_HEIGHT - frameY - frameHeight, frameWidth, frameHeight);
        stage.addActor(frame);

        float fieldWidth = 340f;
        float fieldHeight = 80f;
        float fieldX = (VIRTUAL_WIDTH - fieldWidth) / 2f;

        TextField.TextFieldStyle fieldStyle = buildFieldStyle();

        TextField userField = new TextField("", fieldStyle);
        userField.setMessageText("Username");
        addField(userField, "images/login/textbox.png", fieldX, frameY + 200f, fieldWidth, fieldHeight);

        TextField passField = new TextField("", fieldStyle);
        passField.setMessageText("Password");
        passField.setPasswordCharacter('•');
        passField.setPasswordMode(true);
        addField(passField, "images/login/textbox.png", fieldX, frameY + 300f, fieldWidth, fieldHeight);

        float btnWidth = 153f;
        float btnHeight = 51f;
        float totalWidth = btnWidth * 2 + 50f;
        float startX = (VIRTUAL_WIDTH - totalWidth) / 2f;

        ImageButton loginBtn = imageButton("images/login/login_btn_def.png", "images/login/login_btn_hover.png");
        placeTopLeft(loginBtn, startX + 20f, 560f, btnWidth, btnHeight);
        loginBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleLogin(game, userField.getText(), passField.getText());
            }
        });
        stage.addActor(loginBtn);

        ImageButton regBtn = imageButton("images/login/reg_btn_def.png", "images/login/reg_btn_hover.png");
        placeTopLeft(regBtn, startX + 190f, 560f, btnWidth, btnHeight);
        regBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleRegister(userField.getText(), passField.getText());
            }
        });
        stage.addActor(regBtn);
    }

    private void addField(TextField field, String bgPath, float x, float yFromTop, float width, float height) {
        Image bg = new Image(new TextureRegionDrawable(new TextureRegion(texture(bgPath))));
        placeTopLeft(bg, x, yFromTop, width, height);
        stage.addActor(bg);

        placeTopLeft(field, x, yFromTop, width, height);
        stage.addActor(field);
    }

    private TextField.TextFieldStyle buildFieldStyle() {
        Texture cursorTexture = solidTexture(Color.WHITE);

        TextField.TextFieldStyle style = new TextField.TextFieldStyle();
        style.font = defaultFont();
        style.fontColor = Color.LIGHT_GRAY;
        style.messageFontColor = new Color(0.6f, 0.6f, 0.6f, 1f);
        style.cursor = new TextureRegionDrawable(new TextureRegion(cursorTexture));
        style.selection = new TextureRegionDrawable(new TextureRegion(cursorTexture));
        return style;
    }

    private void handleLogin(Main game, String user, String pass) {
        if (user == null || user.isEmpty()) {
            showMessage("Please enter your username.");
            return;
        }
        if (pass == null || pass.isEmpty()) {
            showMessage("Please enter your password.");
            return;
        }

        if (dbManager.validateLogin(user, pass)) {
            int unlockedStage = dbManager.getUnlockedStage(user);
            game.setScreen(new MainMenuScreen(game, user, unlockedStage));
        } else {
            showMessage("Invalid credentials. The shadows grow stronger...");
        }
    }

    private void handleRegister(String user, String pass) {
        if (user == null || user.isEmpty()) {
            showMessage("Please enter a username.");
            return;
        }
        if (pass == null || pass.isEmpty()) {
            showMessage("Please enter a password.");
            return;
        }

        if (dbManager.registerUser(user, pass)) {
            showMessage("Registration Successful! You may now log in.");
        } else {
            showMessage("Username already exists. Choose a different name.");
        }
    }

    // Swapped from JOptionPane.showMessageDialog to a console line + on-screen
    // toast label, since Scene2D has no built-in modal dialog for this yet.
    private void showMessage(String message) {
        Gdx.app.log("CodeQuest", message);
        Label label = addCenteredTitle(message, 660f, 1.4f, new Color(1f, 0.6f, 0.6f, 1f));
        label.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(3f),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.removeActor()));
    }
}
