package codequest.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import codequest.Main;

/** Port of DifficultySelectPanel — picking a difficulty launches GameScreen. */
public class DifficultySelectScreen extends BaseScreen {

    private static final float BTN_WIDTH = 720f;
    private static final float BTN_HEIGHT = 180f;

    public DifficultySelectScreen(Main game, String username, int stageNumber, int unlockedStage) {
        setBackground("images/background.png");
        addCenteredTitle("SELECT DIFFICULTY", 150f, 6f, Color.WHITE);
        addCenteredTitle("Stage " + stageNumber, 210f, 3f, new Color(0.8f, 0.8f, 0.8f, 1f));

        float btnX = (VIRTUAL_WIDTH - BTN_WIDTH) / 2f;

        addDifficultyButton(game, username, "easy", btnX, 200f, stageNumber);
        addDifficultyButton(game, username, "normal", btnX, 400f, stageNumber);
        addDifficultyButton(game, username, "hard", btnX, 600f, stageNumber);

        ImageButton backBtn = imageButton("images/buttons/back_btn_def.png", "images/buttons/back_btn_hover.png");
        placeTopLeft(backBtn, btnX, 860f, BTN_WIDTH, BTN_HEIGHT);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new StageSelectScreen(game, username, unlockedStage));
            }
        });
        stage.addActor(backBtn);
    }

    private void addDifficultyButton(Main game, String username, String difficulty, float x, float yFromTop, int stageNumber) {
        ImageButton btn = imageButton(
                "images/stages/buttons/" + difficulty + "_btn_def.png",
                "images/stages/buttons/" + difficulty + "_btn_hover.png");
        placeTopLeft(btn, x, yFromTop, BTN_WIDTH, BTN_HEIGHT);
        btn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float ex, float ey) {
                game.setScreen(new GameScreen(game, username, stageNumber, difficulty));
            }
        });
        stage.addActor(btn);
    }
}
