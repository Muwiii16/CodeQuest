package codequest.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import codequest.Main;

/** Port of EnemyCodexPanel — same emoji-to-plain-text simplification as TowerCodexScreen. */
public class EnemyCodexScreen extends BaseScreen {

    private static final String[][] ENEMIES = {
            { "Kapre", "HP: 250 HP", "Speed: Slow (1.0 tiles/sec)", "Leak Dmg: 5 Lives",
                    "Ability: Anito's Resolve - Immune to slow/stun effects.",
                    "images/enemies_towers/kapre.png" },
            { "Tikbalang", "HP: 85 HP", "Speed: Fast (2.5 tiles/sec)", "Leak Dmg: 2 Lives",
                    "Ability: Swift Shadow - 30% chance to dodge single-target projectiles.",
                    "images/enemies_towers/tikbalang.png" },
            { "Manananggal", "HP: 45 HP", "Speed: Normal (1.5 tiles/sec)", "Leak Dmg: 3 Lives",
                    "Ability: Skybound - Flying unit. Ignores ground-only attacks.",
                    "images/enemies_towers/manananggal.png" }
    };

    public EnemyCodexScreen(Main game, String username, int unlockedStage) {
        setBackground("images/background.png");
        addCenteredTitle("ENEMIES", 130f, 5f, Color.WHITE);

        float cardWidth = 1600f;
        float cardHeight = 220f;
        float cardX = (VIRTUAL_WIDTH - cardWidth) / 2f;
        float startY = 180f;
        float spacing = 240f;

        for (int i = 0; i < ENEMIES.length; i++) {
            addCard(ENEMIES[i], cardX, startY + spacing * i, cardWidth, cardHeight);
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
        Image icon = new Image(new TextureRegionDrawable(new TextureRegion(texture(data[5]))));
        placeTopLeft(icon, x + 20f, yFromTop + 10f, imgSize, imgSize);
        stage.addActor(icon);

        float textX = x + imgSize + 40f;
        float textY = yFromTop + 30f;
        float lineH = 38f;

        addLabel(data[0], textX, textY, width - (textX - x) - 20f, lineH, new Color(1f, 210 / 255f, 80 / 255f, 1f), 1.3f);

        String[] stats = { data[1], data[2], data[3], data[4] };
        Color[] colors = {
                new Color(220 / 255f, 80 / 255f, 80 / 255f, 1f),
                new Color(80 / 255f, 200 / 255f, 220 / 255f, 1f),
                new Color(220 / 255f, 220 / 255f, 80 / 255f, 1f),
                new Color(180 / 255f, 220 / 255f, 140 / 255f, 1f)
        };
        for (int i = 0; i < stats.length; i++) {
            addLabel(stats[i], textX, textY + lineH + lineH * i, width - (textX - x) - 20f, lineH, colors[i], 0.9f);
        }
    }

    private void addLabel(String text, float x, float yFromTop, float width, float height, Color color, float scale) {
        Label label = new Label(text, new Label.LabelStyle(defaultFont(), color));
        label.setFontScale(scale);
        label.setWrap(true);
        placeTopLeft(label, x, yFromTop, width, height);
        stage.addActor(label);
    }
}
