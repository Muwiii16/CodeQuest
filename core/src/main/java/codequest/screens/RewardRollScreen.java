package codequest.screens;

import codequest.Main;
import codequest.data.DatabaseManager;
import codequest.data.DatabaseManager.Cosmetic;
import codequest.data.DatabaseManager.RollOutcome;
import codequest.session.GachaRoll;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

/**
 * The reward reveal after a passed lesson. The roll was already made (and the item unlocked) while the
 * result was saved; this screen only plays it: a shaking orb whose color slowly turns to the rarity of what
 * is inside, a flash, and the card, with more glow and sparks the rarer it is.
 */
public class RewardRollScreen extends BaseScreen {

    private static final float CX = VIRTUAL_WIDTH / 2f;

    private final Main game;
    private final String username;
    private final String lessonTitle;
    private final DatabaseManager db = new DatabaseManager();
    private final long stageId;

    public RewardRollScreen(Main game, String username, RollOutcome outcome, String lessonTitle, long stageId) {
        this.game = game;
        this.stageId = stageId;
        this.username = username;
        this.lessonTitle = lessonTitle;
        setBackground(Art.BG_MENU);
        rect(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, new Color(0f, 0f, 0f, 0.6f)).setTouchable(Touchable.disabled);
        text("LESSON COMPLETE", 0, 70, VIRTUAL_WIDTH, 70, 4f, new Color(0.6f, 0.95f, 0.6f, 1f), Align.center);
        text(lessonTitle, 0, 150, VIRTUAL_WIDTH, 40, 2f, new Color(0.78f, 0.84f, 1f, 1f), Align.center);

        switch (outcome.status()) {
            case WON -> playRoll(outcome);
            case ALREADY_CLAIMED -> {
                text("Reward already claimed", 0, 380, VIRTUAL_WIDTH, 80, 4f, new Color(1f, 0.82f, 0.3f, 1f), Align.center);
                text("A lesson gives one reward roll, the first time you pass it.", 0, 470, VIRTUAL_WIDTH, 40, 1.8f,
                        new Color(0.78f, 0.84f, 1f, 1f), Align.center);
                continueButton(700);
            }
            default -> {
                text("Could not make your reward roll.", 0, 380, VIRTUAL_WIDTH, 80, 3.4f, new Color(1f, 0.6f, 0.55f, 1f),
                        Align.center);
                text("Check your connection and try again.", 0, 470, VIRTUAL_WIDTH, 40, 1.8f, new Color(0.78f, 0.84f, 1f, 1f),
                        Align.center);
                solidButton("Try again", CX - 330, 600, 300, 80, new Color(0.14f, 0.42f, 0.34f, 1f), this::retry);
                solidButton("Skip", CX + 30, 600, 300, 80, new Color(0.16f, 0.22f, 0.46f, 1f), this::finish);
            }
        }
    }

    private void retry() {
        load("Rolling...", () -> db.rollReward(username, stageId),
                outcome -> game.setScreen(new RewardRollScreen(game, username, outcome, lessonTitle, stageId)));
    }

    private void finish() {
        game.setScreen(new CampaignScreen(game, username));
    }

    private void continueButton(float yTop) {
        solidButton("Continue", CX - 220, yTop, 440, 80, new Color(0.16f, 0.22f, 0.46f, 1f), this::finish);
    }

    private void playRoll(RollOutcome outcome) {
        Cosmetic won = outcome.cosmetic();
        Color rarity = CosmeticLook.rarityColor(won.rarity());
        int rank = won.rarityRank();

        // the orb: shakes, grows, and its color turns to the rarity of what is inside
        Image orb = new Image(new TextureRegionDrawable(new TextureRegion(texture("game/glow.png"))));
        orb.setColor(0.75f, 0.8f, 1f, 1f);
        orb.setBounds(CX - 160, VIRTUAL_HEIGHT - 520, 320, 320);
        orb.setOrigin(160, 160);
        orb.setTouchable(Touchable.disabled);
        stage.addActor(orb);
        Label question = text("?", CX - 100, 300, 200, 200, 9f, Color.WHITE, Align.center);

        orb.addAction(Actions.sequence(
                Actions.parallel(
                        Actions.scaleTo(1.4f, 1.4f, 1.5f, Interpolation.pow2In),
                        Actions.color(rarity, 1.5f, Interpolation.pow2In),
                        Actions.repeat(7, Actions.sequence(Actions.moveBy(14, 0, 0.05f), Actions.moveBy(-28, 0, 0.1f),
                                Actions.moveBy(14, 0, 0.05f)))),
                Actions.run(() -> reveal(outcome, rarity, rank, question)),
                Actions.removeActor()));
        question.addAction(Actions.sequence(Actions.delay(1.5f), Actions.removeActor()));
    }

    private void reveal(RollOutcome outcome, Color rarity, int rank, Label question) {
        Cosmetic won = outcome.cosmetic();
        // white flash
        Image flash = rect(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, new Color(1f, 1f, 1f, 0f));
        flash.setTouchable(Touchable.disabled);
        flash.addAction(Actions.sequence(Actions.alpha(0.9f, 0.08f), Actions.alpha(0f, 0.4f), Actions.removeActor()));

        Group card = region();
        card.setOrigin(CX, VIRTUAL_HEIGHT / 2f);
        card.setScale(0.35f);
        card.getColor().a = 0f;
        fill(card, () -> {
            // glow behind the card, bigger and brighter the rarer the item
            float glowSize = 520f + rank * 140f;
            Image glow = new Image(new TextureRegionDrawable(new TextureRegion(texture("game/glow.png"))));
            glow.setColor(rarity.r, rarity.g, rarity.b, 0.35f + 0.18f * rank);
            glow.setBounds(CX - glowSize / 2f, VIRTUAL_HEIGHT - 560 - glowSize / 2f + 200, glowSize, glowSize);
            glow.setTouchable(Touchable.disabled);
            card.addActor(glow);

            rect(CX - 300, 230, 600, 560, new Color(rarity.r * 0.9f, rarity.g * 0.9f, rarity.b * 0.9f, 1f));
            rect(CX - 292, 238, 584, 544, new Color(0.08f, 0.1f, 0.22f, 1f));
            text(outcome.duplicate() ? "DUPLICATE" : "NEW!", CX - 292, 256, 584, 50, 2.6f,
                    outcome.duplicate() ? new Color(0.7f, 0.75f, 0.9f, 1f) : new Color(1f, 0.85f, 0.3f, 1f), Align.center);

            // the item: its real picture (hat and weapon sprites), a trail of glowing dots, or the title in words
            float boxX = CX - 150;
            float boxY = 330;
            float box = 300;
            if ("trail".equals(won.category())) {
                Color dot = CosmeticLook.colorOf(won);
                for (int i = 0; i < 6; i++) {
                    float d = 110 - i * 14;
                    Image trailDot = new Image(new TextureRegionDrawable(new TextureRegion(texture("game/glow.png"))));
                    trailDot.setColor(dot.r, dot.g, dot.b, Math.max(0.3f, 1f - i * 0.14f));
                    trailDot.setBounds(boxX + i * 48, VIRTUAL_HEIGHT - boxY - 190 + i * 24 - d / 2f, d, d);
                    trailDot.setTouchable(Touchable.disabled);
                    card.addActor(trailDot);
                }
            } else if ("hat".equals(won.category()) || "weapon".equals(won.category())) {
                String path = "hat".equals(won.category()) ? CosmeticLook.hatSprite(won) : CosmeticLook.weaponSprite(won);
                com.badlogic.gdx.graphics.Texture t = texture(path);
                float scale = Math.min(box / t.getWidth(), 170f / t.getHeight());
                float w = t.getWidth() * scale;
                float h = t.getHeight() * scale;
                Image item = new Image(new TextureRegionDrawable(new TextureRegion(t)));
                item.setBounds(CX - w / 2f, VIRTUAL_HEIGHT - 400 - h / 2f, w, h);
                item.setTouchable(Touchable.disabled);
                card.addActor(item);
            } else {
                text("\"" + won.name() + "\"", CX - 292, 400, 584, 90, 3.2f, CosmeticLook.colorOf(won), Align.center);
            }
            text(won.name(), CX - 292, 520, 584, 70, 3.6f, Color.WHITE, Align.center);
            text(won.rarity().toUpperCase() + "  " + CosmeticLook.categoryName(won.category()).toUpperCase(), CX - 292, 600,
                    584, 40, 2.2f, rarity, Align.center);
            text(outcome.duplicate() ? "You already had this one." : won.description(), CX - 270, 655, 540, 60, 1.5f,
                    new Color(0.78f, 0.84f, 1f, 1f), Align.center).setWrap(true);
            if (outcome.pity()) {
                text("PITY BONUS: guaranteed rare or better", CX - 292, 725, 584, 40, 1.5f, new Color(1f, 0.82f, 0.3f, 1f),
                        Align.center);
            }
        });
        card.addAction(Actions.parallel(Actions.fadeIn(0.25f), Actions.scaleTo(1f, 1f, 0.5f, Interpolation.swingOut)));

        // sparks circling the card for epic and legendary items
        if (rank >= 2) {
            Group orbit = region();
            orbit.setOrigin(CX, VIRTUAL_HEIGHT - 510);
            int n = rank == 2 ? 8 : 14;
            for (int i = 0; i < n; i++) {
                double ang = i * Math.PI * 2 / n;
                float size = 40f + (i % 3) * 14f;
                Image sp = new Image(new TextureRegionDrawable(new TextureRegion(texture("game/spark.png"))));
                sp.setColor(rarity.r, rarity.g, rarity.b, 0.9f);
                sp.setBounds(CX + (float) Math.cos(ang) * 360 - size / 2f,
                        VIRTUAL_HEIGHT - 510 + (float) Math.sin(ang) * 300 - size / 2f, size, size);
                sp.setOrigin(size / 2f, size / 2f);
                sp.setTouchable(Touchable.disabled);
                sp.addAction(Actions.forever(Actions.sequence(Actions.scaleTo(1.4f, 1.4f, 0.4f + 0.05f * i),
                        Actions.scaleTo(0.6f, 0.6f, 0.4f + 0.05f * i))));
                orbit.addActor(sp);
            }
            orbit.addAction(Actions.forever(Actions.rotateBy(-360f, rank == 2 ? 9f : 6f)));
        }

        // buttons and the odds fade in after the reveal
        Group after = region();
        after.getColor().a = 0f;
        fill(after, () -> {
            solidButton("Equip it now", CX - 470, 830, 440, 80, new Color(0.14f, 0.42f, 0.34f, 1f), () -> equip(won));
            solidButton("Continue", CX + 30, 830, 440, 80, new Color(0.16f, 0.22f, 0.46f, 1f), this::finish);
            text("Odds per roll:   " + GachaRoll.oddsText(), 0, 935, VIRTUAL_WIDTH, 30, 1.3f,
                    new Color(0.6f, 0.66f, 0.85f, 1f), Align.center);
            text(outcome.rollsUntilGuarantee() <= 1 ? "Your next roll is guaranteed to be rare or better!"
                    : "A rare or better item is guaranteed within " + outcome.rollsUntilGuarantee() + " rolls.", 0, 970,
                    VIRTUAL_WIDTH, 30, 1.3f, new Color(1f, 0.82f, 0.3f, 1f), Align.center);
        });
        after.addAction(Actions.sequence(Actions.delay(0.7f), Actions.fadeIn(0.3f)));
    }

    private void equip(Cosmetic won) {
        load("Equipping...", () -> db.equipCosmetic(db.profileId(username), won.category(), won.id()), ok -> {
            toast(ok ? "Equipped " + won.name() + "." : "Could not equip it. You can do it in Cosmetics.", ok);
        });
    }
}
