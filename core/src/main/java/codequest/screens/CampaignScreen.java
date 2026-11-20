package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.CampaignData.Progress;
import codequest.data.DatabaseManager;
import codequest.data.DatabaseManager.Cosmetic;
import codequest.data.Lesson;
import codequest.data.RunCache;
import codequest.session.GachaRoll;
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
            Set<Long> rolled, DatabaseManager.RollStats stats, List<Lesson> lessons, Map<Long, Progress> progress,
            int classIdx) {
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
    private Set<Long> rolled = Set.of();
    private DatabaseManager.RollStats stats = new DatabaseManager.RollStats(0, 0);
    private int classIdx;
    private int page;
    private Long expandedStageId;
    private List<Lesson> lessons = List.of();
    private Map<Long, Progress> progress = Map.of();

    public CampaignScreen(Main game, String username) {
        this(game, username, null);
    }

    /** preferredClassId opens that subject (e.g. the one just played or picked); null opens the current one. */
    public CampaignScreen(Main game, String username, Long preferredClassId) {
        super(game, username, "Campaign", () -> game.setScreen(new MainMenuScreen(game, username)));
        base = mark();
        load("Loading your lessons...", () -> {
            long id = db.profileId(username);
            CompletableFuture<Object[]> shop = CompletableFuture.supplyAsync(
                    () -> new Object[] { db.getCatalog(), new HashSet<>(db.getOwnedCosmetics(id)), db.rolledStages(id),
                            db.getRollStats(id) });
            List<ClassInfo> myClasses = data.studentClasses(id);
            int first = CampaignData.defaultClassIndex(myClasses, preferredClassId);
            LessonsLoaded l = fetchLessons(id, myClasses.isEmpty() ? null : myClasses.get(first).id());
            Object[] s = shop.join();
            @SuppressWarnings("unchecked")
            List<Cosmetic> cat = (List<Cosmetic>) s[0];
            @SuppressWarnings("unchecked")
            Set<Long> own = (Set<Long>) s[1];
            @SuppressWarnings("unchecked")
            Set<Long> rolls = (Set<Long>) s[2];
            return new Loaded(id, myClasses, cat, own, rolls, (DatabaseManager.RollStats) s[3], l.lessons(), l.progress(), first);
        }, l -> {
            studentId = l.studentId();
            classIdx = l.classIdx();
            classes = l.classes();
            catalog = l.catalog();
            owned = l.owned();
            rolled = l.rolled();
            stats = l.stats();
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

    /** The subjects screen: this semester's classes, and the past ones the student can still open. */
    private void openSubjects() {
        game.setScreen(new StudentSubjectsScreen(game, username, classes, classes.get(classIdx).id()));
    }

    private void rebuild() {
        clearFrom(base);

        String context = classes.isEmpty() ? "Built-in lessons  (you are not in a class yet)"
                : classes.get(classIdx).subjectName() + "  -  " + classes.get(classIdx).sectionName()
                        + (classes.get(classIdx).termLabel().isEmpty() ? "" : "  -  " + classes.get(classIdx).termLabel())
                        + (classes.get(classIdx).ended() ? "   (ended)" : "");
        text(context, 160, 128, 1010, 40, 1.8f, classes.isEmpty() || !classes.get(classIdx).ended() ? MUTED : GOLD, Align.left);
        // a student enrolls themselves with the code the professor gives
        solidButton("Join a class", 1190, 122, 290, 52, GOOD,
                () -> game.setScreen(new JoinClassScreen(game, username, () -> game.setScreen(new CampaignScreen(game, username)))));
        if (classes.size() > 1) {
            solidButton("Subjects", 1500, 122, 260, 52, BUTTON, this::openSubjects);
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
        Long here = classes.isEmpty() ? null : classes.get(classIdx).id();
        PlayScreen.open(game, username, lesson, null, resume, () -> game.setScreen(new CampaignScreen(game, username, here)));
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
            status = "Completed  -  Rank " + (p.rank() == null ? "-" : p.rank()) + "  -  best score " + p.score()
                    + (rolled.contains(lesson.stageId()) ? "  -  reward claimed" : "  -  reward roll waiting");
        } else if (unlocked) {
            status = "Ready  -  starts at " + lesson.baseDifficulty().toUpperCase() + "  -  reward roll waiting";
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
            solidButton("Locked", 1460, y + 27, 270, 56, new Color(0.2f, 0.22f, 0.3f, 1f), Color.GRAY, INERT);
        }

        float next = y + 124;
        if (expanded) {
            rect(160, y + 114, 1600, 76, CARD);
            text(rewardText(lesson), 200, y + 114, 1520, 76, 1.5f, GOLD, Align.left);
            next = y + 204;
        }
        return next;
    }

    /** The first time you pass a lesson you get one roll; replays give none. */
    private String rewardText(Lesson lesson) {
        String head = rolled.contains(lesson.stageId())
                ? "Reward claimed. Replaying this lesson gives no more rolls."
                : "Pass this lesson for one reward roll (a hat, weapon, trail or title).   Odds:  " + GachaRoll.oddsText();
        return head + "\nPity: a rare or better item is guaranteed within " + GachaRoll.PITY_ROLLS + " rolls.  "
                + (stats.untilGuarantee() <= 1 ? "Your next roll is guaranteed!" : "Next guarantee in " + stats.untilGuarantee()
                        + " rolls.");
    }
}
