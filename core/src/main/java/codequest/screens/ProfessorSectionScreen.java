package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.CampaignData.Progress;
import codequest.data.ClassTestData;
import codequest.data.Lesson;
import codequest.data.ProfessorData;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One section: its lessons (each with its own performance analysis and a class
 * test button), plus stage creation, class management and the overall class analysis.
 */
public class ProfessorSectionScreen extends ProfessorScreen {

    private static final int PAGE_SIZE = 6;

    private final ClassInfo cls;
    private final List<Lesson> lessons;
    private final List<ProfessorData.Student> roster;
    private final Map<Long, Map<Long, Progress>> progress;
    private final Optional<ClassTestData.Session> openTest;
    private final int base;
    private int page;

    public ProfessorSectionScreen(Main game, String username, ClassInfo cls) {
        super(game, username, cls.sectionName() + "   -   " + cls.language(),
                () -> game.setScreen(new ProfessorDashboardScreen(game, username)));
        this.cls = cls;
        ProfessorData data = new ProfessorData();
        lessons = new CampaignData().lessons(cls.id());
        roster = data.roster(cls.id());
        progress = data.overallProgress(roster, lessons);
        openTest = new ClassTestData().openSession(cls.id());

        // ── right column: the section tools ──
        float x = 1370;
        float w = 490;
        card(x - 20, 140, w + 40, 880);
        text("Section tools", x, 160, w, 40, 2f, AMBER, Align.left);
        text(roster.size() + " students   -   " + lessons.size() + " lessons", x, 205, w, 30, 1.4f, MUTED, Align.left);
        solidButton("Stage creation", x, 260, w, 90, GOOD, () -> game.setScreen(new ProfessorStageCreationScreen(game, username, cls)));
        solidButton("Class management", x, 380, w, 90, BUTTON,
                () -> game.setScreen(new ProfessorClassManagementScreen(game, username, cls)));
        solidButton("Overall class performance", x, 500, w, 90, BUTTON,
                () -> game.setScreen(new ProfessorOverallScreen(game, username, cls)));
        if (openTest.isPresent()) {
            ClassTestData.Session s = openTest.get();
            text("Class test " + (s.isActive() ? "running" : "lobby open") + " on lesson " + s.lessonNumber(), x, 640, w,
                    60, 1.5f, new Color(0.55f, 0.9f, 0.65f, 1f), Align.topLeft).setWrap(true);
        }
        text("Each lesson below has its own performance analysis and a class test button.", x, 880, w, 100, 1.3f, MUTED,
                Align.topLeft).setWrap(true);

        card(60, 140, 1260, 880);
        text("Lessons", 90, 155, 600, 40, 2f, AMBER, Align.left);
        base = mark();
        rebuild();
    }

    private void rebuild() {
        clearFrom(base);
        if (lessons.isEmpty()) {
            text("No lessons yet.\nUse \"Stage creation\" to add the first one.", 90, 240, 1200, 200, 2f, MUTED,
                    Align.center);
            return;
        }
        int from = page * PAGE_SIZE;
        int to = Math.min(lessons.size(), from + PAGE_SIZE);
        float y = 210;
        for (int i = from; i < to; i++) {
            Lesson l = lessons.get(i);
            int done = 0;
            for (ProfessorData.Student s : roster) {
                Progress p = progress.getOrDefault(s.id(), Map.of()).get(l.stageId());
                if (p != null && p.completed()) {
                    done++;
                }
            }
            rect(90, y, 1200, 110, ROW);
            rect(90, y, 8, 110, AMBER);
            text(String.valueOf(l.number()), 115, y, 70, 110, 3f, Color.WHITE, Align.center);
            text(l.title(), 200, y + 14, 560, 44, 2.1f, Color.WHITE, Align.left);
            text(l.baseDifficulty().toUpperCase() + "   -   " + done + " / " + roster.size() + " completed", 200, y + 64,
                    560, 34, 1.4f, MUTED, Align.left);
            solidButton("Analysis", 770, y + 25, 220, 60, BUTTON,
                    () -> game.setScreen(new ProfessorLessonAnalysisScreen(game, username, cls, l)));
            boolean running = openTest.isPresent() && openTest.get().stageId() == l.stageId();
            solidButton(running ? "Open lobby" : "Class test", 1010, y + 25, 250, 60,
                    running ? new Color(0.55f, 0.37f, 0.08f, 1f) : GOOD, () -> openLobby(l));
            y += 124;
        }
        if (lessons.size() > PAGE_SIZE) {
            int pages = (lessons.size() + PAGE_SIZE - 1) / PAGE_SIZE;
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

    private void openLobby(Lesson lesson) {
        if (openTest.isPresent() && openTest.get().stageId() != lesson.stageId()) {
            toast("A class test is already open on lesson " + openTest.get().lessonNumber() + ". End it first.", false);
            return;
        }
        game.setScreen(new ProfessorTestLobbyScreen(game, username, cls, lesson));
    }
}
