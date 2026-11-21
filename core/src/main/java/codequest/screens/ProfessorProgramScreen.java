package codequest.screens;

import codequest.Main;
import codequest.content.BuiltInPrograms;
import codequest.content.DefaultStages;
import codequest.content.ProgramCoverage;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.Lesson;
import codequest.data.QuestionData;
import codequest.data.QuestionData.Question;
import codequest.screens.PixelFonts.Face;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.List;

/**
 * The full C program a lesson builds. Its questions are written chunk by chunk in program order, and every answered
 * question adds its chunk to the student's code block. This screen is where the program is written, and it checks that
 * the lesson's questions, in order, rebuild it exactly (which lines are covered, and the first one that does not match).
 */
public class ProfessorProgramScreen extends ProfessorScreen {

    private static final Color TEAL = new Color(0.3f, 0.77f, 0.71f, 1f);
    private static final Color COVERED = new Color(0.6f, 0.95f, 0.6f, 1f);
    private static final Color WRONG = new Color(1f, 0.5f, 0.45f, 1f);
    private static final int MAX_SHOWN = 24;

    private final CampaignData campaigns = new CampaignData();
    private QuestionData questionData = new QuestionData();
    private final ClassInfo cls;
    private Lesson lesson;
    private final TextArea programBox;
    private final TextArea outputsBox;
    private final Group checkRegion;
    private List<Question> questions = List.of();

    public ProfessorProgramScreen(Main game, String username, ClassInfo cls, Lesson lesson) {
        super(game, username, "Program  -  " + lesson.label() + "  " + lesson.title(),
                () -> ProfessorLessonScreen.open(game, username, cls, lesson));
        this.cls = cls;
        this.questionData = new QuestionData(codequest.content.Language.fromKey(cls.language())); // the class language
        this.lesson = lesson;

        card(60, 140, 1000, 880);
        boolean cpp = codequest.content.Language.fromKey(cls.language()) == codequest.content.Language.CPP;
        text("The full " + (cpp ? "C++" : "C") + " program this lesson builds", 90, 155, 940, 40, 2f, AMBER, Align.left, Face.SANS);
        text("Each question adds the next lines to the student's code block. For a small lesson, write several short programs "
                + "and separate them with a line of ---", 90, 196, 940, 44, 1.1f, MUTED, Align.topLeft, Face.SANS).setWrap(true);
        programBox = textArea(cpp ? "#include <iostream>\nusing namespace std;\n\nint main() {\n    ...\n}"
                : "#include <stdio.h>\n\nint main() {\n    ...\n}", 90, 245, 940, 330, Face.MONO);
        programBox.setText(lesson.program() == null ? "" : lesson.program());
        text("What each program prints when it runs (same order, separated by a line of ---). The terminal shows it.", 90, 585,
                940, 26, 1.1f, MUTED, Align.left, Face.SANS);
        outputsBox = textArea("Hello, CodeQuest!", 90, 615, 940, 225, Face.MONO);
        outputsBox.setText(lesson.outputs() == null ? "" : lesson.outputs());
        solidButton("Save program", 90, 870, 450, 70, GOOD, this::save);
        solidButton("Check against the questions", 570, 870, 460, 70, BUTTON, this::rebuildCheck);
        solidButton("Use the built-in program for this lesson's map", 90, 950, 940, 54, BUTTON, this::useBuiltIn);

        card(1090, 140, 770, 880);
        text("What the questions build", 1120, 155, 700, 40, 2f, AMBER, Align.left, Face.SANS);
        checkRegion = region();

        load("Loading the questions...", () -> questionData.list(lesson.stageId()), list -> {
            questions = list;
            rebuildCheck();
        });
    }

    /** Compares the program in the box with the questions' chunks, in order, and shows the result line by line. */
    private void rebuildCheck() {
        String program = programBox.getText();
        ProgramCoverage.Report report = QuestionData.coverage(program, questions);
        fill(checkRegion, () -> {
            boolean ok = report.complete();
            text(questions.isEmpty() ? "This lesson has no questions yet. Add one question per chunk of the program."
                    : ProgramCoverage.describe(report), 1120, 205, 710, 90, 1.3f,
                    ok ? COVERED : report.problem() != null ? WRONG : AMBER, Align.topLeft, Face.SANS).setWrap(true);
            List<String> shown = new ArrayList<>();
            for (String raw : program.split("\n", -1)) {
                if (!raw.trim().isEmpty()) {
                    shown.add(raw.stripTrailing());
                }
            }
            float y = 310;
            int code = 0; // the --- lines between programs are not code and do not count as covered or not
            for (int i = 0; i < Math.min(shown.size(), MAX_SHOWN); i++) {
                if (ProgramCoverage.isMarker(shown.get(i))) {
                    text("--- next program ---", 1120, y, 710, 28, 1.05f, TEAL, Align.left, Face.SANS);
                    y += 28;
                    continue;
                }
                Color c = code < report.coveredLines() ? COVERED
                        : code == report.coveredLines() && report.problem() != null ? WRONG : MUTED;
                text(shown.get(i), 1120, y, 710, 28, 1.15f, c, Align.left, Face.MONO);
                y += 28;
                code++;
            }
            if (shown.size() > MAX_SHOWN) {
                text("... " + (shown.size() - MAX_SHOWN) + " more lines", 1120, y, 710, 28, 1.1f, MUTED, Align.left, Face.SANS);
            }
            text("Green = covered by a question.  Grey = no question yet.  Red = a question that does not match.\n"
                    + "A lesson can have at most " + codequest.content.ProgramChallengeProvider.MAX_SLOTS
                    + " questions (one per tower).", 1120, 950, 710, 60, 1.05f, MUTED, Align.topLeft, Face.SANS).setWrap(true);
        });
    }

    private void save() {
        String program = programBox.getText();
        String outputs = outputsBox.getText();
        load("Saving the program...", () -> campaigns.updateProgram(lesson.stageId(), program, outputs), ok -> {
            if (ok) {
                lesson = lesson.withProgram(program, outputs);
                toast(lesson.hasProgram()
                        ? "Program saved. Lessons with a program are played block by block."
                        : "Program removed. The lesson draws its questions at random again.", true);
                rebuildCheck();
            } else {
                toast("Could not save the program. Check your connection.", false);
            }
        });
    }

    /** Puts the built-in program in the box and, if the lesson has no questions, adds the matching questions. */
    private void useBuiltIn() {
        BuiltInPrograms.Program p = BuiltInPrograms.forLayout(DefaultStages.layoutFor(lesson.number()),
                codequest.content.Language.fromKey(cls.language()));
        boolean empty = questions.isEmpty();
        load("Adding the built-in program...", () -> {
            if (!campaigns.updateProgram(lesson.stageId(), p.text(), p.outputs())) {
                return -1;
            }
            return empty ? questionData.importProgram(lesson.stageId(), p) : 0;
        }, added -> {
            if (added < 0) {
                toast("Could not save. Check your connection.", false);
                return;
            }
            lesson = lesson.withProgram(p.text(), p.outputs());
            programBox.setText(p.text());
            outputsBox.setText(p.outputs() == null ? "" : p.outputs());
            toast(empty ? "Built-in program and " + added + " questions added. Edit any of them."
                    : "Built-in program added. Your existing questions were kept: check they match it.", true);
            load("Loading the questions...", () -> questionData.list(lesson.stageId()), list -> {
                questions = list;
                rebuildCheck();
            });
        });
    }
}
