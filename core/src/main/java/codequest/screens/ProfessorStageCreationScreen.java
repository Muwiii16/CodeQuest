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
import com.badlogic.gdx.graphics.Color;
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
    private boolean adaptive = true;
    private GeneratedLesson generated;

    public ProfessorStageCreationScreen(Main game, String username, ClassInfo cls) {
        super(game, username, "Stage creation  -  " + cls.sectionName(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        this.cls = cls;

        card(60, 140, 760, 560);
        text("Details", 90, 155, 400, 40, 2f, AMBER, Align.left);
        text("Lesson title", 90, 215, 400, 30, 1.4f, MUTED, Align.left);
        titleField = textField("e.g. Variables and data types", false, 90, 248, 700, 64);
        card(850, 140, 1010, 560);
        text("Stage preview", 880, 155, 500, 40, 2f, AMBER, Align.left);

        card(60, 730, 1800, 290);
        text("Describe what you need for this lesson", 90, 742, 900, 36, 1.7f, AMBER, Align.left);
        promptBox = textArea("e.g. Teach printf: printing text, the #include line and return 0. Only that, for "
                + "first-year students...", 90, 785, 1740, 150);
        solidButton("Generate preview with AI", 90, 945, 760, 60, new Color(0.45f, 0.3f, 0.7f, 1f), this::generate);
        solidButton("Save lesson", 1330, 945, 500, 60, GOOD, this::save);

        // Everything after this mark is redrawn when the lesson number arrives or a form button is clicked.
        base = mark();
        rebuild();
        load("Loading...", () -> campaigns.lessons(cls.id()), earlier -> {
            nextNumber = earlier.size() + 1;
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
        text("Lesson " + nextNumber, 560, 160, 230, 36, 1.7f, MUTED, Align.right);

        text("Starting difficulty", 90, 335, 400, 30, 1.4f, MUTED, Align.left);
        for (int i = 0; i < DIFFICULTIES.length; i++) {
            int idx = i;
            solidButton(DIFFICULTIES[i].toUpperCase(), 90 + i * 237, 368, 225, 64,
                    i == difficultyIdx ? new Color(0.55f, 0.37f, 0.08f, 1f) : BUTTON, () -> {
                        difficultyIdx = idx;
                        rebuild();
                    });
        }
        text("Adaptive difficulty", 90, 465, 400, 30, 1.4f, MUTED, Align.left);
        solidButton(adaptive ? "ON  -  adjusts after each section" : "OFF  -  stays fixed", 90, 498, 700, 64,
                adaptive ? GOOD : BUTTON, () -> {
                    adaptive = !adaptive;
                    rebuild();
                });
        if (!scopeNote.isEmpty()) {
            text("What the AI understood", 90, 575, 700, 30, 1.25f, MUTED, Align.left, PixelFonts.Face.SANS);
            text(scopeNote, 90, 610, 700, 90, 1.3f, Color.WHITE, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);
        } else {
            text("The lesson covers only what you describe. If the description is too wide, the AI asks you before it writes "
                    + "anything.", 90, 580, 700, 100, 1.25f, MUTED, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);
        }

        if (!followUps.isEmpty()) {
            drawFollowUps();
            return;
        }
        if (generated != null) {
            drawGenerated();
            return;
        }
        Stage layout = DefaultStages.forNumber(DefaultStages.layoutFor(nextNumber));
        Image map = new Image(new TextureRegionDrawable(new TextureRegion(texture(Art.map(DefaultStages.layoutFor(nextNumber))))));
        placeTopLeft(map, 880, 215, 520, 293);
        map.setTouchable(Touchable.disabled);
        stage.addActor(map);
        text("The map is only the setting. What the lesson teaches comes from your description, and the questions are "
                + "built from it (a different number of questions is fine).", 1430, 215, 410, 200, 1.3f, MUTED, Align.topLeft,
                PixelFonts.Face.SANS).setWrap(true);
        text("Describe the lesson below and press \"Generate preview with AI\" to get a program and its questions, or "
                + "save the lesson and write the questions yourself in the lesson editor.\n\n"
                + "The AI uses only what you describe. Variables, if/else, loops, functions, arrays or pointers appear "
                + "only if you ask for them (or tick them on the left)."
                + (taught.isEmpty() ? "" : "\n\nEarlier lessons taught "
                        + ScopeCheck.formatTopics(taught).toLowerCase().replace('_', ' ') + "; this lesson may reuse them."),
                880, 530, 960, 190, 1.4f, MUTED, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);
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
            if (shown == 17) {
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
        text("This is a draft. Save lesson stores it; every question can then be rewritten in the lesson editor.", 880, 640,
                960, 50, 1.15f, AMBER, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);
    }

    private void generate() {
        String request = promptBox.getText().trim();
        if (request.length() < 5) {
            toast("Describe what you need first (at least a few words).", false);
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
        load("Reading your description...", () -> new LessonGenerator().clarify(request, notes), c -> {
            if (!c.ok()) {
                toast(c.error(), false);
                return;
            }
            if (!c.clear() && round < 2) {
                followUps = c.questions();
                pendingRequest = request;
                clarifyRound = round;
                answersText = "";
                scopeNote = c.summary();
                rebuild();
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
        answersBox = textArea("e.g. 1. for and while loops   2. no scanf, just printing", 880, 525, 940, 90, PixelFonts.Face.SANS);
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
            toast("Answer the questions first, so the AI does not have to guess.", false);
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
                () -> new LessonGenerator().generate(request, difficulty, focus, before, notes), result -> {
            if (!result.ok()) {
                toast(result.error(), false);
                rebuild();
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
            toast("Give the lesson a title.", false);
            return;
        }
        String difficulty = DIFFICULTIES[difficultyIdx];
        String prompt = fullPrompt != null && generated != null ? fullPrompt : promptBox.getText();
        GeneratedLesson draft = generated;
        Set<CTopic> savedTopics = draft != null ? usedTopics : scopeFor(prompt);
        load("Saving the lesson...", () -> {
            Optional<Lesson> saved = campaigns.createLesson(cls.id(), cls.sectionName(), title, difficulty, adaptive, prompt, savedTopics);
            if (saved.isEmpty() || draft == null) {
                return saved.map(l -> Map.entry(l, 0)).orElse(null);
            }
            Lesson lesson = saved.get();
            if (!campaigns.updateProgram(lesson.stageId(), draft.program(), draft.outputs())) {
                return Map.entry(lesson, -1);
            }
            int added = 0;
            QuestionData questions = new QuestionData();
            for (QuestionDraft q : draft.questions()) {
                if (questions.add(lesson.stageId(), q)) {
                    added++;
                }
            }
            return Map.entry(lesson.withProgram(draft.program(), draft.outputs()), added);
        }, saved -> {
            if (saved == null) {
                toast("Could not save the lesson. Check your connection.", false);
                return;
            }
            if (draft != null && saved.getValue() < draft.questions().size()) {
                toast("The lesson was saved, but only " + Math.max(0, saved.getValue()) + " of "
                        + draft.questions().size() + " AI questions could be stored. Check them in the lesson editor.", false);
            }
            game.setScreen(new ProfessorLessonScreen(game, username, cls, saved.getKey()));
        });
    }
}
