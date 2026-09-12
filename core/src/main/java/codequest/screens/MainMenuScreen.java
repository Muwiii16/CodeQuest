package codequest.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import codequest.Main;

/** Port of the Swing prototype's MainMenuPanel. */
public class MainMenuScreen extends BaseScreen {

    public MainMenuScreen(Main game, String username, int unlockedStage) {
        setBackground("images/background.png");

        Image title = new Image(new TextureRegionDrawable(new TextureRegion(texture("images/titles/game_title.png"))));
        float titleWidth = 800f;
        float titleHeight = titleWidth * (title.getDrawable().getMinHeight() / title.getDrawable().getMinWidth());
        placeTopLeft(title, (VIRTUAL_WIDTH - titleWidth) / 2f, 150f - titleHeight / 2f, titleWidth, titleHeight);
        stage.addActor(title);

        float btnWidth = 720f;
        float btnHeight = 180f;
        float btnX = (VIRTUAL_WIDTH - btnWidth) / 2f;
        float startY = 330f;
        float spacing = 130f;

        ImageButton startBtn = imageButton("images/buttons/start_btn_def.png", "images/buttons/start_btn_hover.png");
        ImageButton howToBtn = imageButton("images/buttons/htp_btn_def.png", "images/buttons/htp_btn_hover.png");
        ImageButton codexBtn = imageButton("images/buttons/codex_btn_def.png", "images/buttons/codex_btn_hover.png");
        ImageButton ldbBtn = imageButton("images/buttons/ldb_btn_def.png", "images/buttons/ldb_btn_hover.png");
        ImageButton exitBtn = imageButton("images/buttons/exit_btn_def.png", "images/buttons/exit_btn_hover.png");

        placeTopLeft(startBtn, btnX, startY, btnWidth, btnHeight);
        placeTopLeft(howToBtn, btnX, startY + spacing, btnWidth, btnHeight);
        placeTopLeft(codexBtn, btnX, startY + spacing * 2, btnWidth, btnHeight);
        placeTopLeft(ldbBtn, btnX, startY + spacing * 3, btnWidth, btnHeight);
        placeTopLeft(exitBtn, btnX, startY + spacing * 4, btnWidth, btnHeight);

        startBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new StageSelectScreen(game, username, unlockedStage));
            }
        });
        howToBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new HowToPlayScreen(game, username, unlockedStage));
            }
        });
        codexBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new CodexScreen(game, username, unlockedStage));
            }
        });
        ldbBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new LeaderboardScreen(game, username));
            }
        });
        exitBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });

        stage.addActor(startBtn);
        stage.addActor(howToBtn);
        stage.addActor(codexBtn);
        stage.addActor(ldbBtn);
        stage.addActor(exitBtn);
    }
}
