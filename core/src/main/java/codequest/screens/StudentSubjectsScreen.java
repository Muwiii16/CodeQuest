package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData.ClassInfo;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.List;

/**
 * The student's subjects. A class is one subject taught in one term, and a student can be in several. The ones still
 * running are listed first; the ones the professor marked as ended move to "Past subjects", where every lesson stays
 * playable, so finishing a subject never takes its lessons away.
 */
public class StudentSubjectsScreen extends StudentScreen {

    private static final int ROWS_PER_PAGE = 6;

    /** A line of the list: a heading, or a class. */
    private record Item(String heading, ClassInfo cls) {
    }

    private final List<Item> items = new ArrayList<>();
    private final Long currentId;
    private final int base;
    private int page;

    public StudentSubjectsScreen(Main game, String username, List<ClassInfo> classes, Long currentId) {
        super(game, username, "Your subjects", () -> game.setScreen(new CampaignScreen(game, username, currentId)));
        this.currentId = currentId;
        // newest enrollment first within each group (the list comes oldest first)
        List<ClassInfo> running = new ArrayList<>();
        List<ClassInfo> past = new ArrayList<>();
        for (int i = classes.size() - 1; i >= 0; i--) {
            (classes.get(i).ended() ? past : running).add(classes.get(i));
        }
        if (!running.isEmpty()) {
            items.add(new Item("This semester", null));
            running.forEach(c -> items.add(new Item(null, c)));
        }
        if (!past.isEmpty()) {
            items.add(new Item("Past subjects  -  still open to play", null));
            past.forEach(c -> items.add(new Item(null, c)));
        }
        base = mark();
        rebuild();
    }

    private void rebuild() {
        clearFrom(base);
        int from = page * ROWS_PER_PAGE;
        int to = Math.min(items.size(), from + ROWS_PER_PAGE);
        float y = 150;
        for (int i = from; i < to; i++) {
            Item it = items.get(i);
            if (it.heading() != null) {
                text(it.heading(), 160, y, 1400, 40, 1.9f, GOLD, Align.left);
                y += 52;
                continue;
            }
            ClassInfo c = it.cls();
            boolean current = currentId != null && c.id() == currentId;
            rect(160, y, 1600, 110, ROW);
            text(c.subjectName(), 190, y + 12, 1000, 44, 2.4f, Color.WHITE, Align.left);
            String line = c.sectionName() + "   -   " + c.courseCode() + "   -   " + c.language()
                    + (c.termLabel().isEmpty() ? "" : "   -   " + c.termLabel());
            text(line, 190, y + 62, 1150, 34, 1.4f, MUTED, Align.left);
            if (current) {
                text("Opened now", 1330, y, 200, 110, 1.4f, GOLD, Align.center);
            }
            solidButton(c.ended() ? "Open (ended)" : "Open", 1540, y + 27, 190, 56, GOOD,
                    () -> game.setScreen(new CampaignScreen(game, username, c.id())));
            y += 122;
        }
        if (items.size() > ROWS_PER_PAGE) {
            int pages = (items.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE;
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
