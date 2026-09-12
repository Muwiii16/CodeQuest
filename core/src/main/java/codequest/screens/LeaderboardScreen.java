package codequest.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import codequest.Main;
import codequest.data.DatabaseManager;

import java.util.List;

/**
 * Port of LeaderboardPanel. The original drew gradient-filled, rounded-rect
 * rows with hover highlighting by hand in Graphics2D; this uses flat tinted
 * rows instead since Scene2D has no built-in gradient drawable — same data,
 * layout and rank colors, simpler row chrome.
 */
public class LeaderboardScreen extends BaseScreen {

    private static final String[] HEADERS = { "RANK", "USERNAME", "SCORE", "BEST STAGE", "DIFFICULTY", "DATE" };
    private static final float[] COL_WIDTHS = { 80, 300, 200, 180, 180, 280 };

    public LeaderboardScreen(Main game, String username) {
        setBackground("images/background.png");

        Image title = new Image(new TextureRegionDrawable(new TextureRegion(texture("images/titles/ldb_title.png"))));
        float titleWidth = 800f;
        float titleHeight = titleWidth * (title.getDrawable().getMinHeight() / title.getDrawable().getMinWidth());
        placeTopLeft(title, (VIRTUAL_WIDTH - titleWidth) / 2f, 120f - titleHeight / 2f, titleWidth, titleHeight);
        stage.addActor(title);

        DatabaseManager dbManager = new DatabaseManager();
        List<String[]> topPlayers = dbManager.getTopLeaderboard();
        System.out.println("Leaderboard loaded, players found: " + topPlayers.size());

        float tableWidth = 1400f;
        float tableX = (VIRTUAL_WIDTH - tableWidth) / 2f;
        float startY = 220f;
        float rowHeight = 60f;

        float[] colX = new float[HEADERS.length];
        float runningX = tableX + 20f;
        for (int i = 0; i < HEADERS.length; i++) {
            colX[i] = runningX;
            runningX += COL_WIDTHS[i];
        }

        addRowBackground(tableX, startY, tableWidth, rowHeight, new Color(50 / 255f, 32 / 255f, 8 / 255f, 1f));
        for (int i = 0; i < HEADERS.length; i++) {
            addCell(HEADERS[i], colX[i], startY, COL_WIDTHS[i], rowHeight, new Color(220 / 255f, 180 / 255f, 60 / 255f, 1f), true);
        }

        for (int i = 0; i < topPlayers.size(); i++) {
            String[] player = topPlayers.get(i);
            String[] rowData = {
                    "#" + (i + 1),
                    player[0],
                    player[1],
                    "Stage " + player[2],
                    capitalize(player[3]),
                    player[4].length() >= 10 ? player[4].substring(0, 10) : player[4]
            };
            Color rankColor = getRankColor(i);
            float rowY = startY + rowHeight + 5f + (rowHeight + 5f) * i;

            addRowBackground(tableX, rowY, tableWidth, rowHeight, new Color(10 / 255f, 8 / 255f, 5 / 255f, 0.85f));
            for (int c = 0; c < rowData.length; c++) {
                Color color = c == 0 ? rankColor : new Color(210 / 255f, 200 / 255f, 180 / 255f, 1f);
                addCell(rowData[c], colX[c], rowY, COL_WIDTHS[c], rowHeight, color, c == 0);
            }
        }

        if (topPlayers.isEmpty()) {
            addCenteredTitle("No scores yet. Be the first to play!", startY + rowHeight + 40f, 1.4f,
                    new Color(180 / 255f, 180 / 255f, 180 / 255f, 1f));
        }

        float btnWidth = 720f;
        float btnHeight = btnWidth * 0.25f;
        ImageButton backBtn = imageButton("images/buttons/back_btn_def.png", "images/buttons/back_btn_hover.png");
        placeTopLeft(backBtn, (VIRTUAL_WIDTH - btnWidth) / 2f, 900f, btnWidth, btnHeight);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                int unlockedStage = dbManager.getUnlockedStage(username);
                game.setScreen(new MainMenuScreen(game, username, unlockedStage));
            }
        });
        stage.addActor(backBtn);
    }

    private void addRowBackground(float x, float yFromTop, float width, float height, Color color) {
        Image bg = new Image(new TextureRegionDrawable(new TextureRegion(solidTexture(color))));
        placeTopLeft(bg, x, yFromTop, width, height);
        stage.addActor(bg);
    }

    private void addCell(String text, float x, float yFromTop, float width, float height, Color color, boolean bold) {
        Label.LabelStyle style = new Label.LabelStyle(defaultFont(), color);
        Label label = new Label(text, style);
        label.setFontScale(bold ? 1.1f : 0.9f);
        label.setAlignment(com.badlogic.gdx.utils.Align.center);
        placeTopLeft(label, x, yFromTop, width, height);
        stage.addActor(label);
    }

    private Color getRankColor(int rank) {
        switch (rank) {
            case 0:
                return new Color(1f, 215 / 255f, 0, 1f);
            case 1:
                return new Color(192 / 255f, 192 / 255f, 192 / 255f, 1f);
            case 2:
                return new Color(205 / 255f, 127 / 255f, 50 / 255f, 1f);
            default:
                return new Color(180 / 255f, 160 / 255f, 120 / 255f, 1f);
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty())
            return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}
