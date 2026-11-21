package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.ProfessorData;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Align;

import java.util.List;
import java.util.Map;

/** Professor home: the sections they handle, and a button to create a new class. */
public class ProfessorDashboardScreen extends ProfessorScreen {

    private static final int PAGE_SIZE = 6;

    private List<ClassInfo> classes = List.of();
    private int pending; // students waiting for approval after typing a join code
    private final int base;
    private int page;

    public ProfessorDashboardScreen(Main game, String username) {
        super(game, username, "My Sections", null);
        solidButton("+  Create class", VIRTUAL_WIDTH - 480, 135, 420, 66, GOOD,
                () -> game.setScreen(new ProfessorClassCreationScreen(game, username)));
        base = mark();
        load("Loading your sections...", () -> {
            ProfessorData data = new ProfessorData();
            return Map.entry(data.listClasses(data.professorId(username)), data.pendingRequests().size());
        }, loaded -> {
            classes = loaded.getKey();
            pending = loaded.getValue();
            rebuild();
        });
    }

    private void rebuild() {
        clearFrom(base);
        text(classes.size() + (classes.size() == 1 ? " section" : " sections"), 60, 140, 600, 56, 1.8f, MUTED, Align.left);
        // the analysis of a whole subject: all the sections that teach it, together and side by side
        solidButton("Subject analysis", 500, 135, 440, 66, new Color(0.2f, 0.42f, 0.78f, 1f),
                () -> game.setScreen(new ProfessorSubjectAnalysisScreen(game, username)));
        // students who typed a join code wait here for the professor's approval
        solidButton(pending == 0 ? "Join requests" : "Join requests  (" + pending + ")", 960, 135, 440, 66,
                pending == 0 ? BUTTON : new Color(0.7f, 0.45f, 0.08f, 1f),
                () -> game.setScreen(new ProfessorJoinRequestsScreen(game, username)));
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
            Color[] tints = { new Color(0.2f, 0.42f, 0.78f, 1f), new Color(0.55f, 0.3f, 0.9f, 1f), new Color(0.2f, 0.75f, 0.6f, 1f) };
            Image card = solidButton("", x, y, cardW, cardH, tints[slot % 3],
                    () -> game.setScreen(new ProfessorSectionScreen(game, username, c)));
            Image stripe = rect(x, y, 0, 0, new Color(0, 0, 0, 0));
            stripe.setTouchable(Touchable.disabled);
            Label name = text(c.sectionName(), x + 40, y + 8, cardW - 100, 58, 2.6f, Color.WHITE, Align.left);
            name.setEllipsis(true);
            Label subject = text(c.subjectName() + (c.termLabel().isEmpty() ? "" : "  -  " + c.termLabel()), x + 40,
                    y + 92, cardW - 80, 40, 1.5f, AMBER, Align.left);
            Label counts = text(c.studentCount() + (c.studentCount() == 1 ? " student" : " students") + "   -   "
                    + c.lessonCount() + (c.lessonCount() == 1 ? " lesson" : " lessons"), x + 40, y + 142, cardW - 80, 40,
                    1.6f, MUTED, Align.left);
            Label open = text((c.ended() ? "ENDED   -   " : "") + "Open  >", x + 40, y + 205, cardW - 80, 40, 1.6f,
                    c.ended() ? new Color(1f, 0.75f, 0.4f, 1f) : Color.WHITE, Align.left);
            rideAlong(card, stripe, name, subject, counts, open);
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
