package codequest.screens;

import codequest.Main;
import codequest.content.AnswerKind;
import codequest.content.AnswerMatcher;
import codequest.content.CTopic;
import codequest.content.Challenge;
import codequest.content.Difficulty;
import codequest.content.QuestionDraft;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.Lesson;
import codequest.data.QuestionData;
import codequest.data.QuestionData.Question;
import codequest.screens.PixelFonts.Face;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Write a new question or rewrite an existing one, in three steps so that each page holds only a few boxes:
 * 1. the kind of question (how the student answers), its topic and difficulty;
 * 2. the question and the answer;
 * 3. the help for the student (hint, explanation) and the terminal message for a wrong answer.
 * A preview on the right shows how the question will look. What was typed is kept when moving between pages.
 */
public class ProfessorQuestionEditScreen extends ProfessorScreen {

    private static final Color PICKED = new Color(0.2f, 0.42f, 0.78f, 1f);
    private static final Color STEP_DONE = new Color(0.16f, 0.3f, 0.5f, 1f);
    private static final String[] STEPS = { "1   Kind of question", "2   Question and answer", "3   Help and errors" };

    private final QuestionData questionData = new QuestionData();
    private final ClassInfo cls;
    private final Lesson lesson;
    private final Question existing;
    private final Question variantOf;
    private final Group form;
    private final Group preview;

    // what has been typed (kept here so the boxes can be rebuilt for another page)
    private String promptT = "";
    private String contextT = "";
    private String correctT = "";
    private final String[] wrongT = { "", "", "" };
    private String chunkT = "";
    private String hintT = "";
    private String explanationT = "";
    private String errorT = "";

    // the boxes of the page being shown (null when that page is not shown)
    private TextArea promptBox;
    private TextField contextField;
    private TextField correctField;
    private final List<TextField> wrongFields = new ArrayList<>();
    private TextArea chunkBox;
    private TextField hintField;
    private TextArea explanationBox;
    private TextArea errorBox;

    private CTopic topic = CTopic.SYNTAX;
    private Difficulty difficulty = Difficulty.NORMAL;
    private AnswerKind kind = AnswerKind.CHOICE;
    private int page;

    /** existing == null writes a new question. */
    public ProfessorQuestionEditScreen(Main game, String username, ClassInfo cls, Lesson lesson, Question existing) {
        this(game, username, cls, lesson, existing, null, null);
    }

    /**
     * Writes another difficulty level of an existing question: it starts as a copy of {@code variantOf} at
     * {@code newLevel}, and is saved in the same slot, so it adds the same lines of the program but is asked another way.
     */
    public ProfessorQuestionEditScreen(Main game, String username, ClassInfo cls, Lesson lesson, Question variantOf,
            Difficulty newLevel) {
        this(game, username, cls, lesson, null, variantOf, newLevel);
    }

    private ProfessorQuestionEditScreen(Main game, String username, ClassInfo cls, Lesson lesson, Question existing,
            Question variantOf, Difficulty newLevel) {
        super(game, username, (existing != null ? "Edit question" : variantOf != null ? "New difficulty level" : "New question")
                + "  -  " + lesson.label(), () -> game.setScreen(new ProfessorLessonScreen(game, username, cls, lesson)));
        this.cls = cls;
        this.lesson = lesson;
        this.existing = existing;
        this.variantOf = variantOf;

        card(60, 140, 1100, 880);
        form = region();
        card(1190, 140, 670, 880);
        text("Preview", 1220, 155, 400, 40, 2f, AMBER, Align.left);
        preview = region();

        Question source = existing != null ? existing : variantOf;
        if (source != null) {
            QuestionDraft d = source.draft();
            topic = d.topic();
            difficulty = d.difficulty();
            kind = d.kind();
            promptT = d.prompt();
            correctT = d.correct();
            // the second row holds wrong answers, other accepted answers or extra tokens, depending on the kind
            List<String> second = kind == AnswerKind.FILL_BLANK ? d.accepted() : d.distractors();
            for (int i = 0; i < wrongT.length; i++) {
                wrongT[i] = i < second.size() ? second.get(i) : "";
            }
            hintT = d.hint() == null ? "" : d.hint();
            contextT = d.context() == null ? "" : d.context();
            explanationT = d.explanation() == null ? "" : d.explanation();
            errorT = d.errorText() == null ? "" : d.errorText();
            chunkT = d.chunk() == null ? "" : d.chunk();
        }
        if (variantOf != null && newLevel != null) {
            difficulty = newLevel;
        }
        // a question that already has text starts on the page with the words; a new one starts with the kind
        page = source != null ? 1 : 0;
        rebuildForm();
        rebuildPreview();
    }

    /** Copies what is typed in the boxes on screen into the fields above. */
    private void sync() {
        if (promptBox != null) {
            promptT = promptBox.getText();
        }
        if (contextField != null) {
            contextT = contextField.getText();
        }
        if (correctField != null) {
            correctT = correctField.getText();
        }
        for (int i = 0; i < wrongFields.size() && i < wrongT.length; i++) {
            wrongT[i] = wrongFields.get(i).getText();
        }
        if (chunkBox != null) {
            chunkT = chunkBox.getText();
        }
        if (hintField != null) {
            hintT = hintField.getText();
        }
        if (explanationBox != null) {
            explanationT = explanationBox.getText();
        }
        if (errorBox != null) {
            errorT = errorBox.getText();
        }
    }

    private void goTo(int next) {
        sync();
        page = next;
        rebuildForm();
        rebuildPreview();
    }

    // ── the form ─────────────────────────────────────────

    private void rebuildForm() {
        sync();
        promptBox = null;
        contextField = null;
        correctField = null;
        wrongFields.clear();
        chunkBox = null;
        hintField = null;
        explanationBox = null;
        errorBox = null;
        fill(form, () -> {
            for (int i = 0; i < STEPS.length; i++) {
                int step = i;
                solidButton(STEPS[i], 90 + i * 350, 152, 340, 56, i == page ? PICKED : STEP_DONE, () -> goTo(step));
            }
            switch (page) {
                case 0 -> pageKind();
                case 1 -> pageQuestion();
                default -> pageHelp();
            }
            if (page > 0) {
                solidButton("< Back", 90, 952, 170, 56, BUTTON, () -> goTo(page - 1));
            }
            if (page < 2) {
                solidButton("Next >", 280, 952, 170, 56, PICKED, () -> goTo(page + 1));
            }
            solidButton("Save question", 560, 952, 360, 56, GOOD, this::save);
            solidButton("Cancel", 940, 952, 190, 56, BUTTON,
                    () -> game.setScreen(new ProfessorLessonScreen(game, username, cls, lesson)));
        });
    }

    private void heading(String s, float y) {
        text(s, 90, y, 1040, 30, 1.45f, Color.WHITE, Align.left);
    }

    private void help(String s, float y, float h) {
        text(s, 90, y, 1040, h, 1.2f, MUTED, Align.topLeft, Face.SANS).setWrap(true);
    }

    /** Page 1: how the student answers, the topic and the difficulty. */
    private void pageKind() {
        heading("How does the student answer?", 232);
        AnswerKind[] kinds = AnswerKind.values();
        for (int i = 0; i < kinds.length; i++) {
            AnswerKind k = kinds[i];
            float y = 274 + i * 66;
            solidButton(k.label(), 90, y, 290, 54, k == kind ? PICKED : BUTTON, () -> {
                kind = k;
                rebuildForm();
                rebuildPreview();
            });
            text(describe(k), 400, y, 730, 54, 1.25f, k == kind ? Color.WHITE : MUTED, Align.left, Face.SANS);
        }
        heading("Topic: which idea does this question teach?", 626);
        CTopic[] topics = CTopic.values();
        for (int i = 0; i < topics.length; i++) {
            CTopic t = topics[i];
            solidButton(ProfessorLessonScreen.CosmeticTopic.label(t), 90 + i * 171, 666, 163, 48, t == topic ? PICKED : BUTTON, () -> {
                topic = t;
                rebuildForm();
            });
        }
        heading("Difficulty", 736);
        Difficulty[] ds = Difficulty.values();
        for (int i = 0; i < ds.length; i++) {
            Difficulty d = ds[i];
            String name = d.name().charAt(0) + d.name().substring(1).toLowerCase();
            solidButton(name, 90 + i * 350, 776, 340, 48, d == difficulty ? PICKED : BUTTON, () -> {
                difficulty = d;
                rebuildForm();
            });
        }
        help("Next, press Next to write the question and its answer. You can come back to this page at any time.", 850, 60);
    }

    private static String describe(AnswerKind k) {
        return switch (k) {
            case CHOICE -> "The student picks the right token for a blank from a few choices.";
            case FILL_BLANK -> "The student types the missing code themselves. Harder than choices.";
            case TRUE_FALSE -> "A statement about the code. The student says True or False.";
            case ARRANGE -> "The student taps tokens in the right order to build one line.";
            case FIND_BUG -> "The student taps the line of code that has the mistake.";
        };
    }

    /** True/false and find-the-bug questions of a lesson that builds a program say which lines they add. */
    private boolean needsChunk() {
        return lesson.hasProgram() && (kind == AnswerKind.TRUE_FALSE || kind == AnswerKind.FIND_BUG);
    }

    /** Page 2: the question text and the answer, with the boxes the chosen kind needs. */
    private void pageQuestion() {
        help("Kind: " + kind.label() + ".  " + describe(kind), 228, 28);

        heading(switch (kind) {
            case TRUE_FALSE -> "Statement";
            case FIND_BUG -> "Code with the mistake (one line per row)";
            case FILL_BLANK -> "Question (type ___ where the answer goes)";
            case ARRANGE -> "What should the student build?";
            case CHOICE -> "Question (type ___ where the answer goes)";
        }, 268);
        promptBox = textArea(switch (kind) {
            case TRUE_FALSE -> "e.g. Every C program starts running at main.";
            case FIND_BUG -> "int x = 5;\nint *p = x;\nprintf(\"%d\", *p);";
            case FILL_BLANK -> "e.g. for (int i = 0; i < 3; i___)";
            case ARRANGE -> "e.g. Build the line that prints Hi.";
            case CHOICE -> "e.g. printf(\"Hello\")___";
        }, 90, 304, 1040, 90, PixelFonts.faceForPrompt(kind, promptT));
        promptBox.setText(promptT);

        boolean goalRequired = kind == AnswerKind.CHOICE || kind == AnswerKind.FILL_BLANK;
        heading("Goal line" + (goalRequired ? "" : "  (optional)"), 412);
        contextField = textField("What the code is for, so only one answer fits. e.g. Run only when the person is 18 or older",
                false, 90, 448, 1040, 52, Face.SANS);
        contextField.setText(contextT);

        heading(switch (kind) {
            case TRUE_FALSE -> "Is the statement true or false?";
            case FIND_BUG -> "Number of the line that has the bug";
            case FILL_BLANK -> "The answer the student types";
            case ARRANGE -> "The line, token by token (separate with spaces)";
            case CHOICE -> "Correct answer";
        }, 520);
        if (kind == AnswerKind.TRUE_FALSE) {
            boolean isTrue = Challenge.TRUE.equalsIgnoreCase(correctT.trim());
            boolean isFalse = Challenge.FALSE.equalsIgnoreCase(correctT.trim());
            solidButton("True", 90, 556, 510, 52, isTrue ? GOOD : BUTTON, () -> {
                correctT = Challenge.TRUE;
                rebuildForm();
                rebuildPreview();
            });
            solidButton("False", 620, 556, 510, 52, isFalse ? GOOD : BUTTON, () -> {
                correctT = Challenge.FALSE;
                rebuildForm();
                rebuildPreview();
            });
        } else {
            correctField = textField(switch (kind) {
                case FIND_BUG -> "e.g. 2";
                case FILL_BLANK -> "e.g. ++";
                case ARRANGE -> "e.g. printf ( \"Hi\" ) ;";
                default -> "the token that fills the blank, e.g. ;";
            }, false, 90, 556, 1040, 52, Face.MONO);
            correctField.setText(correctT);
        }

        if (needsChunk()) {
            heading(kind == AnswerKind.FIND_BUG ? "The same code with the bug fixed (as it is in the program)"
                    : "The lines of the program this statement is about", 628);
            chunkBox = textArea(kind == AnswerKind.FIND_BUG ? "the code with the bug fixed" : "the lines this statement is about",
                    90, 664, 1040, 90, Face.MONO);
            chunkBox.setText(chunkT);
        } else if (kind == AnswerKind.CHOICE || kind == AnswerKind.FILL_BLANK || kind == AnswerKind.ARRANGE) {
            heading(switch (kind) {
                case FILL_BLANK -> "Other answers that also count  (optional)";
                case ARRANGE -> "Extra tokens that do not belong  (optional, one word each)";
                default -> "Wrong answers  (1 to 3)";
            }, 628);
            for (int i = 0; i < wrongT.length; i++) {
                TextField f = textField(switch (kind) {
                    case FILL_BLANK -> "other answer";
                    case ARRANGE -> "extra token";
                    default -> i == 0 ? "wrong answer" : "wrong answer (optional)";
                }, false, 90 + i * 350, 664, 340, 52, Face.MONO);
                f.setText(wrongT[i]);
                wrongFields.add(f);
            }
        }
        help("Press Refresh preview on the right to see how it looks.", 790, 28);
    }

    /** Page 3: hint, explanation and the terminal message. */
    private void pageHelp() {
        help("None of these is needed to save, except the explanation in a lesson that builds a program.", 228, 28);

        heading("Hint  (optional)", 268);
        hintField = textField("A nudge shown when the student asks for a hint", false, 90, 304, 1040, 52, Face.SANS);
        hintField.setText(hintT);

        heading("Explanation  (shown after the student answers)", 384);
        help("Say why the right answer is right. Needed in a lesson that builds a program.", 416, 26);
        explanationBox = textArea("e.g. #include <stdio.h> loads the library that contains printf. Without it the compiler "
                + "does not know printf.", 90, 448, 1040, 124, Face.SANS);
        explanationBox.setText(explanationT);

        heading("Terminal message  (optional)", 600);
        help("What the student's terminal shows when a wrong answer breaks the code. One message for every wrong answer, "
                + "or one line per answer written  answer :: message  (use  * :: message  for any other).", 632, 56);
        errorBox = textArea("e.g. error: expected ';' before 'return'", 90, 696, 1040, 110, Face.MONO);
        errorBox.setText(errorT);
    }

    private QuestionDraft draft() {
        sync();
        List<String> second = new ArrayList<>();
        for (String w : wrongT) {
            second.add(w);
        }
        boolean other = kind == AnswerKind.FILL_BLANK;
        return new QuestionDraft(topic, difficulty, promptT, correctT, other ? List.of() : second, hintT, kind,
                other ? second : List.of(), chunkT, contextT, explanationT, errorT);
    }

    // ── the preview ──────────────────────────────────────

    private void rebuildPreview() {
        fill(preview, () -> {
            QuestionDraft d = draft().cleaned();
            text(kind.label() + "  -  " + kind.instruction(), 1220, 200, 610, 30, 1.2f, MUTED, Align.left, Face.SANS);
            if (kind != AnswerKind.FIND_BUG) {
                text(d.prompt().isEmpty() ? "(the question will appear here)" : d.prompt(), 1220, 240, 610, 250, 1.5f,
                        d.prompt().isEmpty() ? MUTED : Color.WHITE, Align.topLeft,
                        d.prompt().isEmpty() ? Face.SANS : PixelFonts.faceForPrompt(kind, d.prompt())).setWrap(true);
            }
            String note = switch (kind) {
                case CHOICE -> "Green is the correct answer. In the game the choices are shuffled.";
                case FILL_BLANK -> "The student types the answer. Spaces are ignored, capital letters are not.";
                case TRUE_FALSE -> "Green is the right answer.";
                case ARRANGE -> "The student taps the tokens in order. The tokens start out mixed up.";
                case FIND_BUG -> "The student taps a line. Green is the line with the bug.";
            };
            switch (kind) {
                case CHOICE -> {
                    float y = 570;
                    if (!d.correct().isEmpty()) {
                        solidButton(d.correct(), 1220, y, 610, 56, GOOD, Color.WHITE, Face.MONO, INERT);
                        y += 66;
                    }
                    for (String w : d.distractors()) {
                        solidButton(w, 1220, y, 610, 56, BUTTON, Color.WHITE, Face.MONO, INERT);
                        y += 66;
                    }
                }
                case FILL_BLANK -> {
                    solidButton("(student types here)", 1220, 570, 610, 56, BUTTON, Color.WHITE, Face.SANS, INERT);
                    solidButton(d.correct().isEmpty() ? "(answer)" : d.correct(), 1220, 636, 610, 56, GOOD, Color.WHITE, Face.MONO,
                            INERT);
                    if (!d.accepted().isEmpty()) {
                        text("Also counts: " + String.join("   ", d.accepted()), 1220, 706, 610, 60, 1.3f, MUTED,
                                Align.topLeft, Face.MONO).setWrap(true);
                    }
                }
                case TRUE_FALSE -> {
                    boolean isTrue = Challenge.TRUE.equals(d.correct());
                    boolean isFalse = Challenge.FALSE.equals(d.correct());
                    solidButton("True", 1220, 570, 295, 80, isTrue ? GOOD : BUTTON, Color.WHITE, Face.SANS, INERT);
                    solidButton("False", 1535, 570, 295, 80, isFalse ? GOOD : BUTTON, Color.WHITE, Face.SANS, INERT);
                }
                case ARRANGE -> {
                    List<String> right = AnswerMatcher.splitTokens(d.correct());
                    text("Right order", 1220, 520, 610, 30, 1.2f, MUTED, Align.left, Face.SANS);
                    chipRow(right, 1220, 556, GOOD);
                    List<String> bank = new ArrayList<>(right);
                    bank.addAll(d.distractors());
                    text("What the student picks from", 1220, 680, 610, 30, 1.2f, MUTED, Align.left, Face.SANS);
                    chipRow(bank, 1220, 716, BUTTON);
                }
                case FIND_BUG -> {
                    String[] lines = d.prompt().isEmpty() ? new String[0] : d.prompt().split("\n", -1);
                    if (lines.length == 0) {
                        text("(the code will appear here)", 1220, 240, 610, 40, 1.5f, MUTED, Align.left, Face.SANS);
                    }
                    int bad = -1;
                    try {
                        bad = Integer.parseInt(d.correct());
                    } catch (NumberFormatException ignored) {
                        // not a number yet: no line is marked
                    }
                    for (int i = 0; i < Math.min(lines.length, QuestionDraft.MAX_BUG_LINES); i++) {
                        // left-aligned, so the code's indentation stays visible
                        rect(1220, 240 + i * 52, 610, 46, i + 1 == bad ? GOOD : BUTTON);
                        text(String.valueOf(i + 1), 1230, 240 + i * 52, 40, 46, 1.4f, MUTED, Align.center, Face.MONO);
                        text(lines[i], 1285, 240 + i * 52, 540, 46, 1.4f, Color.WHITE, Align.left, Face.MONO);
                    }
                }
            }
            text(note, 1220, 870, 610, 60, 1.2f, MUTED, Align.topLeft, Face.SANS).setWrap(true);
            solidButton("Refresh preview", 1220, 940, 610, 56, BUTTON, this::rebuildPreview);
        });
    }

    /** A wrapping row of small token buttons for the preview. */
    private void chipRow(List<String> tokens, float x, float y, Color color) {
        float cx = x;
        float cy = y;
        for (String t : tokens) {
            float w = Math.max(60f, t.length() * 15f + 30f);
            if (cx + w > x + 610) {
                cx = x;
                cy += 54f;
            }
            solidButton(t, cx, cy, w, 46, color, Color.WHITE, Face.MONO, INERT);
            cx += w + 10f;
        }
    }

    private void save() {
        QuestionDraft d = draft().cleaned();
        Optional<String> problem = d.problem(lesson.hasProgram());
        if (problem.isPresent()) {
            toast(problem.get(), false);
            rebuildPreview();
            return;
        }
        load("Saving the question...", () -> existing == null ? questionData.add(lesson.stageId(), d, variantOf == null ? 0 : variantOf.slot())
                : questionData.update(existing.id(), d), ok -> {
            if (ok) {
                game.setScreen(new ProfessorLessonScreen(game, username, cls, lesson));
            } else {
                toast("Could not save the question. Check your connection.", false);
            }
        });
    }
}
