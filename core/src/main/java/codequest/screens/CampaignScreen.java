package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.CampaignData.Progress;
import codequest.data.DatabaseManager;
import codequest.data.DatabaseManager.Cosmetic;
import codequest.data.Lesson;
import codequest.data.RunCache;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Campaign Mode: the lessons of the student's class (or the built-in set when
 * they are not in one), each with a Rewards dropdown. Lessons unlock in order, and a
 * lesson left half-played can be resumed.
 */
public class CampaignScreen extends StudentScreen {

    private static final int PAGE_SIZE = 5;

    /** Everything the screen shows, fetched together in the background. */
    private record Loaded(long studentId, List<ClassInfo> classes, List<Cosmetic> catalog, Set<Long> owned,
            List<Lesson> lessons, Map<Long, Progress> progress) {
    }

    private record LessonsLoaded(List<Lesson> lessons, Map<Long, Progress> progress) {
    }

    private final CampaignData data = new CampaignData();
    private final DatabaseManager db = new DatabaseManager();
    private final int base;

    private long studentId;
    private List<ClassInfo> classes = List.of();
    private List<Cosmetic> catalog = List.of();
    private Set<Long> owned = Set.of();
    private int classIdx;
    private int page;
    private Long expandedStageId;
    private List<Lesson> lessons = List.of();
    private Map<Long, Progress> progress = Map.of();

    public CampaignScreen(Main game, String username) {
        super(game, username, "Campaign", () -> game.setScreen(new MainMenuScreen(game, username)));
        base = mark();
        load("Loading your lessons...", () -> {
            long id = db.profileId(username);
            CompletableFuture<Object[]> shop = CompletableFuture.supplyAsync(
                    () -> new Object[] { db.getCatalog(), new HashSet<>(db.getOwnedCosmetics(id)) });
            List<ClassInfo> myClasses = data.studentClasses(id);
            LessonsLoaded l = fetchLessons(id, myClasses.isEmpty() ? null : myClasses.get(0).id());
            Object[] s = shop.join();
            @SuppressWarnings("unchecked")
            List<Cosmetic> cat = (List<Cosmetic>) s[0];
            @SuppressWarnings("unchecked")
            Set<Long> own = (Set<Long>) s[1];
            return new Loaded(id, myClasses, cat, own, l.lessons(), l.progress());
        }, l -> {
            studentId = l.studentId();
            classes = l.classes();
            catalog = l.catalog();
            owned = l.owned();
            lessons = l.lessons();
            progress = l.progress();
            rebuild();
        });
    }

    private LessonsLoaded fetchLessons(long id, Long classId) {
        CompletableFuture<Map<Long, Progress>> progressFuture = CompletableFuture.supplyAsync(() -> data.progress(id));
        List<Lesson> list = data.lessons(classId);
        return new LessonsLoaded(list, progressFuture.join());
    }

    private void switchClass() {
        classIdx = (classIdx + 1) % classes.size();
        load("Loading lessons...", () -> fetchLessons(studentId, classes.get(classIdx).id()), l -> {
            lessons = l.lessons();
            progress = l.progress();
            page = 0;
            expandedStageId = null;
            rebuild();
        });
    }

    private void rebuild() {
        clearFrom(base);

        String context = classes.isEmpty() ? "Built-in lessons  (you are not in a class yet)"
                : classes.get(classIdx).sectionName() + "  -  " + classes.get(classIdx).language();
        text(context, 160, 128, 1100, 40, 1.8f, MUTED, Align.left);
        if (classes.size() > 1) {
            solidButton("Switch class", 1500, 122, 260, 52, BUTTON, this::switchClass);
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

    private void play(Lesson lesson, boolean resume) {
        PlayScreen.open(game, username, lesson, null, resume, () -> game.setScreen(new CampaignScreen(game, username)));
    }

    /** Draws one lesson row (and its rewards strip if expanded); returns the y for the next row. */
    private float drawLesson(int index, float y) {
        Lesson lesson = lessons.get(index);
        Progress p = progress.get(lesson.stageId());
        boolean unlocked = CampaignData.isUnlocked(lessons, progress, index);
        boolean done = p != null && p.completed();
        boolean halfPlayed = unlocked && RunCache.exists(RunCache.campaignKey(lesson.stageId()), username);

        rect(160, y, 1600, 110, ROW);
        rect(160, y, 8, 110, done ? GOOD : unlocked ? TEAL : new Color(0.35f, 0.38f, 0.5f, 1f));
        text(String.valueOf(lesson.number()), 190, y, 80, 110, 3.4f, unlocked ? Color.WHITE : MUTED, Align.center);
        text(lesson.title(), 300, y + 14, 880, 44, 2.3f, unlocked ? Color.WHITE : MUTED, Align.left);
        String status;
        if (halfPlayed) {
            status = "In progress  -  you can pick up where you stopped";
        } else if (done) {
            status = "Completed  -  Rank " + (p.rank() == null ? "-" : p.rank()) + "  -  best score " + p.score();
        } else if (unlocked) {
            status = "Ready  -  starts at " + lesson.baseDifficulty().toUpperCase();
        } else {
            status = "Locked  -  pass lesson " + (lesson.number() - 1) + " first";
        }
        text(status, 300, y + 64, 880, 34, 1.5f, done || halfPlayed ? new Color(0.6f, 0.9f, 0.7f, 1f) : MUTED, Align.left);

        boolean expanded = Long.valueOf(lesson.stageId()).equals(expandedStageId);
        solidButton(expanded ? "Rewards  ^" : "Rewards  v", 1200, y + 27, 230, 56, BUTTON, () -> {
            expandedStageId = expanded ? null : lesson.stageId();
            rebuild();
        });
        if (halfPlayed) {
            solidButton("Resume", 1450, y + 27, 150, 56, GOOD, () -> play(lesson, true));
            solidButton("Restart", 1610, y + 27, 140, 56, BUTTON, () -> play(lesson, false));
        } else if (unlocked) {
            solidButton(done ? "Replay" : "Play", 1460, y + 27, 270, 56, GOOD, () -> play(lesson, false));
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
