package codequest.screens;

import codequest.Main;
import codequest.content.CTopic;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.Lesson;
import codequest.data.QuestionData;
import codequest.data.QuestionData.Question;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Edit a lesson after it was created (or generated): rename it, change its difficulty, and read, add,
 * rewrite or delete its questions and answer choices. The game draws a random question matching each
 * tower's topic and difficulty, so a topic can have several; a topic with none uses the built-in ones.
 */
public class ProfessorLessonScreen extends ProfessorScreen {

    private static final String[] DIFFICULTIES = { "easy", "normal", "hard" };
    private static final int PAGE_SIZE = 6;

    private final CampaignData campaigns = new CampaignData();
    private final QuestionData questionData = new QuestionData();
    private final ClassInfo cls;
    private Lesson lesson;
    private final TextField titleField;
    private final TextArea promptBox;
    private final Group formRegion;
    private final Group listRegion;
    private int difficultyIdx;
    private boolean adaptive;
    private boolean deleteArmed;
    private long questionDeleteArmed = -1;
    private List<Question> questions = List.of();
    private int page;

    public ProfessorLessonScreen(Main game, String username, ClassInfo cls, Lesson lesson) {
        super(game, username, "Lesson " + lesson.number() + "  -  " + lesson.title(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        this.cls = cls;
        this.lesson = lesson;
        this.difficultyIdx = Math.max(0, java.util.Arrays.asList(DIFFICULTIES).indexOf(lesson.baseDifficulty()));
        this.adaptive = lesson.adaptive();

        // ── lesson details ──
        card(60, 140, 700, 880);
        text("Lesson details", 90, 155, 500, 40, 2f, AMBER, Align.left);
        text("Title", 90, 215, 300, 30, 1.4f, MUTED, Align.left);
        titleField = textField("Lesson title", false, 90, 248, 640, 64);
        titleField.setText(lesson.title());
        text("What you asked for (kept for the AI step)", 90, 560, 640, 30, 1.4f, MUTED, Align.left);
        promptBox = textArea("(nothing written)", 90, 595, 640, 170);
        promptBox.setText(lesson.prompt() == null ? "" : lesson.prompt());
        formRegion = region();
        rebuildForm();

        // ── questions ──
        card(790, 140, 1070, 880);
        text("Questions", 820, 155, 400, 40, 2f, AMBER, Align.left);
        listRegion = region();
        refreshQuestions();
    }

    private void rebuildForm() {
        fill(formRegion, () -> {
            text("Starting difficulty", 90, 335, 400, 30, 1.4f, MUTED, Align.left);
            for (int i = 0; i < DIFFICULTIES.length; i++) {
                int idx = i;
                solidButton(DIFFICULTIES[i].toUpperCase(), 90 + i * 215, 368, 205, 60,
                        i == difficultyIdx ? new Color(0.55f, 0.37f, 0.08f, 1f) : BUTTON, () -> {
                            difficultyIdx = idx;
                            rebuildForm();
                        });
            }
            text("Adaptive difficulty", 90, 445, 400, 30, 1.4f, MUTED, Align.left);
            solidButton(adaptive ? "ON  -  adjusts after each section" : "OFF  -  stays fixed", 90, 478, 640, 60,
                    adaptive ? GOOD : BUTTON, () -> {
                        adaptive = !adaptive;
                        rebuildForm();
                    });
            solidButton("Save details", 90, 800, 640, 70, GOOD, this::saveDetails);
            solidButton(deleteArmed ? "Click again: delete this lesson" : "Delete lesson", 90, 890, 640, 70,
                    deleteArmed ? new Color(0.7f, 0.15f, 0.15f, 1f) : new Color(0.35f, 0.18f, 0.18f, 1f), this::deleteLesson);
        });
    }

    private void refreshQuestions() {
        load("Loading the questions...", () -> questionData.list(lesson.stageId()), list -> {
            questions = list;
            if (page * PAGE_SIZE >= Math.max(1, questions.size())) {
                page = 0;
            }
            rebuildList();
        });
    }

    private void rebuildList() {
        fill(listRegion, this::drawList);
    }

    private void drawList() {
        Map<CTopic, Integer> counts = new EnumMap<>(CTopic.class);
        for (CTopic t : CTopic.values()) {
            counts.put(t, 0);
        }
        questions.forEach(q -> counts.merge(q.draft().topic(), 1, Integer::sum));
        StringBuilder coverage = new StringBuilder();
        for (CTopic t : CTopic.values()) {
            coverage.append(coverage.length() == 0 ? "" : "     ").append(CosmeticTopic.label(t)).append(" ")
                    .append(counts.get(t));
        }
        text(questions.size() + (questions.size() == 1 ? " question" : " questions"), 1500, 160, 330, 36, 1.7f, MUTED,
                Align.right);
        text(coverage.toString(), 820, 205, 1010, 30, 1.25f, MUTED, Align.left);
        text("A topic with 0 uses the built-in questions. Students get a random question for each tower's topic.", 820, 238,
                1010, 30, 1.2f, MUTED, Align.left);

        if (questions.isEmpty()) {
            text("No questions written for this lesson yet.\nAdd your own, or start from the built-in ones and rewrite them.",
                    820, 330, 1010, 150, 1.8f, MUTED, Align.center);
            solidButton("Add the built-in starter questions", 900, 520, 850, 80, GOOD, this::importBuiltIn);
        }
        int from = page * PAGE_SIZE;
        int to = Math.min(questions.size(), from + PAGE_SIZE);
        float y = 280;
        for (int i = from; i < to; i++) {
            Question q = questions.get(i);
            rect(820, y, 1010, 92, ROW);
            text(CosmeticTopic.label(q.draft().topic()) + "   -   " + q.draft().difficulty().name().toLowerCase(), 840, y + 8,
                    600, 30, 1.3f, AMBER, Align.left);
            text(ellipsize(q.draft().prompt(), 64), 840, y + 40, 700, 40, 1.5f, Color.WHITE, Align.left);
            text("answer: " + ellipsize(q.draft().correct(), 14), 1260, y + 8, 250, 30, 1.2f, MUTED, Align.left);
            solidButton("Edit", 1530, y + 22, 120, 48, BUTTON,
                    () -> game.setScreen(new ProfessorQuestionEditScreen(game, username, cls, lesson, q)));
            boolean armed = questionDeleteArmed == q.id();
            solidButton(armed ? "Sure?" : "Delete", 1665, y + 22, 150, 48,
                    armed ? new Color(0.7f, 0.15f, 0.15f, 1f) : new Color(0.35f, 0.18f, 0.18f, 1f), () -> deleteQuestion(q));
            y += 100;
        }
        if (questions.size() > PAGE_SIZE) {
            int pages = (questions.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            if (page > 0) {
                solidButton("< Prev", 820, 905, 160, 48, BUTTON, () -> {
                    page--;
                    rebuildList();
                });
            }
            text("Page " + (page + 1) + " / " + pages, 1000, 905, 160, 48, 1.4f, MUTED, Align.center);
            if (page < pages - 1) {
                solidButton("Next >", 1180, 905, 160, 48, BUTTON, () -> {
                    page++;
                    rebuildList();
                });
            }
        }
        solidButton("+  Add a question", 1400, 960, 430, 50, GOOD,
                () -> game.setScreen(new ProfessorQuestionEditScreen(game, username, cls, lesson, null)));
    }

    private static String ellipsize(String s, int max) {
        String flat = s.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max - 3) + "...";
    }

    private void importBuiltIn() {
        load("Adding the starter questions...", () -> questionData.importBuiltIn(lesson.stageId()), added -> {
            toast(added + " starter questions added. Edit any of them to make them yours.", added > 0);
            refreshQuestions();
        });
    }

    private void deleteQuestion(Question q) {
        if (questionDeleteArmed != q.id()) {
            questionDeleteArmed = q.id();
            rebuildList();
            return;
        }
        questionDeleteArmed = -1;
        load("Deleting the question...", () -> questionData.delete(q.id()), ok -> {
            refreshQuestions();
            toast(ok ? "Question deleted." : "Could not delete the question.", ok);
        });
    }

    private void saveDetails() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            toast("The lesson needs a title.", false);
            return;
        }
        String difficulty = DIFFICULTIES[difficultyIdx];
        boolean adapt = adaptive;
        String prompt = promptBox.getText();
        load("Saving...", () -> campaigns.updateLesson(lesson.stageId(), title, difficulty, adapt, prompt), ok -> {
            if (ok) {
                lesson = new Lesson(lesson.stageId(), lesson.campaignId(), lesson.number(), title, difficulty, adapt,
                        prompt.isBlank() ? null : prompt);
                toast("Lesson details saved.", true);
            } else {
                toast("Could not save. Check your connection.", false);
            }
        });
    }

    private void deleteLesson() {
        if (!deleteArmed) {
            deleteArmed = true;
            rebuildForm();
            return;
        }
        Lesson toDelete = lesson;
        load("Deleting the lesson...", () -> campaigns.deleteLesson(toDelete), ok -> {
            if (ok) {
                game.setScreen(new ProfessorSectionScreen(game, username, cls));
            } else {
                toast("Could not delete the lesson.", false);
            }
        });
    }

    /** Short topic names for the lists. */
    static final class CosmeticTopic {
        private CosmeticTopic() {
        }

        static String label(CTopic t) {
            return switch (t) {
                case SYNTAX -> "Syntax";
                case VARIABLES -> "Variables";
                case CONTROL_STRUCTURES -> "Control";
                case FUNCTIONS -> "Functions";
                case ARRAYS -> "Arrays";
                case POINTERS -> "Pointers";
            };
        }
    }
}
