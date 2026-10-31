package codequest.screens;

import codequest.Main;
import codequest.data.DatabaseManager;
import codequest.data.DatabaseManager.Cosmetic;
import codequest.session.GachaRoll;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Cosmetics: what the student has won from lesson rewards (hats, floating weapons, trails, titles) and
 * dressing the character with it. Rarer items glow; items not won yet are shown locked.
 */
public class CosmeticsScreen extends StudentScreen {

    private static final String[] CATEGORIES = { "hat", "weapon", "trail", "title" };

    private record Loaded(long studentId, List<Cosmetic> catalog, Set<Long> owned, Map<String, Long> equipped,
            DatabaseManager.RollStats stats) {
    }

    private record Wardrobe(Set<Long> owned, Map<String, Long> equipped) {
    }

    private final DatabaseManager db = new DatabaseManager();
    private final int base;

    private long studentId;
    private List<Cosmetic> catalog = List.of();
    private Set<Long> owned = Set.of();
    private Map<String, Long> equipped = Map.of();
    private DatabaseManager.RollStats stats = new DatabaseManager.RollStats(0, 0);
    private String category = CATEGORIES[0];

    public CosmeticsScreen(Main game, String username) {
        super(game, username, "Cosmetics", () -> game.setScreen(new MainMenuScreen(game, username)));
        base = mark();
        load("Opening your wardrobe...", () -> {
            long id = db.profileId(username);
            CompletableFuture<List<Cosmetic>> cat = CompletableFuture.supplyAsync(db::getCatalog);
            CompletableFuture<DatabaseManager.RollStats> st = CompletableFuture.supplyAsync(() -> db.getRollStats(id));
            Wardrobe w = fetchWardrobe(id);
            return new Loaded(id, cat.join(), w.owned(), w.equipped(), st.join());
        }, l -> {
            studentId = l.studentId();
            catalog = l.catalog();
            owned = l.owned();
            equipped = l.equipped();
            stats = l.stats();
            rebuild();
        });
    }

    private Wardrobe fetchWardrobe(long id) {
        CompletableFuture<Map<String, Long>> eq = CompletableFuture.supplyAsync(() -> db.getEquippedCosmetics(id));
        Set<Long> own = new HashSet<>(db.getOwnedCosmetics(id));
        return new Wardrobe(own, eq.join());
    }

    private Image art(String path, Color color, float x, float yTop, float w, float h) {
        Image image = new Image(new TextureRegionDrawable(new TextureRegion(texture(path))));
        image.setColor(color);
        placeTopLeft(image, x, yTop, w, h);
        image.setTouchable(Touchable.disabled);
        stage.addActor(image);
        return image;
    }

    private void rebuild() {
        clearFrom(base);
        CosmeticLook.Outfit outfit = CosmeticLook.outfit(equipped, catalog);

        // ── character preview: hat, floating weapon with its glow, trail ──
        card(60, 140, 640, 880);
        text("Your character", 90, 160, 580, 40, 2f, TEAL, Align.left);
        rect(150, 700, 460, 18, new Color(0, 0, 0, 0.35f));

        Cosmetic trail = outfit.trail();
        if (trail != null && !trail.isDefault()) {
            Color tc = CosmeticLook.colorOf(trail);
            for (int i = 1; i <= 5 + trail.rarityRank() * 2; i++) {
                Image dot = art("game/glow.png", tc, 340 - i * 38, 600, 70 - i * 5, 70 - i * 5);
                dot.getColor().a = Math.max(0.1f, 0.85f - i * 0.1f);
            }
        }
        art("game/avatar.png", Color.WHITE, 290, 440, 280, 280);

        Cosmetic hat = outfit.hat();
        if (hat != null && !hat.isDefault()) {
            if (hat.rarityRank() >= 1) {
                Color aura = CosmeticLook.rarityColor(hat.rarity());
                float size = 280f + 70f * hat.rarityRank();
                Image glow = art("game/glow.png", new Color(aura.r, aura.g, aura.b, 0.3f + 0.08f * hat.rarityRank()),
                        430 - size / 2f, 470 - size / 2f, size, size);
                glow.addAction(Actions.forever(Actions.sequence(Actions.alpha(0.2f, 1.2f), Actions.alpha(0.5f, 1.2f))));
            }
            art("game/hat.png", CosmeticLook.colorOf(hat), 336, 410, 188, 144);
        }

        Cosmetic weapon = outfit.weapon();
        if (weapon != null) {
            float wx = 570;
            float wy = 450;
            if (weapon.rarityRank() >= 1) {
                Color aura = CosmeticLook.rarityColor(weapon.rarity());
                float size = 200f + 70f * weapon.rarityRank();
                Image glow = art("game/glow.png", new Color(aura.r, aura.g, aura.b, 0.3f + 0.1f * weapon.rarityRank()),
                        wx + 60 - size / 2f, wy - size / 2f, size, size);
                glow.addAction(Actions.forever(Actions.sequence(Actions.alpha(0.2f, 1f), Actions.alpha(0.6f, 1f))));
            }
            Image sword = art("game/weapon.png", CosmeticLook.colorOf(weapon), wx - 30, wy - 40, 180, 68);
            sword.setOrigin(25, 34);
            sword.setRotation(-20f);
            sword.addAction(Actions.forever(Actions.sequence(Actions.moveBy(0, 14, 1.1f), Actions.moveBy(0, -14, 1.1f))));
        }
        text(outfit.hat() != null && !outfit.hat().isDefault() ? "Hat: " + outfit.hat().name() : "Hat: none", 90, 770, 580, 30,
                1.4f, MUTED, Align.center);
        text("Weapon: " + (weapon == null ? "none" : weapon.name()), 90, 805, 580, 30, 1.4f, MUTED, Align.center);
        text("Trail: " + (trail == null ? "none" : trail.name()), 90, 840, 580, 30, 1.4f, MUTED, Align.center);
        Cosmetic title = pickTitle();
        text(title == null ? "" : title.name(), 90, 880, 580, 40, 2f, GOLD, Align.center);

        int gachaTotal = 0;
        int gachaOwned = 0;
        for (Cosmetic c : catalog) {
            if ("gacha".equals(c.unlockType())) {
                gachaTotal++;
                if (owned.contains(c.id())) {
                    gachaOwned++;
                }
            }
        }
        text("Collected " + gachaOwned + " of " + gachaTotal + "   -   " + stats.rolls() + " rolls so far", 90, 935, 580, 30,
                1.25f, MUTED, Align.center);
        text(stats.untilGuarantee() <= 1 ? "Your next roll is guaranteed rare or better!"
                : "Rare or better guaranteed in " + stats.untilGuarantee() + " rolls", 90, 968, 580, 30, 1.25f, GOLD,
                Align.center);

        // ── collection ──
        card(740, 140, 1120, 880);
        float tabW = 250;
        for (int i = 0; i < CATEGORIES.length; i++) {
            String cat = CATEGORIES[i];
            solidButton(CosmeticLook.categoryLabel(cat), 770 + i * (tabW + 14), 160, tabW, 60,
                    cat.equals(category) ? new Color(0.1f, 0.5f, 0.45f, 1f) : BUTTON, () -> {
                        category = cat;
                        rebuild();
                    });
        }

        List<Cosmetic> items = new ArrayList<>();
        for (Cosmetic c : catalog) {
            if (c.category().equals(category)) {
                items.add(c);
            }
        }
        items.sort(Comparator.comparingInt(Cosmetic::rarityRank).thenComparing(Cosmetic::name));

        float y = 245;
        int shown = 0;
        for (Cosmetic c : items) {
            if (shown++ >= 8) {
                break;
            }
            boolean have = owned.contains(c.id()) || c.isDefault();
            boolean on = equipped.get(category) != null ? equipped.get(category) == c.id() : isDefaultOfCategory(c);
            Color rarity = CosmeticLook.rarityColor(c.rarity());
            rect(770, y, 1060, 88, ROW);
            rect(770, y, 8, 88, have ? rarity : new Color(0.3f, 0.32f, 0.42f, 1f));
            Image swatch = rect(795, y + 12, 64, 64, have ? CosmeticLook.colorOf(c) : new Color(0.2f, 0.22f, 0.3f, 1f));
            swatch.setTouchable(Touchable.disabled);
            text(have ? c.name() : "???", 880, y + 8, 520, 38, 2f, have ? Color.WHITE : MUTED, Align.left);
            text(c.rarity().toUpperCase() + (have ? "  -  " + c.description() : "  -  not won yet"), 880, y + 50, 600, 30,
                    1.3f, have ? rarity : MUTED, Align.left);
            if (on) {
                text("Equipped", 1500, y, 300, 88, 1.8f, new Color(0.6f, 0.9f, 0.7f, 1f), Align.center);
            } else if (have) {
                solidButton("Equip", 1540, y + 16, 260, 56, GOOD, () -> equip(c));
            } else {
                text("Locked", 1500, y, 300, 88, 1.6f, MUTED, Align.center);
            }
            y += 98;
        }
        text("Odds per roll:   " + GachaRoll.oddsText(), 770, 975, 1060, 30, 1.25f, MUTED, Align.center);
    }

    private boolean isDefaultOfCategory(Cosmetic c) {
        for (Cosmetic other : catalog) {
            if (other.category().equals(c.category()) && other.isDefault()) {
                return other.id() == c.id();
            }
        }
        return false;
    }

    private Cosmetic pickTitle() {
        Long id = equipped.get("title");
        Cosmetic fallback = null;
        for (Cosmetic c : catalog) {
            if (c.category().equals("title")) {
                if (id != null && c.id() == id) {
                    return c;
                }
                if (fallback == null && c.isDefault()) {
                    fallback = c;
                }
            }
        }
        return fallback;
    }

    private void equip(Cosmetic c) {
        load("Equipping...", () -> {
            if (!db.equipCosmetic(studentId, c.category(), c.id())) {
                throw new IllegalStateException("could not equip");
            }
            return fetchWardrobe(studentId);
        }, w -> {
            owned = w.owned();
            equipped = w.equipped();
            rebuild();
            toast("Equipped " + c.name() + ".", true);
        });
    }
}
