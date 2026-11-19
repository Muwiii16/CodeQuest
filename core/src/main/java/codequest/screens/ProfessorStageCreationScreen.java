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
    /** What this lesson teaches (the professor picks it). A lesson may only use these and what earlier lessons taught. */
    private final Set<CTopic> picked = EnumSet.noneOf(CTopic.class);
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
        promptBox = textArea("e.g. 10 questions on declaring and initializing int, float and char variables, "
                + "starting easy for first-year students...", 90, 785, 1740, 150);
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
        text("This lesson teaches  (it will use only these, plus what earlier lessons taught)", 90, 566, 700, 30, 1.25f, MUTED,
                Align.left, PixelFonts.Face.SANS);
        CTopic[] topicList = CTopic.values();
        for (int i = 0; i < topicList.length; i++) {
            CTopic t = topicList[i];
            boolean on = picked.contains(t);
            solidButton(ProfessorLessonScreen.CosmeticTopic.label(t) + (taught.contains(t) ? "  (taught)" : ""),
                    90 + (i % 3) * 237, 600 + (i / 3) * 52, 228, 44,
                    on ? new Color(0.55f, 0.37f, 0.08f, 1f) : taught.contains(t) ? new Color(0.16f, 0.3f, 0.26f, 1f) : BUTTON,
                    () -> {
                        if (!picked.remove(t)) {
                            picked.add(t);
                        }
                        rebuild();
                    });
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
        float y = 215;
        for (Section s : layout.sections()) {
            StringBuilder topics = new StringBuilder();
            for (var t : s.towerTopics()) {
                topics.append(topics.length() == 0 ? "" : ", ").append(t.name().replace('_', ' ').toLowerCase());
            }
            text("Section " + s.index() + ":  " + topics, 1430, y, 410, 56, 1.3f, Color.WHITE, Align.topLeft).setWrap(true);
            y += 66;
        }
        text("Standard layout: 4 sections, " + layout.towerCount() + " towers. Describe the lesson below and press "
                + "\"Generate preview with AI\" to get a program and its questions, or save the lesson and write the "
                + "questions yourself in the lesson editor.\n\n"
                + (taught.isEmpty() ? "This is the first lesson with topics: the AI will teach only what you pick, nothing else."
                        : "Earlier lessons taught " + ScopeCheck.formatTopics(taught).toLowerCase().replace('_', ' ')
                                + ". This lesson may reuse them, and nothing beyond what you pick."),
                880, 530, 960, 170, 1.4f, MUTED, Align.topLeft).setWrap(true);
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
        if (picked.isEmpty()) {
            toast("Pick what this lesson teaches first, so the AI stays inside it.", false);
            return;
        }
        String difficulty = DIFFICULTIES[difficultyIdx];
        Set<CTopic> focus = EnumSet.copyOf(picked);
        Set<CTopic> before = EnumSet.copyOf(taught.isEmpty() ? EnumSet.noneOf(CTopic.class) : taught);
        String notes = taughtNotes;
        load("Asking Gemini to write the lesson (this can take up to a minute)...",
                () -> new LessonGenerator().generate(request, difficulty, focus, before, notes), result -> {
            if (!result.ok()) {
                toast(result.error(), false);
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

    private void save() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            toast("Give the lesson a title.", false);
            return;
        }
        String difficulty = DIFFICULTIES[difficultyIdx];
        String prompt = promptBox.getText();
        GeneratedLesson draft = generated;
        Set<CTopic> savedTopics = picked.isEmpty() ? Set.of() : EnumSet.copyOf(picked);
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
