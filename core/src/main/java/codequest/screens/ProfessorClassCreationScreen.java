package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.ProfessorData;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.List;

/**
 * Class creation: a details form, the students who belong to the class, and the
 * programming language to teach. Only C is playable today; the others are shown
 * greyed out. Once Gemini is connected, the language will also seed the class's
 * generated dictionary.
 */
public class ProfessorClassCreationScreen extends ProfessorScreen {

    private static final String[] LANGUAGES = { "C", "Python", "C++", "Java" };

    private final ProfessorData data = new ProfessorData();
    private final List<String> students = new ArrayList<>();
    private final TextField sectionField;
    private final TextField courseField;
    private final TextField studentField;
    private final int base;

    public ProfessorClassCreationScreen(Main game, String username) {
        super(game, username, "Create class", () -> game.setScreen(new ProfessorDashboardScreen(game, username)));

        // ── details ──
        card(60, 140, 880, 880);
        text("Class details", 90, 155, 600, 40, 2f, AMBER, Align.left);
        text("Section name", 90, 215, 400, 30, 1.4f, MUTED, Align.left);
        sectionField = textField("e.g. BSCS 1-A", false, 90, 248, 820, 64);
        text("Course code", 90, 335, 400, 30, 1.4f, MUTED, Align.left);
        courseField = textField("e.g. CC102", false, 90, 368, 820, 64);
        courseField.setText("CC102");
        text("Programming language", 90, 455, 600, 30, 1.4f, MUTED, Align.left);
        float lw = 195;
        for (int i = 0; i < LANGUAGES.length; i++) {
            boolean playable = i == 0;
            String language = LANGUAGES[i];
            float x = 90 + i * (lw + 13.3f);
            if (playable) {
                solidButton(LANGUAGES[i], x, 490, lw, 70, new Color(0.55f, 0.37f, 0.08f, 1f), () -> { });
            } else {
                solidButton(LANGUAGES[i], x, 490, lw, 70, new Color(0.14f, 0.16f, 0.21f, 1f), Color.GRAY,
                        () -> toast(language + " is coming soon. C is the only language available for now.", false));
                text("coming soon", x, 565, lw, 24, 1.1f, MUTED, Align.center);
            }
        }
        text("When Gemini is connected, the class dictionary for the chosen language will be generated here.", 90, 640,
                820, 80, 1.3f, MUTED, Align.topLeft).setWrap(true);
        solidButton("Create class", 90, 930, 820, 70, GOOD, this::create);

        // ── students ──
        card(980, 140, 880, 880);
        text("Students in this class", 1010, 155, 700, 40, 2f, AMBER, Align.left);
        studentField = textField("Student username", false, 1010, 215, 560, 64);
        solidButton("Add", 1590, 215, 240, 64, GOOD, this::addStudent);
        base = mark();
        rebuild();
    }

    private void rebuild() {
        clearFrom(base);
        if (students.isEmpty()) {
            text("No students added yet. You can also add them later under Class management.", 1010, 310, 820, 80, 1.5f,
                    MUTED, Align.topLeft).setWrap(true);
            return;
        }
        for (int i = 0; i < Math.min(students.size(), 11); i++) {
            String name = students.get(i);
            float y = 305 + i * 60;
            rect(1010, y, 820, 54, ROW);
            text(name, 1030, y, 500, 54, 1.7f, Color.WHITE, Align.left);
            solidButton("Remove", 1650, y + 4, 160, 46, new Color(0.35f, 0.18f, 0.18f, 1f), () -> {
                students.remove(name);
                rebuild();
            });
        }
        if (students.size() > 11) {
            text("+ " + (students.size() - 11) + " more", 1010, 980, 820, 30, 1.3f, MUTED, Align.left);
        }
    }

    private void addStudent() {
        String name = studentField.getText().trim();
        if (name.isEmpty()) {
            toast("Type the student's username.", false);
            return;
        }
        if (students.contains(name)) {
            toast(name + " is already on the list.", false);
            return;
        }
        switch (data.checkStudent(name)) {
            case ENROLLED -> {
                students.add(name);
                studentField.setText("");
                rebuild();
            }
            case NO_SUCH_STUDENT -> toast("No account named '" + name + "'.", false);
            case NOT_A_STUDENT -> toast("'" + name + "' is not a student account.", false);
            default -> toast("Could not check that username. Check your connection.", false);
        }
    }

    private void create() {
        String section = sectionField.getText().trim();
        String course = courseField.getText().trim();
        if (section.isEmpty()) {
            toast("Give the class a section name.", false);
            return;
        }
        if (course.isEmpty()) {
            course = "CC102";
        }
        long professorId = data.professorId(username);
        var created = professorId < 0 ? java.util.Optional.<ProfessorData.CreatedClass>empty()
                : data.createClass(professorId, section, course, "C", students);
        if (created.isEmpty()) {
            toast("Could not create the class. Check your connection.", false);
            return;
        }
        ClassInfo info = new ClassInfo(created.get().classId(), course, section, "C", created.get().enrolled().size(), 0);
        game.setScreen(new ProfessorSectionScreen(game, username, info));
    }
}
