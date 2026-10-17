package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.ProfessorData;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;

import java.util.List;

/** Class management: the class list, with add and drop for students. */
public class ProfessorClassManagementScreen extends ProfessorScreen {

    private static final int MAX_ROWS = 11;

    private final ProfessorData data = new ProfessorData();
    private final ClassInfo cls;
    private final TextField addField;
    private final int base;
    private long dropArmed = -1;

    public ProfessorClassManagementScreen(Main game, String username, ClassInfo cls) {
        super(game, username, "Class management  -  " + cls.sectionName(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        this.cls = cls;

        card(60, 140, 1800, 880);
        addField = textField("Student username to add", false, 90, 165, 700, 64);
        solidButton("Add student", 810, 165, 280, 64, GOOD, this::add);
        text("Dropping a student removes them from this class only; their progress and certificates are kept.", 1110,
                160, 720, 80, 1.3f, MUTED, Align.topLeft).setWrap(true);
        base = mark();
        rebuild();
    }

    private void rebuild() {
        clearFrom(base);
        List<ProfessorData.Student> students = data.roster(cls.id());
        text(students.size() + (students.size() == 1 ? " student" : " students") + " in this class", 90, 255, 800, 40, 1.8f,
                AMBER, Align.left);
        if (students.isEmpty()) {
            text("Nobody is enrolled yet. Add students by their username.", 90, 320, 1700, 40, 1.6f, MUTED, Align.left);
        }
        for (int i = 0; i < Math.min(students.size(), MAX_ROWS); i++) {
            ProfessorData.Student s = students.get(i);
            float y = 310 + i * 60;
            rect(90, y, 1740, 54, ROW);
            text(s.username(), 110, y, 500, 54, 1.7f, Color.WHITE, Align.left);
            text(s.fullName() == null ? "" : s.fullName(), 640, y, 700, 54, 1.5f, MUTED, Align.left);
            boolean armed = dropArmed == s.id();
            solidButton(armed ? "Click again to drop" : "Drop", 1480, y + 4, 330, 46,
                    armed ? new Color(0.6f, 0.2f, 0.2f, 1f) : new Color(0.35f, 0.18f, 0.18f, 1f), () -> drop(s));
        }
        if (students.size() > MAX_ROWS) {
            text("+ " + (students.size() - MAX_ROWS) + " more not shown", 90, 980, 1700, 30, 1.3f, MUTED, Align.left);
        }
    }

    private void add() {
        String name = addField.getText().trim();
        if (name.isEmpty()) {
            toast("Type the student's username.", false);
            return;
        }
        switch (data.enrollStudent(cls.id(), name)) {
            case ENROLLED -> {
                addField.setText("");
                rebuild();
                toast(name + " added to " + cls.sectionName() + ".", true);
            }
            case NO_SUCH_STUDENT -> toast("No account named '" + name + "'.", false);
            case NOT_A_STUDENT -> toast("'" + name + "' is not a student account.", false);
            case ALREADY_ENROLLED -> toast(name + " is already in this class.", false);
            default -> toast("Could not add the student. Check your connection.", false);
        }
    }

    private void drop(ProfessorData.Student student) {
        if (dropArmed != student.id()) {
            dropArmed = student.id();
            rebuild();
            return;
        }
        dropArmed = -1;
        boolean ok = data.dropStudent(cls.id(), student.id());
        rebuild();
        toast(ok ? student.username() + " dropped from the class." : "Could not drop the student.", ok);
    }
}
