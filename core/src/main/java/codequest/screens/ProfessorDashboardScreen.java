package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.ProfessorData;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Align;

import java.util.List;

/** Professor home: the sections they handle, and a button to create a new class. */
public class ProfessorDashboardScreen extends ProfessorScreen {

    private static final int PAGE_SIZE = 6;

    private List<ClassInfo> classes = List.of();
    private final int base;
    private int page;

    public ProfessorDashboardScreen(Main game, String username) {
        super(game, username, "My Sections", null);
        solidButton("+  Create class", VIRTUAL_WIDTH - 480, 135, 420, 66, GOOD,
                () -> game.setScreen(new ProfessorClassCreationScreen(game, username)));
        base = mark();
        load("Loading your sections...", () -> {
            ProfessorData data = new ProfessorData();
            return data.listClasses(data.professorId(username));
        }, list -> {
            classes = list;
            rebuild();
        });
    }

    private void rebuild() {
        clearFrom(base);
        text(classes.size() + (classes.size() == 1 ? " section" : " sections"), 60, 140, 600, 56, 1.8f, MUTED, Align.left);
        if (classes.isEmpty()) {
            card(60, 240, VIRTUAL_WIDTH - 120, 220);
            text("You do not handle any sections yet.\nClick \"Create class\" to set up your first one.", 100, 240,
                    VIRTUAL_WIDTH - 200, 220, 2f, MUTED, Align.center);
            return;
        }
        float cardW = 570;
        float cardH = 300;
        float gap = 45;
        float x0 = (VIRTUAL_WIDTH - (cardW * 3 + gap * 2)) / 2f;
        int from = page * PAGE_SIZE;
        int to = Math.min(classes.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            ClassInfo c = classes.get(i);
            int slot = i - from;
            float x = x0 + (slot % 3) * (cardW + gap);
            float y = 240 + (slot / 3) * (cardH + gap);
            solidButton("", x, y, cardW, cardH, CARD, () -> game.setScreen(new ProfessorSectionScreen(game, username, c)));
            rect(x + 3, y + 3, cardW - 6, 8, AMBER).setTouchable(Touchable.disabled);
            text(c.sectionName(), x + 40, y + 50, cardW - 80, 70, 3.2f, Color.WHITE, Align.left);
            text(c.courseCode() + "   -   " + c.language(), x + 40, y + 135, cardW - 80, 40, 1.8f, AMBER, Align.left);
            text(c.studentCount() + (c.studentCount() == 1 ? " student" : " students") + "   -   " + c.lessonCount()
                    + (c.lessonCount() == 1 ? " lesson" : " lessons"), x + 40, y + 190, cardW - 80, 40, 1.6f, MUTED,
                    Align.left);
            text("Open  >", x + 40, y + 240, cardW - 80, 40, 1.6f, Color.WHITE, Align.left);
        }
        if (classes.size() > PAGE_SIZE) {
            int pages = (classes.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            if (page > 0) {
                solidButton("< Prev", 700, 985, 200, 56, BUTTON, () -> {
                    page--;
                    rebuild();
                });
            }
            text("Page " + (page + 1) + " / " + pages, 920, 985, 200, 56, 1.5f, MUTED, Align.center);
            if (page < pages - 1) {
                solidButton("Next >", 1140, 985, 200, 56, BUTTON, () -> {
                    page++;
                    rebuild();
                });
            }
        }
    }
}
