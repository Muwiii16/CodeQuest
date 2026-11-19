package codequest.screens;

import codequest.Main;
import codequest.content.AnswerKind;
import codequest.content.CTopic;
import codequest.content.Difficulty;
import codequest.content.ProgramCoverage;
import codequest.content.QuestionDraft;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.Lesson;
import codequest.data.QuestionData;
import codequest.data.QuestionData.Question;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
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
    private static final int PAGE_SIZE = 9;

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
        super(game, username, lesson.label() + "  -  " + lesson.title(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        this.cls = cls;
        this.lesson = lesson;
        this.difficultyIdx = Math.max(0, java.util.Arrays.asList(DIFFICULTIES).indexOf(lesson.baseDifficulty()));
        this.adaptive = lesson.adaptive();

        // ── lesson details ──
        card(60, 140, 700, 880);
        text(lesson.test() ? "Class test details" : "Lesson details", 90, 155, 600, 40, 2f, AMBER, Align.left);
        text("Title", 90, 215, 300, 30, 1.4f, MUTED, Align.left);
        titleField = textField("Lesson title", false, 90, 248, 640, 64);
        titleField.setText(lesson.title());
        text("What you asked for (kept for the AI step)", 90, 560, 640, 30, 1.4f, MUTED, Align.left);
        promptBox = textArea("(nothing written)", 90, 595, 640, 200, PixelFonts.Face.SANS);
        promptBox.setText(lesson.prompt() == null ? "" : lesson.prompt());
        formRegion = region();
        rebuildForm();

        // ── questions ──
        card(790, 140, 1070, 880);
        text("Questions", 820, 155, 400, 40, 2f, AMBER, Align.left);
        listRegion = region();
        refreshQuestions();
    }

    /** Opens the lesson screen with the lesson as it is stored now (e.g. after its program was changed). */
    public static void open(Main game, String username, ClassInfo cls, Lesson lesson) {
        LoadingScreen.open(game, "Loading the lesson...", () -> new CampaignData().lesson(lesson.stageId()).orElse(lesson),
                fresh -> game.setScreen(new ProfessorLessonScreen(game, username, cls, fresh)),
                () -> game.setScreen(new ProfessorLessonScreen(game, username, cls, lesson)));
    }

    private void rebuildForm() {
        fill(formRegion, () -> {
            text(lesson.test() ? "Difficulty of the test" : "Starting difficulty", 90, 335, 400, 30, 1.4f, MUTED, Align.left);
            for (int i = 0; i < DIFFICULTIES.length; i++) {
                int idx = i;
                solidButton(DIFFICULTIES[i].toUpperCase(), 90 + i * 215, 368, 205, 60,
                        i == difficultyIdx ? new Color(0.55f, 0.37f, 0.08f, 1f) : BUTTON, () -> {
                            difficultyIdx = idx;
                            rebuildForm();
                        });
            }
            text("Adaptive difficulty", 90, 445, 400, 30, 1.4f, MUTED, Align.left);
            if (lesson.test()) {
                // a class test is played at one fixed difficulty by everybody, so there is nothing to adapt
                solidButton("OFF  -  a class test has one fixed difficulty", 90, 478, 640, 60, BUTTON, INERT);
            } else {
                solidButton(adaptive ? "ON  -  adjusts after each section" : "OFF  -  stays fixed", 90, 478, 640, 60,
                        adaptive ? GOOD : BUTTON, () -> {
                            adaptive = !adaptive;
                            rebuildForm();
                        });
            }
            solidButton("Save details", 90, 830, 640, 62, GOOD, this::saveDetails);
            solidButton(deleteArmed ? "Click again: delete this lesson" : "Delete lesson", 90, 905, 640, 62,
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
        // top: what the professor does most (the program and a new question) sits where it is easy to find
        solidButton(lesson.hasProgram() ? "Edit the program" : "Write the program", 1170, 148, 310, 56, BUTTON,
                () -> game.setScreen(new ProfessorProgramScreen(game, username, cls, lesson)));
        solidButton("+  Add a question", 1500, 148, 330, 56, GOOD,
                () -> game.setScreen(new ProfessorQuestionEditScreen(game, username, cls, lesson, null)));
        text(questions.size() + (questions.size() == 1 ? " question" : " questions"), 820, 214, 190, 34, 1.5f, Color.WHITE,
                Align.left);
        float chipX = 1020;
        for (CTopic t : CTopic.values()) {
            if (counts.get(t) > 0) {
                String chip = CosmeticTopic.label(t) + "  " + counts.get(t);
                float chipW = 36f + chip.length() * 11f;
                rect(chipX, 212, chipW, 36, new Color(0.2f, 0.24f, 0.33f, 1f));
                text(chip, chipX, 212, chipW, 36, 1.15f, Color.WHITE, Align.center, PixelFonts.Face.SANS);
                chipX += chipW + 10f;
            }
        }
        if (lesson.hasProgram()) {
            ProgramCoverage.Report report = QuestionData.coverage(lesson.program(), questions);
            text("Program: " + ProgramCoverage.describe(report), 820, 258, 1010, 30, 1.2f,
                    report.complete() ? new Color(0.6f, 0.95f, 0.6f, 1f) : report.problem() != null
                            ? new Color(1f, 0.5f, 0.45f, 1f) : AMBER, Align.left, PixelFonts.Face.SANS);
        } else {
            text("Students get a random question for each tower's topic. A topic with 0 uses the built-in questions.", 820,
                    258, 1010, 30, 1.2f, MUTED, Align.left, PixelFonts.Face.SANS);
        }

        if (questions.isEmpty()) {
            text("No questions written for this lesson yet.\nAdd your own, or start from the built-in ones and rewrite them.",
                    820, 330, 1010, 150, 1.8f, MUTED, Align.center);
            solidButton("Write the program the lesson builds (recommended)", 900, 520, 850, 80, GOOD,
                    () -> game.setScreen(new ProfessorProgramScreen(game, username, cls, lesson)));
            solidButton("Add the built-in starter questions (random mode)", 900, 620, 850, 70, BUTTON, this::importBuiltIn);
        }
        int from = page * PAGE_SIZE;
        int to = Math.min(questions.size(), from + PAGE_SIZE);
        // a chunk's difficulty levels sit together, easiest first
        List<Question> shown = new java.util.ArrayList<>();
        QuestionData.bySlot(questions).forEach(shown::addAll);
        if (questions.size() > PAGE_SIZE) {
            int pages = (questions.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            solidButton("<", 1640, 252, 52, 40, page > 0 ? BUTTON : ROW, () -> {
                if (page > 0) {
                    page--;
                    rebuildList();
                }
            });
            text("Page " + (page + 1) + " / " + pages, 1692, 252, 90, 40, 1.1f, MUTED, Align.center, PixelFonts.Face.SANS);
            solidButton(">", 1782, 252, 52, 40, page < pages - 1 ? BUTTON : ROW, () -> {
                if (page < pages - 1) {
                    page++;
                    rebuildList();
                }
            });
        }
        float y = 296;
        for (int i = from; i < to; i++) {
            Question q = shown.get(i);
            drawRow(q, y);
            y += 78;
        }
        if (!questions.isEmpty()) {
            text("Click a question to edit it.     \"+ easy\" or \"+ normal\" adds another difficulty of the same lines.", 820, 1000,
                    1010, 20, 1.0f, MUTED, Align.center, PixelFonts.Face.SANS);
        }
    }

    /** One question: a colored stripe for its kind, its text, what kind it is, and the buttons. The whole row opens the editor. */
    private void drawRow(Question q, float y) {
        QuestionDraft d = q.draft();
        Color accent = kindColor(d.kind());
        rect(820, y, 1010, 72, ROW);
        rect(820, y, 8, 72, accent);
        // the row itself is a button: a click anywhere on it (outside the small buttons) opens the question
        Image open = rect(820, y, 1010, 72, new Color(0f, 0f, 0f, 0f));
        open.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float cx, float cy) {
                game.setScreen(new ProfessorQuestionEditScreen(game, username, cls, lesson, q));
            }

            @Override
            public void enter(InputEvent event, float ex, float ey, int pointer, com.badlogic.gdx.scenes.scene2d.Actor from) {
                if (pointer == -1) {
                    Gdx.graphics.setSystemCursor(com.badlogic.gdx.graphics.Cursor.SystemCursor.Hand);
                }
            }

            @Override
            public void exit(InputEvent event, float ex, float ey, int pointer, com.badlogic.gdx.scenes.scene2d.Actor to) {
                if (pointer == -1) {
                    Gdx.graphics.setSystemCursor(com.badlogic.gdx.graphics.Cursor.SystemCursor.Arrow);
                }
            }
        });
        text(q.slot() > 0 ? "#" + q.slot() : "-", 836, y, 64, 72, 1.5f, Color.WHITE, Align.center);
        text(ellipsize(d.prompt(), 40), 912, y + 6, 590, 32, 1.45f, Color.WHITE, Align.left,
                PixelFonts.faceForPrompt(d.kind(), d.prompt())).setTouchable(Touchable.disabled);
        String answer = d.kind() == AnswerKind.FIND_BUG ? "line " + d.correct() : d.correct();
        text(d.kind().label(), 912, y + 40, 190, 26, 1.1f, accent, Align.left, PixelFonts.Face.SANS);
        text(CosmeticTopic.label(d.topic()), 1100, y + 40, 100, 26, 1.1f, MUTED, Align.left, PixelFonts.Face.SANS);
        text(d.difficulty().name().charAt(0) + d.difficulty().name().substring(1).toLowerCase(), 1200, y + 40, 90, 26, 1.1f,
                difficultyColor(d.difficulty()), Align.left, PixelFonts.Face.SANS);
        text("answer:  " + ellipsize(answer, 11), 1285, y + 40, 220, 26, 1.1f, MUTED, Align.left, PixelFonts.Face.MONO);

        solidButton("Edit", 1514, y + 12, 96, 48, new Color(0.2f, 0.42f, 0.78f, 1f),
                () -> game.setScreen(new ProfessorQuestionEditScreen(game, username, cls, lesson, q)));
        Difficulty missing = missingLevel(q);
        if (lesson.hasProgram() && missing != null) {
            solidButton("+ " + missing.name().toLowerCase(), 1620, y + 12, 104, 48, BUTTON,
                    () -> game.setScreen(new ProfessorQuestionEditScreen(game, username, cls, lesson, q, missing)));
        }
        boolean armed = questionDeleteArmed == q.id();
        solidButton(armed ? "Sure?" : "Delete", 1734, y + 12, 84, 48,
                armed ? new Color(0.7f, 0.15f, 0.15f, 1f) : new Color(0.35f, 0.18f, 0.18f, 1f), () -> deleteQuestion(q));
    }

    private static Color kindColor(AnswerKind kind) {
        return switch (kind) {
            case CHOICE -> new Color(0.35f, 0.62f, 1f, 1f);
            case FILL_BLANK -> new Color(0.35f, 0.8f, 0.55f, 1f);
            case TRUE_FALSE -> new Color(0.72f, 0.55f, 0.95f, 1f);
            case ARRANGE -> new Color(0.95f, 0.7f, 0.25f, 1f);
            case FIND_BUG -> new Color(0.95f, 0.45f, 0.45f, 1f);
        };
    }

    private static Color difficultyColor(Difficulty d) {
        return switch (d) {
            case EASY -> new Color(0.55f, 0.9f, 0.65f, 1f);
            case NORMAL -> new Color(0.95f, 0.8f, 0.4f, 1f);
            case HARD -> new Color(1f, 0.55f, 0.5f, 1f);
        };
    }

    /** The first difficulty this question's chunk has no question for yet, or null (none to add, or no slot). */
    private Difficulty missingLevel(Question q) {
        if (q.slot() <= 0) {
            return null;
        }
        for (Difficulty d : Difficulty.values()) {
            boolean taken = false;
            for (Question other : questions) {
                taken |= other.slot() == q.slot() && other.draft().difficulty() == d;
            }
            if (!taken) {
                return d;
            }
        }
        return null;
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
        boolean adapt = adaptive && !lesson.test();
        String prompt = promptBox.getText();
        load("Saving...", () -> campaigns.updateLesson(lesson.stageId(), title, difficulty, adapt, prompt), ok -> {
            if (ok) {
                lesson = lesson.withDetails(title, difficulty, adapt, prompt);
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
