package codequest.screens;

import codequest.Main;
import codequest.data.DatabaseManager;
import codequest.data.DatabaseManager.Cosmetic;
import codequest.session.GachaRoll;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

    /**
     * The real picture of a cosmetic in a framed square: the hat or weapon sprite, a row of glowing dots in the trail's colour,
     * or the crown for a title. When the student does not have it yet, the same picture is drawn in solid black (its
     * silhouette), so they can see what is waiting to be won.
     */
    private void cosmeticPicture(Cosmetic c, String category, boolean have, float x, float yFromTop, float size) {
        Color frame = have ? CosmeticLook.rarityColor(c.rarity()) : new Color(0.25f, 0.3f, 0.45f, 1f);
        rect(x, yFromTop, size, size, frame);
        rect(x + 3, yFromTop + 3, size - 6, size - 6, new Color(0.03f, 0.07f, 0.18f, 1f));
        Color tint = have ? Color.WHITE : Color.BLACK;
        float inner = size - 18;
        float cx = x + 9;
        float cy = yFromTop + 9;
        switch (category) {
            case "trail" -> {
                Color dotColor = have ? CosmeticLook.colorOf(c) : Color.BLACK;
                for (int i = 0; i < 5; i++) {
                    float d = inner * (0.34f - i * 0.05f);
                    float px = cx + i * inner * 0.19f;
                    float py = cy + inner * 0.62f - i * inner * 0.12f;
                    Image dot = art("game/glow.png", dotColor, px, py, d, d);
                    dot.getColor().a = have ? Math.max(0.3f, 1f - i * 0.15f) : 1f;
                }
            }
            default -> {
                String path = switch (category) {
                    case "hat" -> c.isDefault() ? UiKit.icon("hat") : CosmeticLook.hatSprite(c);
                    case "weapon" -> CosmeticLook.weaponSprite(c);
                    default -> UiKit.icon("title");
                };
                com.badlogic.gdx.graphics.Texture t = texture(path);
                float scale = Math.min(inner / t.getWidth(), inner / t.getHeight());
                float w = t.getWidth() * scale;
                float h = t.getHeight() * scale;
                art(path, tint, cx + (inner - w) / 2f, cy + (inner - h) / 2f, w, h);
            }
        }
    }

    /** A picture of the kit (a ribbon or a panel strip) drawn behind the text that follows. */
    private void banner(UiKit.Patch patch, float x, float y, float w, float h) {
        Image picture = new Image(patch.drawable());
        placeTopLeft(picture, x, y, w, h);
        stage.addActor(picture);
        picture.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
    }

    private void rebuild() {
        clearFrom(base);
        CosmeticLook.Outfit outfit = CosmeticLook.outfit(equipped, catalog);

        // ── character preview: hat, floating weapon with its glow, trail ──
        card(60, 140, 640, 880);
        // a blue ribbon with the hat icon behind the heading
        banner(UiKit.button("blue", "up"), 150, 172, 460, 64);
        icon("hat", 160, 177, 54);
        text("YOUR CHARACTER", 215, 172, 380, 64, 1.55f, Color.WHITE, Align.center);
        rect(150, 700, 460, 18, new Color(0, 0, 0, 0.35f));

        Cosmetic trail = outfit.trail();
        if (trail != null && !trail.isDefault()) {
            Color tc = CosmeticLook.colorOf(trail);
            for (int i = 1; i <= 5 + trail.rarityRank() * 2; i++) {
                Image dot = art("game/glow.png", tc, 300 - i * 38, 600, 70 - i * 5, 70 - i * 5);
                dot.getColor().a = Math.max(0.1f, 0.85f - i * 0.1f);
            }
        }
        // the hero (pixel art, 186 x 329) with the hat on the hairline and the weapon floating beside him
        art("game/avatar.png", Color.WHITE, 280, 336, 200, 354);

        Cosmetic hat = outfit.hat();
        if (hat != null && !hat.isDefault()) {
            if (hat.rarityRank() >= 1) {
                Color aura = CosmeticLook.rarityColor(hat.rarity());
                float size = 280f + 70f * hat.rarityRank();
                Image glow = art("game/glow.png", new Color(aura.r, aura.g, aura.b, 0.3f + 0.08f * hat.rarityRank()),
                        385 - size / 2f, 360 - size / 2f, size, size);
                glow.addAction(Actions.forever(Actions.sequence(Actions.alpha(0.2f, 1.2f), Actions.alpha(0.5f, 1.2f))));
            }
            art(CosmeticLook.hatSprite(hat), Color.WHITE, 302, 289, 167, 140);
        }

        Cosmetic weapon = outfit.weapon();
        if (weapon != null) {
            float wx = 540;
            float wy = 450;
            if (weapon.rarityRank() >= 1) {
                Color aura = CosmeticLook.rarityColor(weapon.rarity());
                float size = 200f + 70f * weapon.rarityRank();
                Image glow = art("game/glow.png", new Color(aura.r, aura.g, aura.b, 0.3f + 0.1f * weapon.rarityRank()),
                        wx + 60 - size / 2f, wy - size / 2f, size, size);
                glow.addAction(Actions.forever(Actions.sequence(Actions.alpha(0.2f, 1f), Actions.alpha(0.6f, 1f))));
            }
            Image sword = art(CosmeticLook.weaponSprite(weapon), Color.WHITE, wx - 30, wy - 40, 178, 65);
            sword.setOrigin(25, 34);
            sword.setRotation(-20f);
            sword.addAction(Actions.forever(Actions.sequence(Actions.moveBy(0, 14, 1.1f), Actions.moveBy(0, -14, 1.1f))));
        }
        // what the hero wears: one panel banner with the three lines
        banner(UiKit.STRIP, 100, 712, 560, 116);
        text(outfit.hat() != null && !outfit.hat().isDefault() ? "Hat: " + outfit.hat().name() : "Hat: none", 120, 734, 520, 24,
                1.1f, Color.WHITE, Align.center);
        text("Weapon: " + (weapon == null ? "none" : weapon.name()), 120, 759, 520, 24, 1.1f, Color.WHITE, Align.center);
        text("Trail: " + (trail == null ? "none" : trail.name()), 120, 784, 520, 24, 1.1f, Color.WHITE, Align.center);
        // the title the student has earned, on a gold banner
        Cosmetic title = pickTitle();
        if (title != null) {
            banner(UiKit.button("orange", "up"), 160, 834, 440, 52);
            icon("title", 174, 836, 48);
            text(title.name(), 220, 834, 360, 52, 1.6f, new Color(0.2f, 0.1f, 0.02f, 1f), Align.center);
        }

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
        banner(UiKit.STRIP, 100, 894, 560, 92);
        text("Collected " + gachaOwned + " of " + gachaTotal + "   -   " + stats.rolls() + " rolls so far", 120, 916, 520, 24,
                1.05f, Color.WHITE, Align.center);
        text(stats.untilGuarantee() <= 1 ? "Your next roll is guaranteed rare or better!"
                : "Rare or better guaranteed in " + stats.untilGuarantee() + " rolls", 120, 941, 520, 24, 1.0f, GOLD,
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
            // owned items get a full outline in the colour of their rarity (grey, blue, purple, gold); locked ones stay plain
            rect(770, y, 1060, 88, have ? rarity : new Color(0.2f, 0.24f, 0.36f, 1f));
            rect(773, y + 3, 1054, 82, ROW);
            rect(770, y, 8, 88, have ? rarity : new Color(0.3f, 0.32f, 0.42f, 1f));
            // the item itself in a small frame; an item not won yet shows as a pure black silhouette
            cosmeticPicture(c, category, have, 788, y + 4, 80);
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
        // the odds on their own dark banner with the dice icon
        rect(790, 964, 1020, 40, new Color(0.02f, 0.06f, 0.18f, 0.95f));
        icon("dice", 800, 962, 44);
        text("Odds per roll:   " + GachaRoll.oddsText(), 850, 966, 950, 36, 1.25f, Color.WHITE, Align.center);
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

    /**
     * Equips at once (the screen changes right away, so nothing dims or goes dark while the server saves it) and writes
     * the change in the background. If the server refuses it, the wardrobe is read again and the screen shows the truth.
     */
    private void equip(Cosmetic c) {
        Map<String, Long> next = new HashMap<>(equipped);
        next.put(c.category(), c.id());
        equipped = next;
        rebuild();
        toast("Equipped " + c.name() + ".", true);
        long student = studentId;
        CompletableFuture.supplyAsync(() -> db.equipCosmetic(student, c.category(), c.id())).thenAccept(ok -> {
            if (ok) {
                return;
            }
            Wardrobe w = fetchWardrobe(student);
            Gdx.app.postRunnable(() -> {
                if (isDisposed()) {
                    return;
                }
                owned = w.owned();
                equipped = w.equipped();
                rebuild();
                toast("Could not equip " + c.name() + ". Check your connection.", false);
            });
        });
    }
}
