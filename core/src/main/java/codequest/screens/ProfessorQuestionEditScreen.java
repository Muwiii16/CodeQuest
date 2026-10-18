package codequest.screens;

import codequest.Main;
import codequest.content.CTopic;
import codequest.content.Difficulty;
import codequest.content.QuestionDraft;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.Lesson;
import codequest.data.QuestionData;
import codequest.data.QuestionData.Question;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Write a new question or rewrite an existing one: its topic and difficulty, the question text,
 * the correct token, up to three wrong tokens and an optional hint. A preview shows how the answer
 * row will look (the correct token in green).
 */
public class ProfessorQuestionEditScreen extends ProfessorScreen {

    private final QuestionData questionData = new QuestionData();
    private final ClassInfo cls;
    private final Lesson lesson;
    private final Question existing;
    private final TextArea promptBox;
    private final TextField correctField;
    private final List<TextField> wrongFields = new ArrayList<>();
    private final TextField hintField;
    private final Group pickers;
    private final Group preview;
    private CTopic topic = CTopic.SYNTAX;
    private Difficulty difficulty = Difficulty.NORMAL;

    /** existing == null writes a new question. */
    public ProfessorQuestionEditScreen(Main game, String username, ClassInfo cls, Lesson lesson, Question existing) {
        super(game, username, (existing == null ? "New question" : "Edit question") + "  -  Lesson " + lesson.number(),
                () -> game.setScreen(new ProfessorLessonScreen(game, username, cls, lesson)));
        this.cls = cls;
        this.lesson = lesson;
        this.existing = existing;

        card(60, 140, 1100, 880);
        pickers = region();

        text("Question", 90, 356, 400, 30, 1.4f, MUTED, Align.left);
        promptBox = textArea("e.g. printf(\"Hello\")___   (type ___ where the answer goes)", 90, 388, 1040, 150);
        text("Correct answer", 90, 552, 400, 30, 1.4f, MUTED, Align.left);
        correctField = textField("the token that fills the blank, e.g. ;", false, 90, 584, 1040, 56);
        text("Wrong answers (1 to 3)", 90, 654, 500, 30, 1.4f, MUTED, Align.left);
        for (int i = 0; i < QuestionDraft.MAX_DISTRACTORS; i++) {
            wrongFields.add(textField(i == 0 ? "wrong answer" : "wrong answer (optional)", false,
                    90 + i * 350, 686, 340, 56));
        }
        text("Hint (optional)", 90, 758, 400, 30, 1.4f, MUTED, Align.left);
        hintField = textField("shown when a student asks for a hint in a lesson", false, 90, 790, 1040, 56);

        solidButton("Save question", 90, 920, 520, 70, GOOD, this::save);
        solidButton("Cancel", 640, 920, 490, 70, BUTTON, () -> game.setScreen(new ProfessorLessonScreen(game, username, cls, lesson)));

        card(1190, 140, 670, 880);
        text("Preview", 1220, 155, 400, 40, 2f, AMBER, Align.left);
        preview = region();

        if (existing != null) {
            QuestionDraft d = existing.draft();
            topic = d.topic();
            difficulty = d.difficulty();
            promptBox.setText(d.prompt());
            correctField.setText(d.correct());
            for (int i = 0; i < wrongFields.size(); i++) {
                wrongFields.get(i).setText(i < d.distractors().size() ? d.distractors().get(i) : "");
            }
            hintField.setText(d.hint() == null ? "" : d.hint());
        }
        rebuildPickers();
        rebuildPreview();
    }

    /** Topic and difficulty rows (redrawn whenever one is picked). */
    private void rebuildPickers() {
        fill(pickers, () -> {
            text("Topic (the tower this question appears at)", 90, 155, 800, 30, 1.4f, MUTED, Align.left);
            CTopic[] topics = CTopic.values();
            for (int i = 0; i < topics.length; i++) {
                CTopic t = topics[i];
                solidButton(ProfessorLessonScreen.CosmeticTopic.label(t), 90 + (i % 3) * 350, 188 + (i / 3) * 52, 340, 46,
                        t == topic ? new Color(0.55f, 0.37f, 0.08f, 1f) : BUTTON, () -> {
                            topic = t;
                            rebuildPickers();
                        });
            }
            Difficulty[] ds = Difficulty.values();
            for (int i = 0; i < ds.length; i++) {
                Difficulty d = ds[i];
                solidButton("difficulty: " + d.name().toLowerCase(), 90 + i * 350, 296, 340, 46,
                        d == difficulty ? new Color(0.55f, 0.37f, 0.08f, 1f) : BUTTON, () -> {
                            difficulty = d;
                            rebuildPickers();
                        });
            }
        });
    }

    private QuestionDraft draft() {
        List<String> wrong = new ArrayList<>();
        wrongFields.forEach(f -> wrong.add(f.getText()));
        return new QuestionDraft(topic, difficulty, promptBox.getText(), correctField.getText(), wrong, hintField.getText());
    }

    private void rebuildPreview() {
        fill(preview, () -> {
            QuestionDraft d = draft().cleaned();
            text(d.prompt().isEmpty() ? "(the question will appear here)" : d.prompt(), 1220, 215, 610, 330, 1.6f,
                    d.prompt().isEmpty() ? MUTED : Color.WHITE, Align.topLeft).setWrap(true);
            float y = 570;
            if (!d.correct().isEmpty()) {
                solidButton(d.correct(), 1220, y, 610, 56, GOOD, () -> { });
                y += 66;
            }
            for (String w : d.distractors()) {
                solidButton(w, 1220, y, 610, 56, BUTTON, () -> { });
                y += 66;
            }
            text("Green is the correct answer. In the game the choices are shuffled.", 1220, 870, 610, 60, 1.3f, MUTED,
                    Align.topLeft).setWrap(true);
            solidButton("Refresh preview", 1220, 940, 610, 56, BUTTON, this::rebuildPreview);
        });
    }

    private void save() {
        QuestionDraft d = draft().cleaned();
        Optional<String> problem = d.problem();
        if (problem.isPresent()) {
            toast(problem.get(), false);
            rebuildPreview();
            return;
        }
        load("Saving the question...", () -> existing == null ? questionData.add(lesson.stageId(), d)
                : questionData.update(existing.id(), d), ok -> {
            if (ok) {
                game.setScreen(new ProfessorLessonScreen(game, username, cls, lesson));
            } else {
                toast("Could not save the question. Check your connection.", false);
            }
        });
    }
}
