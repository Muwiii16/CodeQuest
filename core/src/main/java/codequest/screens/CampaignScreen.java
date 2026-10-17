package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.CampaignData.Progress;
import codequest.data.DatabaseManager;
import codequest.data.DatabaseManager.Cosmetic;
import codequest.data.Lesson;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Campaign Mode: the lessons of the student's class (or the built-in set when
 * they are not in one), each with a Rewards dropdown. Lessons unlock in order.
 */
public class CampaignScreen extends StudentScreen {

    private static final int PAGE_SIZE = 5;

    private final CampaignData data = new CampaignData();
    private final DatabaseManager db = new DatabaseManager();
    private final long studentId;
    private final List<ClassInfo> classes;
    private final List<Cosmetic> catalog;
    private final Set<Long> owned;
    private final int base;

    private int classIdx;
    private int page;
    private Long expandedStageId;
    private List<Lesson> lessons;
    private Map<Long, Progress> progress;

    public CampaignScreen(Main game, String username) {
        super(game, username, "Campaign", () -> game.setScreen(new MainMenuScreen(game, username)));
        studentId = db.profileId(username);
        classes = data.studentClasses(studentId);
        catalog = db.getCatalog();
        owned = new HashSet<>(db.getOwnedCosmetics(studentId));
        base = mark();
        loadLessons();
        rebuild();
    }

    private void loadLessons() {
        lessons = data.lessons(classes.isEmpty() ? null : classes.get(classIdx).id());
        progress = data.progress(studentId);
        page = 0;
        expandedStageId = null;
    }

    private void rebuild() {
        clearFrom(base);

        String context = classes.isEmpty() ? "Built-in lessons  (you are not in a class yet)"
                : classes.get(classIdx).sectionName() + "  -  " + classes.get(classIdx).language();
        text(context, 160, 128, 1100, 40, 1.8f, MUTED, Align.left);
        if (classes.size() > 1) {
            solidButton("Switch class", 1500, 122, 260, 52, BUTTON, () -> {
                classIdx = (classIdx + 1) % classes.size();
                loadLessons();
                rebuild();
            });
        }

        if (lessons.isEmpty()) {
            card(160, 200, 1600, 140);
            text("Your professor has not added any lessons yet.", 200, 200, 1520, 140, 2f, MUTED, Align.center);
            return;
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(lessons.size(), from + PAGE_SIZE);
        float y = 190;
        for (int i = from; i < to; i++) {
            y = drawLesson(i, y);
        }

        if (lessons.size() > PAGE_SIZE) {
            int pages = (lessons.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            if (page > 0) {
                solidButton("< Prev", 700, 980, 200, 56, BUTTON, () -> {
                    page--;
                    rebuild();
                });
            }
            text("Page " + (page + 1) + " / " + pages, 920, 980, 200, 56, 1.5f, MUTED, Align.center);
            if (page < pages - 1) {
                solidButton("Next >", 1140, 980, 200, 56, BUTTON, () -> {
                    page++;
                    rebuild();
                });
            }
        }
    }

    /** Draws one lesson row (and its rewards strip if expanded); returns the y for the next row. */
    private float drawLesson(int index, float y) {
        Lesson lesson = lessons.get(index);
        Progress p = progress.get(lesson.stageId());
        boolean unlocked = CampaignData.isUnlocked(lessons, progress, index);
        boolean done = p != null && p.completed();

        rect(160, y, 1600, 110, ROW);
        rect(160, y, 8, 110, done ? GOOD : unlocked ? TEAL : new Color(0.35f, 0.38f, 0.5f, 1f));
        text(String.valueOf(lesson.number()), 190, y, 80, 110, 3.4f, unlocked ? Color.WHITE : MUTED, Align.center);
        text(lesson.title(), 300, y + 14, 880, 44, 2.3f, unlocked ? Color.WHITE : MUTED, Align.left);
        String status;
        if (done) {
            status = "Completed  -  Rank " + (p.rank() == null ? "-" : p.rank()) + "  -  best score " + p.score();
        } else if (unlocked) {
            status = "Ready  -  starts at " + lesson.baseDifficulty().toUpperCase();
        } else {
            status = "Locked  -  pass lesson " + (lesson.number() - 1) + " first";
        }
        text(status, 300, y + 64, 880, 34, 1.5f, done ? new Color(0.6f, 0.9f, 0.7f, 1f) : MUTED, Align.left);

        boolean expanded = Long.valueOf(lesson.stageId()).equals(expandedStageId);
        solidButton(expanded ? "Rewards  ^" : "Rewards  v", 1200, y + 27, 230, 56, BUTTON, () -> {
            expandedStageId = expanded ? null : lesson.stageId();
            rebuild();
        });
        if (unlocked) {
            solidButton(done ? "Replay" : "Play", 1460, y + 27, 270, 56, GOOD,
                    () -> game.setScreen(new PlayScreen(game, username, lesson)));
        } else {
            solidButton("Locked", 1460, y + 27, 270, 56, new Color(0.2f, 0.22f, 0.3f, 1f), Color.GRAY, () -> { });
        }

        float next = y + 124;
        if (expanded) {
            rect(160, y + 114, 1600, 76, CARD);
            text(rewardText(lesson), 200, y + 114, 1520, 76, 1.5f, GOLD, Align.left);
            next = y + 204;
        }
        return next;
    }

    private String rewardText(Lesson lesson) {
        StringBuilder sb = new StringBuilder();
        for (Cosmetic c : catalog) {
            if ("stage_completion".equals(c.unlockType()) && c.lessonNumber() == lesson.number()) {
                sb.append(sb.length() == 0 ? "" : "    ").append(c.name()).append("  (")
                        .append(CosmeticLook.categoryLabel(c.category())).append(owned.contains(c.id()) ? ", owned)" : ")");
            }
        }
        return sb.length() == 0 ? "No rewards for this lesson." : "Pass this lesson to unlock:   " + sb;
    }
}
