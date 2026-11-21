package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.JoinCode;
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
    private final TextField subjectField;
    private final TextField termField;
    private boolean ended;
    private final int base;
    private long dropArmed = -1;
    private boolean codeArmed;
    private boolean deleteArmed;

    /** The class as it is now, shared with the Back button so the section screen opens with the saved changes. */
    private final ClassInfo[] current;

    public ProfessorClassManagementScreen(Main game, String username, ClassInfo cls) {
        this(game, username, new ClassInfo[] { cls });
    }

    private ProfessorClassManagementScreen(Main game, String username, ClassInfo[] box) {
        super(game, username, "Class management  -  " + box[0].sectionName(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, box[0])));
        this.current = box;
        this.cls = box[0];

        card(60, 140, 1800, 880);
        addField = textField("Student username to add", false, 90, 165, 700, 64);
        solidButton("Add student", 810, 165, 280, 64, GOOD, this::add);

        // class details and the danger zone sit at the bottom
        rect(90, 800, 1740, 3, new Color(1f, 1f, 1f, 0.12f));
        text("Class details", 90, 815, 400, 36, 1.7f, AMBER, Align.left);
        this.ended = cls.ended();
        nameField = textField("Section", false, 90, 860, 200, 64);
        nameField.setText(cls.sectionName());
        courseField = textField("Course code", false, 300, 860, 150, 64);
        courseField.setText(cls.courseCode());
        subjectField = textField("Subject name", false, 460, 860, 400, 64);
        subjectField.setText(cls.subject() == null ? "" : cls.subject());
        termField = textField("Term", false, 870, 860, 300, 64);
        termField.setText(cls.term() == null ? "" : cls.term());
        solidButton("Save", 1180, 860, 100, 64, GOOD, this::saveDetails);
        base = mark();
        load("Loading the class...", () -> data.roster(cls.id()), students -> rebuild(students));
        if (cls.joinCode() == null || cls.joinCode().isBlank()) {
            // a class opened from somewhere that did not load the code: ask for it
            long classId = cls.id();
            load("Loading the join code...", () -> data.joinCode(classId), code -> {
                setCls(this.cls.withJoinCode(code));
                refresh();
            });
        }
    }

    /** The join code: what students type to enroll themselves, with Copy and a new code if it was shared too widely. */
    private void drawJoinCode() {
        text("Join code  -  students type it to ask to join; you approve them on the dashboard", 1110, 148, 720, 26, 1.1f, MUTED, Align.left,
                PixelFonts.Face.SANS);
        boolean has = cls.joinCode() != null && !cls.joinCode().isBlank();
        text(has ? JoinCode.display(cls.joinCode()) : "...", 1110, 176, 330, 60, 3.2f, new Color(1f, 0.82f, 0.3f, 1f), Align.left,
                PixelFonts.Face.MONO);
        if (!has) {
            return;
        }
        solidButton("Copy", 1450, 172, 150, 56, new Color(0.2f, 0.42f, 0.78f, 1f), () -> {
            com.badlogic.gdx.Gdx.app.getClipboard().setContents(JoinCode.display(cls.joinCode()));
            toast("Join code copied: " + JoinCode.display(cls.joinCode()), true);
        });
        solidButton(codeArmed ? "Sure?" : "New code", 1610, 172, 220, 56,
                codeArmed ? new Color(0.7f, 0.15f, 0.15f, 1f) : BUTTON, this::newCode);
    }

    private void newCode() {
        if (!codeArmed) {
            codeArmed = true;
            toast("Press the button again to replace the code. The old code stops working; enrolled students stay enrolled.",
                    true);
            refresh();
            return;
        }
        codeArmed = false;
        load("Making a new code...", () -> data.regenerateJoinCode(cls.id()), code -> {
            if (code == null) {
                showError("Could not make a new code", "Check your internet connection and try again. The old code still works.");
                refresh();
                return;
            }
            setCls(cls.withJoinCode(code));
            refresh();
            toast("New join code: " + JoinCode.display(code), true);
        });
    }

    private void refresh() {
        load("Updating...", () -> data.roster(cls.id()), this::rebuild);
    }

    private void rebuild(List<ProfessorData.Student> students) {
        clearFrom(base);
        drawJoinCode();
        text(students.size() + (students.size() == 1 ? " student" : " students") + " in this class", 90, 250, 400, 40, 1.8f,
                AMBER, Align.left);
        text("Dropping a student removes them from this class only; progress and certificates are kept.", 520, 240, 500, 50,
                1.05f, MUTED, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);
        solidButton(ended ? "ENDED  -  past subjects (tap to reopen)"
                : "Running  -  tap when finished", 1030, 245, 520, 46, ended ? new Color(0.5f, 0.33f, 0.1f, 1f) : GOOD,
                () -> {
                    ended = !ended;
                    toast(ended ? "Marked as ended. Press Save to keep it." : "Marked as running. Press Save to keep it.", true);
                    refresh();
                });
        // how lessons open: saved at once (it is not part of the details Save)
        solidButton(cls.manual() ? "Lessons: manual unlock" : "Lessons: self-paced", 1560, 245, 270, 46,
                cls.manual() ? new Color(0.5f, 0.33f, 0.1f, 1f) : BUTTON, this::togglePacing);
        if (students.isEmpty()) {
            text("Nobody is enrolled yet. Add students by their username.", 90, 305, 1700, 40, 1.6f, MUTED, Align.left);
        }
        for (int i = 0; i < Math.min(students.size(), MAX_ROWS); i++) {
            ProfessorData.Student s = students.get(i);
            float y = 295 + i * 62;
            rect(90, y, 1740, 54, ROW);
            text(s.username(), 110, y, 500, 54, 1.7f, Color.WHITE, Align.left);
            text(s.fullName() == null ? "" : s.fullName(), 640, y, 620, 54, 1.5f, MUTED, Align.left).setEllipsis(true);
            solidButton("Analysis", 1290, y + 4, 170, 46, new Color(0.2f, 0.42f, 0.78f, 1f),
                    () -> game.setScreen(new ProfessorStudentAnalysisScreen(game, username, cls, s,
                            () -> game.setScreen(new ProfessorClassManagementScreen(game, username, cls)))));
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
        String subject = subjectField.getText().trim();
        String term = termField.getText().trim();
        boolean nowEnded = ended;
        load("Saving...", () -> data.updateClass(cls.id(), section, finalCourse, subject, term, nowEnded), ok -> {
            if (ok) {
                setCls(new ClassInfo(cls.id(), finalCourse, section, cls.language(), cls.studentCount(), cls.lessonCount(),
                        subject.isEmpty() ? null : subject, term.isEmpty() ? null : term, nowEnded, cls.joinCode(), cls.pacing()));
                toast("Class details saved.", true);
            } else {
                toast("Could not save. Check your connection.", false);
            }
        });
    }

    private void setCls(ClassInfo updated) {
        cls = updated;
        current[0] = updated;
    }

    /** Switches between self-paced lessons and lessons the professor unlocks one by one. */
    private void togglePacing() {
        String next = cls.manual() ? ClassInfo.SELF_PACED : ClassInfo.MANUAL;
        load("Saving...", () -> data.updatePacing(cls.id(), next), ok -> {
            if (!ok) {
                toast("Could not change how lessons open. Check your connection.", false);
                return;
            }
            setCls(cls.withPacing(next));
            toast(ClassInfo.MANUAL.equals(next)
                    ? "Manual: students see each lesson locked until you press Unlock in the section."
                    : "Self-paced: lessons open as students finish the one before.", true);
            refresh();
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
