package codequest.screens;

import codequest.Main;
import codequest.content.DefaultStages;
import codequest.content.Section;
import codequest.content.Stage;
import codequest.data.CampaignData;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.Lesson;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

/**
 * Stage creation: a details form, a preview of the stage, and a prompt box where
 * the professor describes what they need. Gemini will turn the prompt into the
 * stage's questions and the preview; until it is connected the lesson is saved
 * with the prompt and uses the standard layout and built-in questions.
 */
public class ProfessorStageCreationScreen extends ProfessorScreen {

    private static final String[] DIFFICULTIES = { "easy", "normal", "hard" };

    private final CampaignData campaigns = new CampaignData();
    private final ClassInfo cls;
    private final int nextNumber;
    private final TextField titleField;
    private final TextArea promptBox;
    private final int base;
    private int difficultyIdx = 1;
    private boolean adaptive = true;

    public ProfessorStageCreationScreen(Main game, String username, ClassInfo cls) {
        super(game, username, "Stage creation  -  " + cls.sectionName(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        this.cls = cls;
        this.nextNumber = campaigns.lessons(cls.id()).size() + 1;

        // ── details form ──
        card(60, 140, 760, 560);
        text("Details", 90, 155, 400, 40, 2f, AMBER, Align.left);
        text("Lesson " + nextNumber, 560, 160, 230, 36, 1.7f, MUTED, Align.right);
        text("Lesson title", 90, 215, 400, 30, 1.4f, MUTED, Align.left);
        titleField = textField("e.g. Variables and data types", false, 90, 248, 700, 64);

        // ── preview ──
        card(850, 140, 1010, 560);
        text("Stage preview", 880, 155, 500, 40, 2f, AMBER, Align.left);
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
        text("Standard layout: 4 sections, " + layout.towerCount() + " towers. Once Gemini is connected this preview "
                + "will show the questions it writes from your prompt.", 880, 530, 960, 120, 1.4f, MUTED, Align.topLeft)
                .setWrap(true);

        // ── prompt ──
        card(60, 730, 1800, 290);
        text("Describe what you need for this lesson", 90, 742, 900, 36, 1.7f, AMBER, Align.left);
        promptBox = textArea("e.g. 10 questions on declaring and initializing int, float and char variables, "
                + "starting easy for first-year students...", 90, 785, 1740, 150);
        solidButton("Generate preview with AI  (coming soon)", 90, 945, 760, 60, new Color(0.14f, 0.16f, 0.21f, 1f),
                Color.GRAY, () -> toast("AI generation is planned for a later phase. You can still save the lesson.", false));
        solidButton("Save lesson", 1330, 945, 500, 60, GOOD, this::save);

        // Everything after this mark is the form's buttons, redrawn whenever one is clicked.
        base = mark();
        rebuildForm();
    }

    private void rebuildForm() {
        clearFrom(base);
        text("Starting difficulty", 90, 335, 400, 30, 1.4f, MUTED, Align.left);
        for (int i = 0; i < DIFFICULTIES.length; i++) {
            int idx = i;
            solidButton(DIFFICULTIES[i].toUpperCase(), 90 + i * 237, 368, 225, 64,
                    i == difficultyIdx ? new Color(0.55f, 0.37f, 0.08f, 1f) : BUTTON, () -> {
                        difficultyIdx = idx;
                        rebuildForm();
                    });
        }
        text("Adaptive difficulty", 90, 465, 400, 30, 1.4f, MUTED, Align.left);
        solidButton(adaptive ? "ON  -  adjusts after each section" : "OFF  -  stays fixed", 90, 498, 700, 64,
                adaptive ? GOOD : BUTTON, () -> {
                    adaptive = !adaptive;
                    rebuildForm();
                });
        text("Students unlock lessons in order and earn the lesson's reward when they pass.", 90, 590, 700, 80, 1.3f,
                MUTED, Align.topLeft).setWrap(true);
    }

    private void save() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            toast("Give the lesson a title.", false);
            return;
        }
        Lesson saved = campaigns.createLesson(cls.id(), cls.sectionName(), title, DIFFICULTIES[difficultyIdx], adaptive,
                promptBox.getText()).orElse(null);
        if (saved == null) {
            toast("Could not save the lesson. Check your connection.", false);
            return;
        }
        game.setScreen(new ProfessorSectionScreen(game, username, cls));
    }
}
