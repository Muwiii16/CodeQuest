package codequest.screens;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import codequest.Main;

/** Port of codex.java — the hub screen linking to the Enemy/Tower codices. */
public class CodexScreen extends BaseScreen {

    public CodexScreen(Main game, String username, int unlockedStage) {
        setBackground("images/background.png");

        Image title = new Image(new TextureRegionDrawable(new TextureRegion(texture("images/titles/codex_title.png"))));
        float titleWidth = 800f;
        float titleHeight = titleWidth * (title.getDrawable().getMinHeight() / title.getDrawable().getMinWidth());
        placeTopLeft(title, (VIRTUAL_WIDTH - titleWidth) / 2f, 110f - titleHeight / 2f, titleWidth, titleHeight);
        stage.addActor(title);

        float bigBtnWidth = 900f;
        float bigBtnHeight = 280f;
        float backBtnWidth = 720f;
        float backBtnHeight = 180f;

        float totalWidth = bigBtnWidth * 2 + 50f;
        float startX = (VIRTUAL_WIDTH - totalWidth) / 2f;

        ImageButton enemiesBtn = imageButton("images/codex/enemies_btn_def.png", "images/codex/enemies_btn_hover.png");
        placeTopLeft(enemiesBtn, startX, 300f, bigBtnWidth, bigBtnHeight);
        enemiesBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new EnemyCodexScreen(game, username, unlockedStage));
            }
        });
        stage.addActor(enemiesBtn);

        ImageButton towersBtn = imageButton("images/codex/towers_btn_def.png", "images/codex/towers_btn_hover.png");
        placeTopLeft(towersBtn, startX + bigBtnWidth + 50f, 300f, bigBtnWidth, bigBtnHeight);
        towersBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new TowerCodexScreen(game, username, unlockedStage));
            }
        });
        stage.addActor(towersBtn);

        ImageButton backBtn = imageButton("images/buttons/back_btn_def.png", "images/buttons/back_btn_hover.png");
        placeTopLeft(backBtn, (VIRTUAL_WIDTH - backBtnWidth) / 2f, 750f, backBtnWidth, backBtnHeight);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MainMenuScreen(game, username, unlockedStage));
            }
        });
        stage.addActor(backBtn);
    }
}
