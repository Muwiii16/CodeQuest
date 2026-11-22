package codequest.screens;

import codequest.Main;
import codequest.content.CTopic;
import codequest.content.DefaultStages;
import codequest.content.GeneratedLesson;
import codequest.content.ScopeCheck;
import codequest.content.QuestionDraft;
import codequest.content.Section;
import codequest.content.Stage;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.Lesson;
import codequest.data.LessonGenerator;
import codequest.data.QuestionData;
import codequest.game.MapTheme;
import codequest.game.SchoolRoomPainter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Stage creation: a details form, a preview of the stage, and a prompt box where the professor describes what they
 * need. "Generate preview with AI" asks Gemini (through the generate-lesson Edge Function, which holds the key) for
 * one C program and the questions that build it; the draft is checked against the same rules as hand-written
 * questions and shown here. Nothing is saved until the professor presses Save lesson, and every question can be
 * rewritten afterwards in the lesson editor. Without a draft the lesson is saved with just the prompt, as before.
 */
public class ProfessorStageCreationScreen extends ProfessorScreen {

    private static final String[] DIFFICULTIES = { "easy", "normal", "hard" };

    private final CampaignData campaigns = new CampaignData();
    private final ClassInfo cls;
    private final TextField titleField;
    private final TextArea promptBox;
    private final int base;
    private int nextNumber = 1;
    private int nextTestNumber = 1;
    /** What is being made: a lesson of the campaign, or a stage only for class tests (students never see it in the campaign). */
    private boolean asTest;
    /** What the AI understood of the request (one sentence) and the topics it will use: shown, never assumed in silence. */
    private String scopeNote = "";
    private Set<CTopic> usedTopics = EnumSet.of(CTopic.SYNTAX);
    /** The request as the AI saw it, with the professor's answers to its follow-up questions (saved as the lesson's prompt). */
    private String fullPrompt;
    // follow-up questions the AI asked because the request was too wide or unclear; the lesson waits for the answers
    private java.util.List<String> followUps = java.util.List.of();
    private String pendingRequest = "";
    private int clarifyRound;
    private TextArea answersBox;
    private String answersText = "";
    /** The topics earlier lessons of this class taught, which this lesson may reuse. */
    private Set<CTopic> taught = EnumSet.noneOf(CTopic.class);
    private String taughtNotes = "";
    private int difficultyIdx = 1;
    /** How the stage looks: the campus map or one of the school rooms. */
    private MapTheme theme = MapTheme.CAMPUS;
    private boolean adaptive = true;
    /** The grading period the new lesson belongs to (starts as the period of the last lesson of the class). */
    private codequest.data.Period period = codequest.data.Period.PRELIMS;
    private GeneratedLesson generated;

    public ProfessorStageCreationScreen(Main game, String username, ClassInfo cls) {
        super(game, username, "Stage creation  -  " + cls.sectionName(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        this.cls = cls;

        card(60, 140, 760, 560);
        text("Details", 90, 155, 400, 40, 2f, AMBER, Align.left);
        text("Title", 90, 215, 400, 30, 1.4f, MUTED, Align.left);
        titleField = textField("e.g. Variables and data types", false, 90, 248, 700, 64);
        card(850, 140, 1010, 560);
        text("Stage preview", 880, 155, 500, 40, 2f, AMBER, Align.left);

        card(60, 730, 1800, 290);
        promptBox = textArea("e.g. Teach "
                + (codequest.content.Language.fromKey(cls.language()) == codequest.content.Language.CPP ? "cout" : "printf")
                + ": printing text, the #include line and return 0. Only that, for first-year students...", 90, 785, 1740, 150);
        solidButton("Generate preview with AI", 90, 945, 760, 60, new Color(0.45f, 0.3f, 0.7f, 1f), this::generate);

        // Everything after this mark is redrawn when the lesson number arrives or a form button is clicked.
        base = mark();
        rebuild();
        load("Loading...", () -> Map.entry(campaigns.lessons(cls.id()), campaigns.tests(cls.id())), both -> {
            java.util.List<Lesson> earlier = both.getKey();
            nextNumber = earlier.size() + 1;
            if (!earlier.isEmpty()) {
                period = earlier.get(earlier.size() - 1).periodEnum();
            }
            nextTestNumber = both.getValue().size() + 1;
            StringBuilder notes = new StringBuilder();
            for (Lesson l : earlier) {
                taught.addAll(l.topicSet());
                notes.append("Lesson ").append(l.number()).append(": ").append(l.title());
                if (!l.topicSet().isEmpty()) {
                    notes.append(" (").append(ScopeCheck.formatTopics(l.topicSet())).append(")");
                }
                if (l.prompt() != null && !l.prompt().isBlank()) {
                    String d = l.prompt().replace('\n', ' ').trim();
                    notes.append(" - ").append(d.length() > 200 ? d.substring(0, 200) : d);
                }
                notes.append('\n');
            }
            taughtNotes = notes.toString();
            rebuild();
        });
    }

    private void rebuild() {
        if (answersBox != null) {
            answersText = answersBox.getText(); // keep what was typed through a rebuild
        }
        answersBox = null;
        clearFrom(base);
        text(Lesson.label(asTest, asTest ? nextTestNumber : nextNumber), 530, 160, 260, 36, 1.7f, MUTED, Align.right);
        text(asTest ? "Describe what the class test should cover" : "Describe what you need for this lesson", 90, 742, 1200, 36,
                1.7f, AMBER, Align.left);
        solidButton(asTest ? "Save class test" : "Save lesson", 1330, 945, 500, 60, GOOD, this::save);
        if (!asTest) {
            // which grading period the lesson belongs to: students see their lessons grouped by it
            text("Period", 1175, 150, 100, 44, 1.3f, MUTED, Align.right);
            float px = 1290;
            for (codequest.data.Period p : codequest.data.Period.values()) {
                solidButton(p.label(), px, 148, 132, 48, p == period ? new Color(0.55f, 0.37f, 0.08f, 1f) : BUTTON, () -> {
                    period = p;
                    rebuild();
                });
                px += 140;
            }
        }

        text(asTest ? "Difficulty of the test" : "Starting difficulty", 90, 335, 400, 30, 1.4f, MUTED, Align.left);
        for (int i = 0; i < DIFFICULTIES.length; i++) {
            int idx = i;
            solidButton(DIFFICULTIES[i].toUpperCase(), 90 + i * 237, 368, 225, 64,
                    i == difficultyIdx ? new Color(0.55f, 0.37f, 0.08f, 1f) : BUTTON, () -> {
                        difficultyIdx = idx;
                        rebuild();
                    });
        }
        text("Adaptive difficulty", 90, 465, 400, 30, 1.4f, MUTED, Align.left);
        if (asTest) {
            // a class test is played at one fixed difficulty by everybody, so there is nothing to adapt
            solidButton("OFF  -  a class test has one fixed difficulty", 90, 498, 700, 64, BUTTON, INERT);
        } else {
            solidButton(adaptive ? "ON  -  adjusts after each section" : "OFF  -  stays fixed", 90, 498, 700, 64,
                    adaptive ? GOOD : BUTTON, () -> {
                        adaptive = !adaptive;
                        rebuild();
                    });
        }

        // lesson or class test: what the professor is making
        text("Use it as", 90, 575, 400, 28, 1.25f, MUTED, Align.left);
        solidButton("Lesson", 90, 604, 340, 52, asTest ? BUTTON : new Color(0.2f, 0.42f, 0.78f, 1f), () -> {
            asTest = false;
            rebuild();
        });
        solidButton("Class test", 450, 604, 340, 52, asTest ? new Color(0.2f, 0.42f, 0.78f, 1f) : BUTTON, () -> {
            asTest = true;
            rebuild();
        });
        text(asTest ? "Class tests only. Hidden from the campaign."
                : "Appears in the campaign and works as a class test.", 90, 664, 700, 28, 1.1f,
                MUTED, Align.left, PixelFonts.Face.SANS);

        if (!followUps.isEmpty()) {
            drawFollowUps();
            return;
        }
        if (generated != null) {
            drawGenerated();
            return;
        }
        int layoutNumber = DefaultStages.layoutFor(asTest ? nextTestNumber : nextNumber);
        Texture mapPicture = theme.isSchool() ? SchoolRoomPainter.texture(theme, layoutNumber) : texture(Art.map(layoutNumber));
        Image map = new Image(new TextureRegionDrawable(new TextureRegion(mapPicture)));
        placeTopLeft(map, 880, 215, 520, 286);
        map.setTouchable(Touchable.disabled);
        stage.addActor(map);
        text("The map is only the setting. What the lesson teaches comes from your description, and the questions are "
                + "built from it (a different number of questions is fine).", 1430, 215, 410, 200, 1.3f, MUTED, Align.topLeft,
                PixelFonts.Face.SANS).setWrap(true);

        // how the stage looks: the campus, or a school room built from the School Assets (same path and tower spots)
        text("Map", 1430, 430, 410, 28, 1.25f, MUTED, Align.left);
        MapTheme[] themes = MapTheme.values();
        for (int i = 0; i < themes.length; i++) {
            MapTheme t = themes[i];
            solidButton(t.label(), 880 + i * 242, 513, 230, 52, t == theme ? new Color(0.2f, 0.42f, 0.78f, 1f) : BUTTON, () -> {
                theme = t;
                rebuild();
            });
        }

        String what = asTest ? "class test" : "lesson";
        text("Describe the " + what + " below, then press \"Generate preview with AI\", or save it and write the questions "
                + "yourself in the editor. The AI uses only what you describe."
                + (taught.isEmpty() ? "" : " Earlier lessons taught "
                        + ScopeCheck.formatTopics(taught).toLowerCase().replace('_', ' ') + "; this " + what + " may reuse them."),
                880, 580, 960, 115, 1.2f, MUTED, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);
    }

    /** The AI's draft: its program on the left, its questions (in the order the student meets them) on the right. */
    private void drawGenerated() {
        text(generated.title(), 880, 205, 960, 34, 1.5f, Color.WHITE, Align.left, PixelFonts.Face.SANS);
        text("Program the lesson builds", 880, 245, 500, 28, 1.1f, MUTED, Align.left, PixelFonts.Face.SANS);
        float y = 275;
        int shown = 0;
        for (String raw : generated.program().split("\n", -1)) {
            if (raw.isBlank()) {
                continue;
            }
            if (shown == 16) {
                text("...", 880, y, 500, 22, 1.0f, MUTED, Align.left, PixelFonts.Face.MONO);
                break;
            }
            text(raw.stripTrailing(), 880, y, 520, 22, 1.0f, new Color(0.75f, 0.95f, 0.75f, 1f), Align.left,
                    PixelFonts.Face.MONO);
            y += 22;
            shown++;
        }
        text(generated.questions().size() + " questions, in order", 1430, 245, 410, 28, 1.1f, MUTED, Align.left,
                PixelFonts.Face.SANS);
        y = 275;
        int n = 1;
        for (QuestionDraft d : generated.questions()) {
            text(n++ + "  " + d.kind().label() + "  (" + d.topic().name().replace('_', ' ').toLowerCase() + ")", 1430, y, 410,
                    26, 1.0f, Color.WHITE, Align.left, PixelFonts.Face.SANS);
            y += 28;
        }
        text("Draft. Save it, then edit questions in the editor.", 880, 632, 960, 28, 1.15f, AMBER,
                Align.left, PixelFonts.Face.SANS);
        if (!scopeNote.isEmpty()) {
            text("The AI understood:  " + scopeNote, 880, 662, 960, 34, 1.05f, MUTED, Align.left, PixelFonts.Face.SANS).setWrap(true);
        }
    }

    private void generate() {
        String request = promptBox.getText().trim();
        if (request.length() < 5) {
            showError("Describe the stage first", "Type what you want to teach in the box at the bottom (at least a few "
                    + "words), then press Generate again.");
            return;
        }
        followUps = java.util.List.of();
        clarifyRound = 0;
        answersText = "";
        check(request, 0);
    }

    /** Step 1: the AI reads the request and either says it is clear or asks what it needs to know (it never assumes). */
    private void check(String request, int round) {
        String notes = taughtNotes;
        load("Reading your description...", () -> new LessonGenerator(codequest.content.Language.fromKey(cls.language())).clarify(request, notes), c -> {
            if (!c.ok()) {
                showError("Could not check your description", c.error());
                return;
            }
            boolean again = !c.clear() && !c.questions().isEmpty() && round < 2;
            if (again) {
                followUps = c.questions();
                pendingRequest = request;
                clarifyRound = round;
                answersText = "";
                scopeNote = c.summary();
                rebuild();
            }
            // never ask again (or stop) without saying why: the AI's own words come first
            if (c.hasProblem()) {
                showError(round == 0 ? "This can't be made into a lesson yet" : "Your answers don't fit the questions",
                        c.problem() + (again ? "\n\nThe question is shown again below. Please answer it." : "\n\nChange your description and try again."));
                return;
            }
            if (again) {
                if (round > 0) {
                    showError("I still need more detail", (c.summary().isEmpty() ? "" : c.summary() + "\n\n")
                            + "Please answer the questions below.");
                }
                return;
            }
            write(request, c.topics(), c.summary());
        });
    }

    /** The right-hand panel while the AI waits for the professor's answers. */
    private void drawFollowUps() {
        text("Before I write this, I need to know:", 880, 205, 960, 36, 1.6f, AMBER, Align.left, PixelFonts.Face.SANS);
        StringBuilder qs = new StringBuilder();
        for (int i = 0; i < followUps.size(); i++) {
            qs.append(i + 1).append(".  ").append(followUps.get(i)).append("\n\n");
        }
        text(qs.toString().trim(), 880, 255, 940, 230, 1.4f, Color.WHITE, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);
        text("Your answers (one line per question is fine)", 880, 495, 940, 26, 1.1f, MUTED, Align.left, PixelFonts.Face.SANS);
        answersBox = textArea("e.g. 1. for and while loops   2. no "
                + (codequest.content.Language.fromKey(cls.language()) == codequest.content.Language.CPP ? "cin" : "scanf")
                + ", just printing", 880, 525, 940, 90, PixelFonts.Face.SANS);
        answersBox.setText(answersText);
        solidButton("Continue", 880, 630, 460, 56, GOOD, this::answered);
        solidButton("Change my description", 1360, 630, 460, 56, BUTTON, () -> {
            followUps = java.util.List.of();
            scopeNote = "";
            rebuild();
        });
    }

    private void answered() {
        String answers = answersBox == null ? answersText : answersBox.getText();
        if (answers.isBlank()) {
            showError("Please answer the questions", "Type your answers in the box first. The AI asks so that it does not "
                    + "have to guess what you meant.");
            return;
        }
        StringBuilder full = new StringBuilder(pendingRequest).append("\n\nThe AI asked, and the professor answered:\n");
        for (String q : followUps) {
            full.append("Q: ").append(q).append('\n');
        }
        full.append("Professor's answers: ").append(answers.trim());
        followUps = java.util.List.of();
        check(full.toString(), clarifyRound + 1);
    }

    /** Step 2: write the lesson from the request (and the answers), using only what it names. */
    private void write(String request, Set<CTopic> named, String summary) {
        String difficulty = DIFFICULTIES[difficultyIdx];
        Set<CTopic> focus = EnumSet.of(CTopic.SYNTAX);
        focus.addAll(named);
        focus.addAll(ScopeCheck.infer(request));
        usedTopics = focus;
        fullPrompt = request;
        scopeNote = (summary.isEmpty() ? "" : summary + "  ") + "Topics: " + describe(focus) + ".";
        Set<CTopic> before = EnumSet.copyOf(taught.isEmpty() ? EnumSet.noneOf(CTopic.class) : taught);
        String notes = taughtNotes;
        load("Writing the lesson (this can take up to a minute)...",
                () -> new LessonGenerator(codequest.content.Language.fromKey(cls.language())).generate(request, difficulty, focus, before, notes), result -> {
            if (!result.ok()) {
                rebuild();
                showError("The AI could not write the lesson", result.error());
                return;
            }
            generated = result.lesson();
            if (titleField.getText().isBlank()) {
                titleField.setText(generated.title());
            }
            toast("Draft ready. Check it, then press Save lesson.", true);
            rebuild();
        });
    }

    /** The topics of a lesson saved without an AI draft: only what the description names (the basics are always allowed). */
    private Set<CTopic> scopeFor(String description) {
        Set<CTopic> out = EnumSet.of(CTopic.SYNTAX);
        out.addAll(ScopeCheck.infer(description));
        return out;
    }

    private static String describe(Set<CTopic> topics) {
        StringBuilder sb = new StringBuilder();
        for (CTopic t : CTopic.values()) {
            if (topics.contains(t)) {
                sb.append(sb.length() == 0 ? "" : ", ").append(ProfessorLessonScreen.CosmeticTopic.label(t));
            }
        }
        return sb.toString();
    }

    private void save() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showError("The title is missing", asTest ? "Add a title, then save."
                    : "Give the lesson a title in the Title box, then save again.");
            return;
        }
        if (asTest) {
            saveNow(title, codequest.data.ProfessorData.LastLesson.NOT_LAST);
            return;
        }
        // students get a certificate only once the professor says the period (or subject) is complete
        showChoice("Is this the last lesson of " + period.label() + "?",
                "Students earn a badge for every lesson. The " + period.label() + " certificate is given only after you "
                        + "confirm the last lesson. Not sure yet? Pick the first answer: you can close the period later in "
                        + "Class management.",
                new String[] { "Not the last", "Last of " + period.label(), "Last of the subject" },
                choice -> saveNow(title, codequest.data.ProfessorData.LastLesson.values()[choice]));
    }

    private void saveNow(String title, codequest.data.ProfessorData.LastLesson lastAnswer) {
        String difficulty = DIFFICULTIES[difficultyIdx];
        String prompt = fullPrompt != null && generated != null ? fullPrompt : promptBox.getText();
        GeneratedLesson draft = generated;
        boolean test = asTest;
        MapTheme look = theme;
        codequest.data.Period savedPeriod = period;
        Set<CTopic> savedTopics = draft != null ? usedTopics : scopeFor(prompt);
        java.util.concurrent.atomic.AtomicBoolean closureFailed = new java.util.concurrent.atomic.AtomicBoolean();
        load("Saving the lesson...", () -> {
            Optional<Lesson> saved = campaigns.createStage(cls.id(), cls.sectionName(), title, difficulty, adaptive && !test, prompt,
                    savedTopics, test, look, savedPeriod);
            if (saved.isPresent() && !test && !new codequest.data.ProfessorData().applyLastLesson(cls.id(), savedPeriod, lastAnswer)) {
                closureFailed.set(true);
            }
            if (saved.isEmpty() || draft == null) {
                return saved.map(l -> Map.entry(l, 0)).orElse(null);
            }
            Lesson lesson = saved.get();
            if (!campaigns.updateProgram(lesson.stageId(), draft.program(), draft.outputs())) {
                return Map.entry(lesson, -1);
            }
            int added = 0;
            QuestionData questions = new QuestionData(codequest.content.Language.fromKey(cls.language()));
            for (QuestionDraft q : draft.questions()) {
                if (questions.add(lesson.stageId(), q)) {
                    added++;
                }
            }
            return Map.entry(lesson.withProgram(draft.program(), draft.outputs()), added);
        }, saved -> {
            if (saved == null) {
                toast("Could not save it. Check your connection.", false);
                return;
            }
            if (closureFailed.get()) {
                toast("The lesson was saved, but its period status was not. Set it in Class management.", false);
            }
            if (draft != null && saved.getValue() < draft.questions().size()) {
                toast("The lesson was saved, but only " + Math.max(0, saved.getValue()) + " of "
                        + draft.questions().size() + " AI questions stored.", false);
            }
            game.setScreen(new ProfessorLessonScreen(game, username, cls, saved.getKey()));
        });
    }
}
