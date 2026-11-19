package codequest.screens;

import codequest.Main;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

/** The student's main menu: Campaign, Class Test, Cosmetics, Certificates, Dictionary. */
public class MainMenuScreen extends BaseScreen {

    public MainMenuScreen(Main game, String username) {
        setBackground(Art.BG_MENU);

        Image title = new Image(new TextureRegionDrawable(new TextureRegion(texture(Art.LOGO))));
        float titleWidth = 1000f;
        float titleHeight = titleWidth * (title.getDrawable().getMinHeight() / title.getDrawable().getMinWidth());
        placeTopLeft(title, (VIRTUAL_WIDTH - titleWidth) / 2f, 170f - titleHeight / 2f, titleWidth, titleHeight);
        stage.addActor(title);

        float btnWidth = 560f;
        float btnHeight = 120f;
        float btnX = (VIRTUAL_WIDTH - btnWidth) / 2f;
        float startY = 280f;
        float spacing = 138f;

        menuButton("campaign", "Campaign", btnX, startY, btnWidth, btnHeight, () -> game.setScreen(new CampaignScreen(game, username)));
        menuButton("class_test", "Class Test", btnX, startY + spacing, btnWidth, btnHeight,
                () -> game.setScreen(new ClassTestScreen(game, username)));
        menuButton("cosmetics", "Cosmetics", btnX, startY + spacing * 2, btnWidth, btnHeight,
                () -> game.setScreen(new CosmeticsScreen(game, username)));
        menuButton("certificates", "Certificates", btnX, startY + spacing * 3, btnWidth, btnHeight,
                () -> game.setScreen(new CertificatesScreen(game, username)));
        // C is the only language so far; every class teaching it shares this dictionary
        menuButton("dictionary", "Dictionary", btnX, startY + spacing * 4, btnWidth, btnHeight,
                () -> game.setScreen(new DictionaryScreen(game, username, "C")));

        outlinedText("Student:  " + username, VIRTUAL_WIDTH - 620, 28, 380, 40, 1.5f, new Color(0.8f, 0.87f, 1f, 1f), Align.right);
        solidButton("Log out", VIRTUAL_WIDTH - 220, 20, 180, 56, new Color(0.2f, 0.26f, 0.5f, 1f),
                () -> game.setScreen(new LoginScreen(game)));
    }

    /** A flat navy button with the icon on the left and a readable label (the old buttons were pictures with pixel text). */
    private void menuButton(String name, String label, float x, float y, float w, float h, Runnable action) {
        solidButton("", x, y, w, h, new Color(0.16f, 0.22f, 0.46f, 1f), action);
        Image icon = new Image(new TextureRegionDrawable(new TextureRegion(texture("ui/buttons/icon_" + name + ".png"))));
        float size = h - 56f;
        placeTopLeft(icon, x + 34f, y + (h - size) / 2f, size, size);
        icon.setScaling(com.badlogic.gdx.utils.Scaling.fit);
        icon.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        stage.addActor(icon);
        text(label, x + 60f, y, w - 60f, h, 2.7f, Color.WHITE, Align.center).setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
    }
}
