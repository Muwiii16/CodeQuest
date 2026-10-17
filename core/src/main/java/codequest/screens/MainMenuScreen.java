package codequest.screens;

import codequest.Main;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

/** The student's main menu: Campaign, Class Test, Cosmetics, Certificates. */
public class MainMenuScreen extends BaseScreen {

    public MainMenuScreen(Main game, String username) {
        setBackground(Art.BG_MENU);

        Image title = new Image(new TextureRegionDrawable(new TextureRegion(texture(Art.LOGO))));
        float titleWidth = 1000f;
        float titleHeight = titleWidth * (title.getDrawable().getMinHeight() / title.getDrawable().getMinWidth());
        placeTopLeft(title, (VIRTUAL_WIDTH - titleWidth) / 2f, 170f - titleHeight / 2f, titleWidth, titleHeight);
        stage.addActor(title);

        float btnWidth = 560f;
        float btnHeight = 140f;
        float btnX = (VIRTUAL_WIDTH - btnWidth) / 2f;
        float startY = 290f;
        float spacing = 165f;

        menuButton("campaign", btnX, startY, btnWidth, btnHeight, () -> game.setScreen(new CampaignScreen(game, username)));
        menuButton("class_test", btnX, startY + spacing, btnWidth, btnHeight,
                () -> game.setScreen(new ClassTestScreen(game, username)));
        menuButton("cosmetics", btnX, startY + spacing * 2, btnWidth, btnHeight,
                () -> game.setScreen(new CosmeticsScreen(game, username)));
        menuButton("certificates", btnX, startY + spacing * 3, btnWidth, btnHeight,
                () -> game.setScreen(new CertificatesScreen(game, username)));

        text("Student:  " + username, VIRTUAL_WIDTH - 620, 28, 380, 40, 1.5f, new Color(0.8f, 0.87f, 1f, 1f), Align.right);
        solidButton("Log out", VIRTUAL_WIDTH - 220, 20, 180, 56, new Color(0.2f, 0.26f, 0.5f, 1f),
                () -> game.setScreen(new LoginScreen(game)));
    }

    private void menuButton(String name, float x, float y, float w, float h, Runnable action) {
        ImageButton button = imageButton(Art.def(name), Art.hover(name));
        placeTopLeft(button, x, y, w, h);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float cx, float cy) {
                action.run();
            }
        });
        stage.addActor(button);
    }
}
