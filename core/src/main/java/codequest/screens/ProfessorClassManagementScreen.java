package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.ProfessorData;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;

import java.util.List;

/**
 * Class management: the class list with add and drop for students, plus renaming the
 * class and deleting it (which also removes its lessons, tests and student results for them).
 */
public class ProfessorClassManagementScreen extends ProfessorScreen {

    private static final int MAX_ROWS = 8;

    private final ProfessorData data = new ProfessorData();
    private ClassInfo cls;
    private final TextField addField;
    private final TextField nameField;
    private final TextField courseField;
    private final int base;
    private long dropArmed = -1;
    private boolean deleteArmed;

    public ProfessorClassManagementScreen(Main game, String username, ClassInfo cls) {
        super(game, username, "Class management  -  " + cls.sectionName(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        this.cls = cls;

        card(60, 140, 1800, 880);
        addField = textField("Student username to add", false, 90, 165, 700, 64);
        solidButton("Add student", 810, 165, 280, 64, GOOD, this::add);
        text("Dropping a student removes them from this class only; their progress and certificates are kept.", 1110,
                160, 720, 80, 1.3f, MUTED, Align.topLeft).setWrap(true);

        // class details and the danger zone sit at the bottom
        rect(90, 800, 1740, 3, new Color(1f, 1f, 1f, 0.12f));
        text("Class details", 90, 815, 400, 36, 1.7f, AMBER, Align.left);
        nameField = textField("Section name", false, 90, 860, 520, 64);
        nameField.setText(cls.sectionName());
        courseField = textField("Course code", false, 630, 860, 300, 64);
        courseField.setText(cls.courseCode());
        solidButton("Save changes", 950, 860, 280, 64, GOOD, this::saveDetails);
        base = mark();
        load("Loading the class...", () -> data.roster(cls.id()), students -> rebuild(students));
    }

    private void refresh() {
        load("Updating...", () -> data.roster(cls.id()), this::rebuild);
    }

    private void rebuild(List<ProfessorData.Student> students) {
        clearFrom(base);
        text(students.size() + (students.size() == 1 ? " student" : " students") + " in this class", 90, 250, 800, 40, 1.8f,
                AMBER, Align.left);
        if (students.isEmpty()) {
            text("Nobody is enrolled yet. Add students by their username.", 90, 305, 1700, 40, 1.6f, MUTED, Align.left);
        }
        for (int i = 0; i < Math.min(students.size(), MAX_ROWS); i++) {
            ProfessorData.Student s = students.get(i);
            float y = 295 + i * 62;
            rect(90, y, 1740, 54, ROW);
            text(s.username(), 110, y, 500, 54, 1.7f, Color.WHITE, Align.left);
            text(s.fullName() == null ? "" : s.fullName(), 640, y, 700, 54, 1.5f, MUTED, Align.left);
            boolean armed = dropArmed == s.id();
            solidButton(armed ? "Click again to drop" : "Drop", 1480, y + 4, 330, 46,
                    armed ? new Color(0.6f, 0.2f, 0.2f, 1f) : new Color(0.35f, 0.18f, 0.18f, 1f), () -> drop(s, students));
        }
        if (students.size() > MAX_ROWS) {
            text("+ " + (students.size() - MAX_ROWS) + " more not shown", 90, 780, 1700, 30, 1.3f, MUTED, Align.left);
        }
        solidButton(deleteArmed ? "Click again: delete the whole class" : "Delete class", 1290, 860, 540, 64,
                deleteArmed ? new Color(0.7f, 0.15f, 0.15f, 1f) : new Color(0.35f, 0.18f, 0.18f, 1f), this::deleteClass);
        if (deleteArmed) {
            text("This deletes the class, its lessons and questions, and the students' results for them.", 1290, 930, 540,
                    60, 1.2f, new Color(1f, 0.6f, 0.55f, 1f), Align.topLeft).setWrap(true);
        }
    }

    private void add() {
        String name = addField.getText().trim();
        if (name.isEmpty()) {
            toast("Type the student's username.", false);
            return;
        }
        load("Adding the student...", () -> data.enrollStudent(cls.id(), name), result -> {
            switch (result) {
                case ENROLLED -> {
                    addField.setText("");
                    refresh();
                    toast(name + " added to " + cls.sectionName() + ".", true);
                }
                case NO_SUCH_STUDENT -> toast("No account named '" + name + "'.", false);
                case NOT_A_STUDENT -> toast("'" + name + "' is not a student account.", false);
                case ALREADY_ENROLLED -> toast(name + " is already in this class.", false);
                default -> toast("Could not add the student. Check your connection.", false);
            }
        });
    }

    private void drop(ProfessorData.Student student, List<ProfessorData.Student> current) {
        if (dropArmed != student.id()) {
            dropArmed = student.id();
            deleteArmed = false;
            rebuild(current);
            return;
        }
        dropArmed = -1;
        load("Dropping the student...", () -> data.dropStudent(cls.id(), student.id()), ok -> {
            refresh();
            toast(ok ? student.username() + " dropped from the class." : "Could not drop the student.", ok);
        });
    }

    private void saveDetails() {
        String section = nameField.getText().trim();
        String course = courseField.getText().trim();
        if (section.isEmpty()) {
            toast("The class needs a section name.", false);
            return;
        }
        String finalCourse = course.isEmpty() ? cls.courseCode() : course;
        load("Saving...", () -> data.updateClass(cls.id(), section, finalCourse), ok -> {
            if (ok) {
                cls = new ClassInfo(cls.id(), finalCourse, section, cls.language(), cls.studentCount(), cls.lessonCount());
                toast("Class details saved.", true);
            } else {
                toast("Could not save. Check your connection.", false);
            }
        });
    }

    private void deleteClass() {
        if (!deleteArmed) {
            deleteArmed = true;
            dropArmed = -1;
            refresh();
            return;
        }
        load("Deleting the class...", () -> data.deleteClass(cls.id()), ok -> {
            if (ok) {
                game.setScreen(new ProfessorDashboardScreen(game, username));
            } else {
                toast("Could not delete the class.", false);
            }
        });
    }
}
