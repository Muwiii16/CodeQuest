package codequest.screens;

import codequest.Main;
import codequest.data.DatabaseManager;
import codequest.data.DatabaseManager.Cosmetic;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Cosmetics: what the student has earned from lesson rewards, and dressing the character with it. */
public class CosmeticsScreen extends StudentScreen {

    private static final String[] CATEGORIES = { "avatar_skin", "trail", "badge", "title" };

    private record Loaded(long studentId, List<Cosmetic> catalog, Set<Long> owned, Map<String, Long> equipped) {
    }

    private record Wardrobe(Set<Long> owned, Map<String, Long> equipped) {
    }

    private final DatabaseManager db = new DatabaseManager();
    private final int base;

    private long studentId;
    private List<Cosmetic> catalog = List.of();
    private Set<Long> owned = Set.of();
    private Map<String, Long> equipped = Map.of();
    private String category = CATEGORIES[0];

    public CosmeticsScreen(Main game, String username) {
        super(game, username, "Cosmetics", () -> game.setScreen(new MainMenuScreen(game, username)));
        base = mark();
        load("Opening your wardrobe...", () -> {
            long id = db.profileId(username);
            CompletableFuture<List<Cosmetic>> cat = CompletableFuture.supplyAsync(db::getCatalog);
            Wardrobe w = fetchWardrobe(id);
            return new Loaded(id, cat.join(), w.owned(), w.equipped());
        }, l -> {
            studentId = l.studentId();
            catalog = l.catalog();
            owned = l.owned();
            equipped = l.equipped();
            rebuild();
        });
    }

    private Wardrobe fetchWardrobe(long id) {
        CompletableFuture<Map<String, Long>> eq = CompletableFuture.supplyAsync(() -> db.getEquippedCosmetics(id));
        Set<Long> own = new HashSet<>(db.getOwnedCosmetics(id));
        return new Wardrobe(own, eq.join());
    }

    /** The cosmetic equipped in a category, falling back to that category's default one. */
    private Cosmetic equippedIn(String cat) {
        Long id = equipped.get(cat);
        Cosmetic fallback = null;
        for (Cosmetic c : catalog) {
            if (c.category().equals(cat)) {
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

    private void rebuild() {
        clearFrom(base);

        // ── character preview ──
        card(60, 140, 640, 880);
        text("Your character", 90, 160, 580, 40, 2f, TEAL, Align.left);
        Cosmetic skin = equippedIn("avatar_skin");
        Cosmetic trail = equippedIn("trail");
        Cosmetic badge = equippedIn("badge");
        Cosmetic title = equippedIn("title");

        rect(150, 640, 460, 18, new Color(0, 0, 0, 0.35f));
        if (trail != null && !trail.isDefault()) {
            for (int i = 1; i <= 5; i++) {
                Image dot = new Image(new TextureRegionDrawable(new TextureRegion(texture("game/projectile.png"))));
                dot.setColor(CosmeticLook.colorOf(trail));
                dot.getColor().a = 0.85f - i * 0.13f;
                placeTopLeft(dot, 340 - i * 50, 530, 60 - i * 7, 60 - i * 7);
                dot.setTouchable(Touchable.disabled);
                stage.addActor(dot);
            }
        }
        Image avatar = new Image(new TextureRegionDrawable(new TextureRegion(texture("game/avatar.png"))));
        if (skin != null && !skin.isDefault()) {
            avatar.setColor(CosmeticLook.colorOf(skin));
        }
        placeTopLeft(avatar, 290, 380, 280, 280);
        stage.addActor(avatar);
        if (badge != null && !badge.isDefault()) {
            Image chip = rect(520, 400, 56, 56, CosmeticLook.colorOf(badge));
            chip.setTouchable(Touchable.disabled);
        }
        text(title == null ? "" : title.name(), 90, 690, 580, 40, 2f, GOLD, Align.center);
        text(badge == null ? "" : "Badge: " + badge.name(), 90, 740, 580, 30, 1.4f, MUTED, Align.center);
        text(trail == null ? "" : "Trail: " + trail.name(), 90, 775, 580, 30, 1.4f, MUTED, Align.center);
        text(skin == null ? "" : "Skin: " + skin.name(), 90, 810, 580, 30, 1.4f, MUTED, Align.center);
        text("Earn more by passing lessons.", 90, 950, 580, 30, 1.3f, MUTED, Align.center);

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

        float y = 250;
        int shown = 0;
        for (Cosmetic c : catalog) {
            if (!c.category().equals(category)) {
                continue;
            }
            if (shown++ >= 7) {
                break;
            }
            boolean have = owned.contains(c.id()) || c.isDefault();
            boolean on = equipped.get(category) != null ? equipped.get(category) == c.id()
                    : c.isDefault() && equippedIn(category) == c;
            rect(770, y, 1060, 100, ROW);
            Image swatch = rect(790, y + 15, 70, 70, c.isDefault() ? new Color(0.8f, 0.82f, 0.9f, 1f) : CosmeticLook.colorOf(c));
            swatch.setTouchable(Touchable.disabled);
            text(c.name(), 890, y + 10, 520, 40, 2.1f, have ? Color.WHITE : MUTED, Align.left);
            text(c.rarity() + "  -  " + c.description(), 890, y + 56, 560, 34, 1.4f, MUTED, Align.left);
            if (on) {
                text("Equipped", 1500, y, 300, 100, 1.8f, new Color(0.6f, 0.9f, 0.7f, 1f), Align.center);
            } else if (have) {
                solidButton("Equip", 1540, y + 22, 260, 56, GOOD, () -> equip(c));
            } else {
                text(c.lessonNumber() > 0 ? "Pass lesson " + c.lessonNumber() : "Locked", 1480, y, 340, 100, 1.6f,
                        MUTED, Align.center);
            }
            y += 112;
        }
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
