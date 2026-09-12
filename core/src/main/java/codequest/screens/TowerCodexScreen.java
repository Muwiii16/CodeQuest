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

/**
 * Port of TowerCodexPanel. The original used emoji glyphs (💰⚔⚡🎯✦) in its
 * stat labels — LibGDX's default BitmapFont doesn't carry emoji glyphs, so
 * those are plain text labels here instead.
 */
public class TowerCodexScreen extends BaseScreen {

    private static final String[][] TOWERS = {
            { "Mandirigma", "Cost: 75 Gold", "Damage: 15 DMG", "Atk Speed: Fast (0.5s)", "Range: Medium (3 tiles)",
                    "Target Priority: First",
                    "Shredder: High physical DPS; melts large single health pools.",
                    "images/enemies_towers/mandirigma.png" },
            { "Lantaka Cannon", "Cost: 150 Gold", "Damage: 40 DMG", "Atk Speed: Very Slow (2.0s)",
                    "Range: Short-Medium (2.5 tiles)", "Target Priority: Strongest",
                    "Heavy Splash: Deals damage in 1.5-tile radius. Cannot hit flying units.",
                    "images/enemies_towers/lantaka_cannon.png" },
            { "Bantay-Bantayan", "Cost: 100 Gold", "Damage: 25 DMG", "Atk Speed: Slow (1.2s)",
                    "Range: Long (5 tiles)", "Target Priority: Flying First",
                    "Flak Shot: 2x damage vs Flying units and ignores Evasion traits.",
                    "images/enemies_towers/bantay.png" }
    };

    public TowerCodexScreen(Main game, String username, int unlockedStage) {
        setBackground("images/background.png");
        addCenteredTitle("TOWERS", 130f, 5f, Color.WHITE);

        float cardWidth = 1600f;
        float cardHeight = 220f;
        float cardX = (VIRTUAL_WIDTH - cardWidth) / 2f;
        float startY = 180f;
        float spacing = 240f;

        for (int i = 0; i < TOWERS.length; i++) {
            addCard(TOWERS[i], cardX, startY + spacing * i, cardWidth, cardHeight);
        }

        float btnWidth = 720f;
        float btnHeight = btnWidth * 0.25f;
        ImageButton backBtn = imageButton("images/buttons/back_btn_def.png", "images/buttons/back_btn_hover.png");
        placeTopLeft(backBtn, (VIRTUAL_WIDTH - btnWidth) / 2f, 900f, btnWidth, btnHeight);
        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new CodexScreen(game, username, unlockedStage));
            }
        });
        stage.addActor(backBtn);
    }

    private void addCard(String[] data, float x, float yFromTop, float width, float height) {
        Image cardBg = new Image(new TextureRegionDrawable(new TextureRegion(solidTexture(new Color(0, 0, 0, 160 / 255f)))));
        placeTopLeft(cardBg, x, yFromTop, width, height);
        stage.addActor(cardBg);

        float imgSize = height - 20f;
        Image icon = new Image(new TextureRegionDrawable(new TextureRegion(texture(data[7]))));
        placeTopLeft(icon, x + 20f, yFromTop + 10f, imgSize, imgSize);
        stage.addActor(icon);

        float textX = x + imgSize + 40f;
        float textY = yFromTop + 30f;
        float lineH = 38f;

        addLabel(data[0], textX, textY, width - (textX - x) - 20f, lineH, new Color(80 / 255f, 180 / 255f, 1f, 1f), 1.3f);

        String[] leftStats = { data[1], data[2], data[3], data[4] };
        Color[] leftColors = {
                new Color(1f, 210 / 255f, 80 / 255f, 1f),
                new Color(220 / 255f, 80 / 255f, 80 / 255f, 1f),
                new Color(80 / 255f, 200 / 255f, 220 / 255f, 1f),
                new Color(140 / 255f, 220 / 255f, 140 / 255f, 1f)
        };
        for (int i = 0; i < leftStats.length; i++) {
            addLabel(leftStats[i], textX, textY + lineH + lineH * i, 600f, lineH, leftColors[i], 0.9f);
        }

        float rightX = textX + 620f;
        float rightW = x + width - rightX - 20f;
        addLabel(data[5], rightX, textY + lineH, rightW, lineH, new Color(200 / 255f, 160 / 255f, 1f, 1f), 0.85f);
        addLabel("Special Trait", rightX, textY + lineH + 38f, rightW, lineH, new Color(180 / 255f, 220 / 255f, 140 / 255f, 1f), 1f);
        addLabel(data[6], rightX, textY + lineH * 2 + 38f, rightW, lineH * 3, new Color(200 / 255f, 200 / 255f, 200 / 255f, 1f), 0.8f);
    }

    private void addLabel(String text, float x, float yFromTop, float width, float height, Color color, float scale) {
        Label label = new Label(text, new Label.LabelStyle(defaultFont(), color));
        label.setFontScale(scale);
        label.setWrap(true);
        placeTopLeft(label, x, yFromTop, width, height);
        stage.addActor(label);
    }
}
