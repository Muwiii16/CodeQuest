package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.CampaignData.Progress;
import codequest.data.ClassTestData;
import codequest.data.JoinCode;
import codequest.data.Lesson;
import codequest.data.ProfessorData;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * One section: its lessons (each with Edit, its own performance Analysis and a class
 * test button), plus stage creation, class management, the overall class analysis and
 * the history of finished class tests.
 */
public class ProfessorSectionScreen extends ProfessorScreen {

    private static final int PAGE_SIZE = 6;

    private record Loaded(List<Lesson> lessons, List<Lesson> tests, List<ProfessorData.Student> roster,
            Map<Long, Map<Long, Progress>> progress, Optional<ClassTestData.Session> openTest) {
    }

    private final ClassInfo cls;
    private final int base;
    private List<Lesson> lessons = new java.util.ArrayList<>();
    /** Stages made only for class tests: students never see them in the campaign. */
    private List<Lesson> tests = List.of();
    private boolean showTests;
    private List<ProfessorData.Student> roster = List.of();
    private Map<Long, Map<Long, Progress>> progress = Map.of();
    private Optional<ClassTestData.Session> openTest = Optional.empty();
    private int page;

    public ProfessorSectionScreen(Main game, String username, ClassInfo cls) {
        super(game, username, cls.sectionName() + "   -   " + cls.language(),
                () -> game.setScreen(new ProfessorDashboardScreen(game, username)));
        this.cls = cls;

        card(60, 140, 1260, 880);
        base = mark();
        load("Loading the section...", () -> {
            ProfessorData data = new ProfessorData();
            CompletableFuture<Optional<ClassTestData.Session>> test = CompletableFuture
                    .supplyAsync(() -> new ClassTestData().openSession(cls.id()));
            List<Lesson> ls = new CampaignData().lessons(cls.id());
            List<ProfessorData.Student> students = data.roster(cls.id());
            return new Loaded(ls, new CampaignData().tests(cls.id()), students, data.overallProgress(students, ls), test.join());
        }, l -> {
            lessons = new java.util.ArrayList<>(l.lessons());
            tests = l.tests();
            roster = l.roster();
            progress = l.progress();
            openTest = l.openTest();
            rebuild();
        });
    }

    private void rebuild() {
        clearFrom(base);

        // ── right column: the section tools ──
        float x = 1370;
        float w = 490;
        card(x - 20, 140, w + 40, 880);
        text("Section tools", x, 160, w, 40, 2f, AMBER, Align.left);
        text(roster.size() + " students  -  " + lessons.size() + " lessons  -  " + tests.size() + " class tests", x, 205, w, 30,
                1.25f, MUTED, Align.left);
        solidButton("Stage creation", x, 255, w, 80, GOOD, () -> game.setScreen(new ProfessorStageCreationScreen(game, username, cls)));
        solidButton("Class management", x, 355, w, 80, BUTTON,
                () -> game.setScreen(new ProfessorClassManagementScreen(game, username, cls)));
        solidButton("Overall class performance", x, 455, w, 80, BUTTON,
                () -> game.setScreen(new ProfessorOverallScreen(game, username, cls)));
        solidButton("Class test history", x, 555, w, 80, BUTTON,
                () -> game.setScreen(new ProfessorTestHistoryScreen(game, username, cls)));
        if (openTest.isPresent()) {
            ClassTestData.Session s = openTest.get();
            text("Class test " + (s.isActive() ? "running" : "lobby open") + " on " + s.label().toLowerCase(), x, 670, w,
                    60, 1.5f, new Color(0.55f, 0.9f, 0.65f, 1f), Align.topLeft).setWrap(true);
        }
        String code = cls.joinCode();
        if (code != null && !code.isBlank()) {
            text("Join code for students", x, 735, w, 26, 1.15f, MUTED, Align.left, PixelFonts.Face.SANS);
            text(JoinCode.display(code), x, 764, 300, 60, 3.2f, new Color(1f, 0.82f, 0.3f, 1f), Align.left, PixelFonts.Face.MONO);
            solidButton("Copy", x + 320, 768, 170, 54, new Color(0.2f, 0.42f, 0.78f, 1f), () -> {
                com.badlogic.gdx.Gdx.app.getClipboard().setContents(JoinCode.display(code));
                toast("Join code copied: " + JoinCode.display(code), true);
            });
        }
        text("Each lesson has Edit (details and questions), Analysis (grades) and Class test. A stage made only for class "
                + "tests is on the Class tests tab.", x, 860, w, 130, 1.25f, MUTED, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);

        // ── lessons / class tests ──
        solidButton("Lessons  (" + lessons.size() + ")", 90, 150, 280, 52, showTests ? BUTTON : new Color(0.2f, 0.42f, 0.78f, 1f),
                () -> {
                    showTests = false;
                    page = 0;
                    rebuild();
                });
        solidButton("Class tests  (" + tests.size() + ")", 390, 150, 320, 52,
                showTests ? new Color(0.2f, 0.42f, 0.78f, 1f) : BUTTON, () -> {
                    showTests = true;
                    page = 0;
                    rebuild();
                });
        List<Lesson> shown = showTests ? tests : lessons;

        if (shown.isEmpty()) {
            text(showTests ? "No class tests yet.\nUse \"Stage creation\" and choose \"Class test\"."
                    : "No lessons yet.\nUse \"Stage creation\" to add the first one.", 90, 260, 1200, 200, 2f, MUTED,
                    Align.center);
            return;
        }
        int from = page * PAGE_SIZE;
        int to = Math.min(shown.size(), from + PAGE_SIZE);
        float y = 222;
        for (int i = from; i < to; i++) {
            Lesson l = shown.get(i);
            int done = 0;
            for (ProfessorData.Student s : roster) {
                Progress p = progress.getOrDefault(s.id(), Map.of()).get(l.stageId());
                if (p != null && p.completed()) {
                    done++;
                }
            }
            // in a class the professor runs by hand, every lesson has an Unlock / Lock button
            boolean manualLesson = cls.manual() && !showTests;
            rect(90, y, 1200, 110, ROW);
            rect(90, y, 8, 110, showTests ? new Color(0.35f, 0.62f, 1f, 1f) : AMBER);
            text(showTests ? "T" + l.number() : String.valueOf(l.number()), 115, y, 70, 110, 3f, Color.WHITE, Align.center);
            // the title, and the grading period the lesson belongs to on the right of it
            float titleW = manualLesson ? 380 : 440;
            text(l.title(), 200, y + 14, titleW, 44, 2.1f, Color.WHITE, Align.left).setEllipsis(true);
            if (!showTests) {
                text(l.periodEnum().label(), 200 + titleW, y + 18, 120, 36, 1.3f, AMBER, Align.right);
            }
            text(showTests ? l.baseDifficulty().toUpperCase() + "   -   only for class tests"
                    : l.baseDifficulty().toUpperCase() + "   -   " + done + " / " + roster.size() + " completed"
                            + (manualLesson ? "   -   " + (l.unlocked() ? "open to students" : "locked") : ""),
                    200, y + 64, manualLesson ? 500 : 560, 34, 1.4f,
                    manualLesson && l.unlocked() ? new Color(0.6f, 0.9f, 0.7f, 1f) : MUTED, Align.left);
            float bx = manualLesson ? 710 : 770;
            if (manualLesson) {
                final int at = lessons.indexOf(l);
                solidButton(l.unlocked() ? "Lock" : "Unlock", bx, y + 25, 130, 60,
                        l.unlocked() ? new Color(0.5f, 0.33f, 0.1f, 1f) : GOOD, () -> toggleUnlock(at));
                bx += 140;
            }
            solidButton("Edit", bx, y + 25, manualLesson ? 110 : 150, 60, BUTTON,
                    () -> game.setScreen(new ProfessorLessonScreen(game, username, cls, l)));
            bx += manualLesson ? 120 : 160;
            solidButton("Analysis", bx, y + 25, manualLesson ? 150 : 170, 60, BUTTON,
                    () -> game.setScreen(new ProfessorLessonAnalysisScreen(game, username, cls, l)));
            bx += manualLesson ? 160 : 180;
            boolean running = openTest.isPresent() && openTest.get().stageId() == l.stageId();
            solidButton(running ? "Open lobby" : showTests ? "Start test" : "Class test", bx, y + 25, manualLesson ? 160 : 170,
                    60, running ? new Color(0.55f, 0.37f, 0.08f, 1f) : GOOD, () -> openLobby(l));
            y += 124;
        }
        if (shown.size() > PAGE_SIZE) {
            int pages = (shown.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            if (page > 0) {
                solidButton("< Prev", 480, 960, 200, 52, BUTTON, () -> {
                    page--;
                    rebuild();
                });
            }
            text("Page " + (page + 1) + " / " + pages, 700, 960, 200, 52, 1.5f, MUTED, Align.center);
            if (page < pages - 1) {
                solidButton("Next >", 920, 960, 200, 52, BUTTON, () -> {
                    page++;
                    rebuild();
                });
            }
        }
    }

    /** Opens or locks one lesson for the students (a class the professor runs by hand). */
    private void toggleUnlock(int index) {
        if (index < 0 || index >= lessons.size()) {
            return;
        }
        Lesson l = lessons.get(index);
        boolean open = !l.unlocked();
        load(open ? "Unlocking the lesson..." : "Locking the lesson...", () -> new CampaignData().setUnlocked(l.stageId(), open),
                ok -> {
                    if (!ok) {
                        toast("Could not change the lesson. Check your connection.", false);
                        return;
                    }
                    lessons.set(index, l.withUnlocked(open));
                    toast("Lesson " + l.number() + (open ? " is now open to the students." : " is locked again."), true);
                    rebuild();
                });
    }

    private void openLobby(Lesson lesson) {
        if (openTest.isPresent() && openTest.get().stageId() != lesson.stageId()) {
            toast("A class test is already open on " + openTest.get().label().toLowerCase() + ". End it first.", false);
            return;
        }
        game.setScreen(new ProfessorTestLobbyScreen(game, username, cls, lesson));
    }
}
