package codequest.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import codequest.Main;
import codequest.data.DatabaseManager;

/** Port of StageSelectPanel — re-fetches unlockedStage from the DB in the
 *  constructor, same as the original did, even though it's also passed in. */
public class StageSelectScreen extends BaseScreen {

    private static final float BTN_WIDTH = 720f;
    private static final float BTN_HEIGHT = 180f;

    public StageSelectScreen(Main game, String username, int unlockedStageHint) {
        setBackground("images/background.png");
        addCenteredTitle("SELECT STAGE", 150f, 6f, Color.WHITE);

        DatabaseManager db = new DatabaseManager();
        int unlockedStage = db.getUnlockedStage(username);
        System.out.println("Unlocked stage for " + username + ": " + unlockedStage);

        float btnX = (VIRTUAL_WIDTH - BTN_WIDTH) / 2f;

        addStageSlot(1, btnX, 200f, unlockedStage >= 1,
                () -> game.setScreen(new DifficultySelectScreen(game, username, 1, unlockedStage)));
        addStageSlot(2, btnX, 400f, unlockedStage >= 2,
                () -> game.setScreen(new DifficultySelectScreen(game, username, 2, unlockedStage)));
        addStageSlot(3, btnX, 600f, unlockedStage >= 3,
                () -> game.setScreen(new DifficultySelectScreen(game, username, 3, unlockedStage)));

        ImageButton backBtn = imageButton("images/buttons/back_btn_def.png", "images/buttons/back_btn_hover.png");
        placeTopLeft(backBtn, btnX, 870f, BTN_WIDTH, BTN_HEIGHT);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MainMenuScreen(game, username, unlockedStage));
            }
        });
        stage.addActor(backBtn);
    }

    private void addStageSlot(int stageNumber, float x, float yFromTop, boolean unlocked, Runnable onSelect) {
        if (unlocked) {
            ImageButton btn = imageButton(
                    "images/stages/buttons/stg" + stageNumber + "_btn_def.png",
                    "images/stages/buttons/stg" + stageNumber + "_btn_hover.png");
            placeTopLeft(btn, x, yFromTop, BTN_WIDTH, BTN_HEIGHT);
            btn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float ex, float ey) {
                    onSelect.run();
                }
            });
            stage.addActor(btn);
        } else {
            Image locked = lockedImage("images/stages/buttons/stg" + stageNumber + "_btn_lck.png");
            placeTopLeft(locked, x, yFromTop, BTN_WIDTH, BTN_HEIGHT);
            stage.addActor(locked);
        }
    }
}
