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
 * Write a new question or rewrite an existing one: how it is answered (multiple choice, fill in the blank,
 * true or false, arrange the tokens, find the bug), its topic and difficulty, the question text, the answer
 * and an optional hint. The boxes and labels change with the kind, and a preview shows how it will look.
 */
public class ProfessorQuestionEditScreen extends ProfessorScreen {

    private static final Color PICKED = new Color(0.55f, 0.37f, 0.08f, 1f);

    private final QuestionData questionData = new QuestionData();
    private final ClassInfo cls;
    private final Lesson lesson;
    private final Question existing;
    private final Question variantOf;
    private final TextArea promptBox;
    private final TextField correctField;
    private final TextField contextField;
    private final TextArea explanationBox;
    private final TextField errorField;
    private final List<TextField> wrongFields = new ArrayList<>();
    private final TextField hintField;
    private final Group pickers;
    private final Group cover;
    private final Group chunkRegion;
    private TextArea chunkBox;
    private String chunkText = "";
    private final Group preview;
    private CTopic topic = CTopic.SYNTAX;
    private Difficulty difficulty = Difficulty.NORMAL;
    private AnswerKind kind = AnswerKind.CHOICE;

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
                + "  -  Lesson " + lesson.number(), () -> game.setScreen(new ProfessorLessonScreen(game, username, cls, lesson)));
        this.cls = cls;
        this.lesson = lesson;
        this.existing = existing;
        this.variantOf = variantOf;

        card(60, 140, 1100, 880);
        pickers = region();

        promptBox = textArea("", 90, 466, 1040, 60, Face.MONO);
        contextField = textField("e.g. Run the next lines only when the person is 18 or older (an adult)", false, 90, 550, 1040, 44,
                Face.SANS);
        correctField = textField("", false, 90, 622, 1040, 44, Face.MONO);
        for (int i = 0; i < QuestionDraft.MAX_DISTRACTORS; i++) {
            wrongFields.add(textField("", false, 90 + i * 350, 694, 340, 44, Face.MONO));
        }
        // drawn above the boxes: hides the ones this kind does not use (a hidden box would leave its dark background)
        cover = region();
        chunkRegion = region();
        hintField = textField("shown when a student asks for a hint in a lesson", false, 90, 766, 1040, 38, Face.SANS);
        explanationBox = textArea("shown after the student answers: why the right answer is right", 90, 832, 1040, 44, Face.SANS);
        // the box is only 38 px high: with the default top padding a text area has no room to show even one line
        if (explanationBox.getStyle().background instanceof com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable pad) {
            pad.setTopHeight(3f);
        }
        errorField = textField("e.g. error: expected ';' before '}' token   (or several lines:  answer :: message)", false, 90, 898, 1040, 38,
                Face.MONO);

        solidButton("Save question", 90, 952, 520, 56, GOOD, this::save);
        solidButton("Cancel", 640, 952, 490, 56, BUTTON, () -> game.setScreen(new ProfessorLessonScreen(game, username, cls, lesson)));

        card(1190, 140, 670, 880);
        text("Preview", 1220, 155, 400, 40, 2f, AMBER, Align.left);
        preview = region();

        Question source = existing != null ? existing : variantOf;
        if (source != null) {
            QuestionDraft d = source.draft();
            topic = d.topic();
            difficulty = d.difficulty();
            kind = d.kind();
            promptBox.setText(d.prompt());
            correctField.setText(d.correct());
            // the second row holds wrong answers, other accepted answers or extra tokens, depending on the kind
            List<String> second = kind == AnswerKind.FILL_BLANK ? d.accepted() : d.distractors();
            for (int i = 0; i < wrongFields.size(); i++) {
                wrongFields.get(i).setText(i < second.size() ? second.get(i) : "");
            }
            hintField.setText(d.hint() == null ? "" : d.hint());
            contextField.setText(d.context() == null ? "" : d.context());
            explanationBox.setText(d.explanation() == null ? "" : d.explanation());
            errorField.setText(d.errorText() == null ? "" : d.errorText());
            chunkText = d.chunk() == null ? "" : d.chunk();
        }
        if (variantOf != null && newLevel != null) {
            difficulty = newLevel;
        }
        rebuildPickers();
        rebuildPreview();
    }

    /** Topic, difficulty and kind rows plus the labels and boxes that depend on the kind (redrawn on every pick). */
    private void rebuildPickers() {
        fill(pickers, () -> {
            text("Topic (the tower this question appears at)", 90, 155, 800, 30, 1.25f, MUTED, Align.left, Face.SANS);
            CTopic[] topics = CTopic.values();
            for (int i = 0; i < topics.length; i++) {
                CTopic t = topics[i];
                solidButton(ProfessorLessonScreen.CosmeticTopic.label(t), 90 + (i % 3) * 350, 188 + (i / 3) * 52, 340, 46,
                        t == topic ? PICKED : BUTTON, () -> {
                            topic = t;
                            rebuildPickers();
                        });
            }
            Difficulty[] ds = Difficulty.values();
            for (int i = 0; i < ds.length; i++) {
                Difficulty d = ds[i];
                solidButton("difficulty: " + d.name().toLowerCase(), 90 + i * 350, 296, 340, 46,
                        d == difficulty ? PICKED : BUTTON, () -> {
                            difficulty = d;
                            rebuildPickers();
                        });
            }
            text("How the student answers", 90, 354, 800, 30, 1.25f, MUTED, Align.left, Face.SANS);
            AnswerKind[] kinds = AnswerKind.values();
            for (int i = 0; i < kinds.length; i++) {
                AnswerKind k = kinds[i];
                solidButton(k.label(), 90 + i * 210, 382, 200, 46, k == kind ? PICKED : BUTTON, () -> {
                    kind = k;
                    rebuildPickers();
                    rebuildPreview();
                });
            }
            labelsForKind();
        });
        showFieldsForKind();
        rebuildCover();
        rebuildChunk();
    }

    /** True/false and find-the-bug questions of a lesson that builds a program say which lines they add. */
    private boolean needsChunk() {
        return lesson.hasProgram() && (kind == AnswerKind.TRUE_FALSE || kind == AnswerKind.FIND_BUG);
    }

    /** The box for those lines; it replaces the wrong-answer row, which these kinds do not use. */
    private void rebuildChunk() {
        if (chunkBox != null) {
            chunkText = chunkBox.getText();
        }
        chunkBox = null;
        fill(chunkRegion, () -> {
            if (needsChunk()) {
                chunkBox = textArea(kind == AnswerKind.FIND_BUG ? "the code with the bug fixed, as it is in the program"
                        : "the lines of the program this statement is about", 90, 694, 1040, 46, Face.MONO);
                chunkBox.setText(chunkText);
            }
        });
    }

    /** Covers the boxes this kind does not use, and holds the True / False buttons (which take the place of a box). */
    private void rebuildCover() {
        fill(cover, () -> {
            if (kind == AnswerKind.TRUE_FALSE) {
                rect(90, 622, 1040, 44, CARD);
                boolean isTrue = Challenge.TRUE.equalsIgnoreCase(correctField.getText().trim());
                boolean isFalse = Challenge.FALSE.equalsIgnoreCase(correctField.getText().trim());
                solidButton("True", 90, 622, 510, 44, isTrue ? GOOD : BUTTON, () -> {
                    correctField.setText(Challenge.TRUE);
                    rebuildCover();
                    rebuildPreview();
                });
                solidButton("False", 620, 622, 510, 44, isFalse ? GOOD : BUTTON, () -> {
                    correctField.setText(Challenge.FALSE);
                    rebuildCover();
                    rebuildPreview();
                });
            }
            if (kind == AnswerKind.TRUE_FALSE || kind == AnswerKind.FIND_BUG) {
                rect(90, 694, 1040, 44, CARD);
            }
        });
    }

    /** The captions above the boxes, and the True / False buttons, for the chosen kind. */
    private void labelsForKind() {
        text(switch (kind) {
            case TRUE_FALSE -> "Statement (the student says if it is true)";
            case FIND_BUG -> "Code (one line per row: the student taps the line with the bug)";
            case FILL_BLANK -> "Question (type ___ where the student's answer goes)";
            case ARRANGE -> "Question (say what line to build)";
            case CHOICE -> "Question (type ___ where the answer goes)";
        }, 90, 440, 1040, 28, 1.25f, MUTED, Align.left, Face.SANS);
        text(switch (kind) {
            case TRUE_FALSE -> "Is the statement true or false?";
            case FIND_BUG -> "Number of the line that has the bug";
            case FILL_BLANK -> "The answer the student types";
            case ARRANGE -> "The tokens in the right order, separated by spaces";
            case CHOICE -> "Correct answer";
        }, 90, 600, 1040, 22, 1.2f, MUTED, Align.left, Face.SANS);
        text("What this code is for (one sentence shown above the question; it makes a blank have only one right answer)",
                90, 528, 1040, 22, 1.2f, MUTED, Align.left, Face.SANS);
        String secondLabel = needsChunk() ? "The lines of the program this question adds to the code block" : switch (kind) {
            case CHOICE -> "Wrong answers (1 to 3)";
            case FILL_BLANK -> "Other answers that also count (optional)";
            case ARRANGE -> "Extra tokens that do not belong (optional, one word each)";
            default -> "";
        };
        if (!secondLabel.isEmpty()) {
            text(secondLabel, 90, 672, 1040, 22, 1.2f, MUTED, Align.left, Face.SANS);
        }
        text("Hint (optional)", 90, 744, 400, 22, 1.15f, MUTED, Align.left, Face.SANS);
        text("Explanation  (why the answer is right; needed in a lesson with a program)", 90, 810, 1040, 22, 1.15f, MUTED, Align.left,
                Face.SANS);
        text("Terminal message if a wrong answer breaks the code  (optional; the student sees it in the terminal)", 90, 876, 1040,
                22, 1.15f, MUTED, Align.left, Face.SANS);
    }

    /** Shows only the boxes the kind needs and updates their hint texts. */
    private void showFieldsForKind() {
        setFieldFace(promptBox, PixelFonts.faceForPrompt(kind, promptBox.getText()));
        promptBox.setMessageText(switch (kind) {
            case TRUE_FALSE -> "e.g. Every C program starts running at main.";
            case FIND_BUG -> "int x = 5;\nint *p = x;\nprintf(\"%d\", *p);";
            case FILL_BLANK -> "e.g. for (int i = 0; i < 3; i___)";
            case ARRANGE -> "e.g. Build the line that prints Hi:";
            case CHOICE -> "e.g. printf(\"Hello\")___";
        });
        correctField.setVisible(kind != AnswerKind.TRUE_FALSE);
        correctField.setMessageText(switch (kind) {
            case FIND_BUG -> "e.g. 2";
            case FILL_BLANK -> "e.g. ++";
            case ARRANGE -> "e.g. printf ( \"Hi\" ) ;";
            default -> "the token that fills the blank, e.g. ;";
        });
        boolean secondRow = kind == AnswerKind.CHOICE || kind == AnswerKind.FILL_BLANK || kind == AnswerKind.ARRANGE;
        for (int i = 0; i < wrongFields.size(); i++) {
            TextField f = wrongFields.get(i);
            f.setVisible(secondRow);
            f.setMessageText(switch (kind) {
                case FILL_BLANK -> "other answer (optional)";
                case ARRANGE -> "extra token (optional)";
                default -> i == 0 ? "wrong answer" : "wrong answer (optional)";
            });
        }
    }

    private QuestionDraft draft() {
        List<String> second = new ArrayList<>();
        wrongFields.forEach(f -> second.add(f.getText()));
        boolean other = kind == AnswerKind.FILL_BLANK;
        return new QuestionDraft(topic, difficulty, promptBox.getText(), correctField.getText(),
                other ? List.of() : second, hintField.getText(), kind, other ? second : List.of(),
                chunkBox != null ? chunkBox.getText() : chunkText, contextField.getText(), explanationBox.getText(),
                errorField.getText());
    }

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
                        solidButton(d.correct(), 1220, y, 610, 56, GOOD, Color.WHITE, Face.MONO, () -> { });
                        y += 66;
                    }
                    for (String w : d.distractors()) {
                        solidButton(w, 1220, y, 610, 56, BUTTON, Color.WHITE, Face.MONO, () -> { });
                        y += 66;
                    }
                }
                case FILL_BLANK -> {
                    solidButton("(student types here)", 1220, 570, 610, 56, BUTTON, Color.WHITE, Face.SANS, () -> { });
                    solidButton(d.correct().isEmpty() ? "(answer)" : d.correct(), 1220, 636, 610, 56, GOOD, Color.WHITE, Face.MONO,
                            () -> { });
                    if (!d.accepted().isEmpty()) {
                        text("Also counts: " + String.join("   ", d.accepted()), 1220, 706, 610, 60, 1.3f, MUTED,
                                Align.topLeft, Face.MONO).setWrap(true);
                    }
                }
                case TRUE_FALSE -> {
                    boolean isTrue = Challenge.TRUE.equals(d.correct());
                    boolean isFalse = Challenge.FALSE.equals(d.correct());
                    solidButton("True", 1220, 570, 295, 80, isTrue ? GOOD : BUTTON, Color.WHITE, Face.SANS, () -> { });
                    solidButton("False", 1535, 570, 295, 80, isFalse ? GOOD : BUTTON, Color.WHITE, Face.SANS, () -> { });
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
            solidButton(t, cx, cy, w, 46, color, Color.WHITE, Face.MONO, () -> { });
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
