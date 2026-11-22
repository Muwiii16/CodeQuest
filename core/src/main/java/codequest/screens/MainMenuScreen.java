package codequest.screens;

import codequest.Main;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

/**
 * The student's main menu: Campaign, Class Test, Cosmetics, Achievements (badges and certificates). (The dictionary opens
 * from a class in the campaign.) Each button is a picture with its own colour, icon and label, in three states.
 */
public class MainMenuScreen extends BaseScreen {

    public MainMenuScreen(Main game, String username) {
        setBackground(Art.BG_MENU);
        // a soft dark veil over the bright campus so the eye goes to the buttons
        rect(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, new Color(0.01f, 0.03f, 0.12f, 0.42f)).setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);

        Image title = new Image(new TextureRegionDrawable(new TextureRegion(texture(Art.LOGO))));
        float titleWidth = 1000f;
        float titleHeight = titleWidth * (title.getDrawable().getMinHeight() / title.getDrawable().getMinWidth());
        placeTopLeft(title, (VIRTUAL_WIDTH - titleWidth) / 2f, 170f - titleHeight / 2f, titleWidth, titleHeight);
        stage.addActor(title);

        // the button pictures are 1000 x 330 (about 3 : 1)
        float btnWidth = 500f;
        float btnHeight = btnWidth * 330f / 1000f;
        float btnX = (VIRTUAL_WIDTH - btnWidth) / 2f;
        float startY = 285f;
        float spacing = 180f;

        menuButton("campaign", btnX, startY, btnWidth, btnHeight, () -> game.setScreen(new CampaignScreen(game, username)));
        menuButton("class_test", btnX, startY + spacing, btnWidth, btnHeight, () -> game.setScreen(new ClassTestScreen(game, username)));
        menuButton("cosmetics", btnX, startY + spacing * 2, btnWidth, btnHeight, () -> game.setScreen(new CosmeticsScreen(game, username)));
        menuButton("achievements", btnX, startY + spacing * 3, btnWidth, btnHeight,
                () -> game.setScreen(new CertificatesScreen(game, username)));

        exitOnEscape();
        // who is signed in: a dark chip with a cyan outline, then the log out button
        Image chip = new Image(UiKit.CHIP.drawable());
        placeTopLeft(chip, VIRTUAL_WIDTH - 670, 12, 430, 74);
        stage.addActor(chip);
        text("Student:  " + username, VIRTUAL_WIDTH - 660, 22, 410, 62, 1.5f, Color.WHITE, Align.center);
        solidButton("Log out", VIRTUAL_WIDTH - 220, 20, 180, 56, new Color(0.12f, 0.42f, 0.86f, 1f),
                () -> {
                    new codequest.data.DatabaseManager().logout();
                    game.setScreen(new LoginScreen(game));
                });
    }

    /** One menu button: the picture of that name at rest, under the mouse and pressed (assets/ui/menu). */
    private void menuButton(String name, float x, float y, float w, float h, Runnable action) {
        stateButton("ui/menu/" + name + "_idle.png", "ui/menu/" + name + "_hover.png", "ui/menu/" + name + "_pressed.png", x, y, w,
                h, action);
    }
}
