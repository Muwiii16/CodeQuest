package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.JoinCode;
import codequest.data.ProfessorData;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Class creation: a details form, the students who belong to the class, and the
 * programming language to teach: C++ (the pilot language, the default) or C; the others are shown greyed out.
 */
public class ProfessorClassCreationScreen extends ProfessorScreen {

    private final ProfessorData data = new ProfessorData();
    private final List<String> students = new ArrayList<>();
    private final TextField sectionField;
    private final TextField courseField;
    private final TextField subjectField;
    private final TextField termField;
    private final TextField studentField;
    private final int base;
    private String pacing = ClassInfo.SELF_PACED;
    /** The language being chosen: C++ (the pilot) unless the professor picks C. */
    private codequest.content.Language language = codequest.content.Language.PILOT;
    private com.badlogic.gdx.scenes.scene2d.Group languageRegion;

    public ProfessorClassCreationScreen(Main game, String username) {
        super(game, username, "Create class", () -> game.setScreen(new ProfessorDashboardScreen(game, username)));

        // ── details ──
        card(60, 140, 880, 880);
        text("Class details", 90, 155, 600, 40, 2f, AMBER, Align.left);
        text("Section name", 90, 200, 400, 30, 1.4f, MUTED, Align.left);
        sectionField = textField("e.g. BSCS 1-A", false, 90, 230, 820, 60);
        text("Course code", 90, 303, 400, 30, 1.4f, MUTED, Align.left);
        courseField = textField("e.g. CC102", false, 90, 333, 820, 60);
        courseField.setText("CC102");
        text("Subject name (what students see)", 90, 406, 600, 30, 1.4f, MUTED, Align.left);
        subjectField = textField("e.g. Programming 1", false, 90, 436, 820, 60);
        text("Term", 90, 509, 600, 30, 1.4f, MUTED, Align.left);
        termField = textField("e.g. 1st Sem 2026-2027", false, 90, 539, 820, 60);
        termField.setText(codequest.data.Terms.current(java.time.LocalDate.now()));
        text("Programming language", 90, 612, 600, 30, 1.4f, MUTED, Align.left);
        // the language the class teaches: C++ is the pilot language (and the default); C stays for classes that use it
        languageRegion = region();
        drawLanguages();
        // how lessons open (the buttons and their explanation are drawn in rebuild(), so they can show the choice)
        text("How lessons open for students", 90, 735, 600, 30, 1.4f, MUTED, Align.left);
        solidButton("Create class", 90, 930, 820, 70, GOOD, this::create);

        // ── students ──
        card(980, 140, 880, 880);
        text("Students in this class", 1010, 155, 700, 40, 2f, AMBER, Align.left);
        studentField = textField("Student username", false, 1010, 215, 560, 64);
        solidButton("Add", 1590, 215, 240, 64, GOOD, this::addStudent);
        base = mark();
        rebuild();
    }

    /** The language buttons: C++ and C can be chosen, Python and Java are shown as coming soon. */
    private void drawLanguages() {
        fill(languageRegion, this::languageButtons);
    }

    private void languageButtons() {
        Color chosen = new Color(0.55f, 0.37f, 0.08f, 1f);
        Color other = new Color(0.14f, 0.16f, 0.21f, 1f);
        float lw = 195;
        String[] names = { "C++", "C", "Python", "Java" };
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            float x = 90 + i * (lw + 13.3f);
            boolean playable = i < 2;
            if (playable) {
                codequest.content.Language l = codequest.content.Language.fromKey(name);
                boolean on = l == language;
                solidButton(name, x, 642, lw, 56, on ? chosen : other, on ? Color.WHITE : Color.GRAY, () -> {
                    language = l;
                    drawLanguages();
                });
                text(l == codequest.content.Language.PILOT ? "pilot language" : "existing classes", x, 699, lw, 22, 1.0f, MUTED,
                        Align.center);
            } else {
                solidButton(name, x, 642, lw, 56, other, Color.GRAY,
                        () -> toast(name + " is coming soon. C++ is the pilot language.", false));
                text("coming soon", x, 699, lw, 22, 1.0f, MUTED, Align.center);
            }
        }
    }

    /** The two ways lessons can open, with the chosen one highlighted and what it means. */
    private void drawPacing() {
        Color chosen = new Color(0.55f, 0.37f, 0.08f, 1f);
        Color other = new Color(0.14f, 0.16f, 0.21f, 1f);
        boolean manual = ClassInfo.MANUAL.equals(pacing);
        solidButton("Self-paced", 90, 765, 400, 56, manual ? other : chosen, manual ? Color.GRAY : Color.WHITE, () -> pickPacing(ClassInfo.SELF_PACED));
        solidButton("Manual", 510, 765, 400, 56, manual ? chosen : other, manual ? Color.WHITE : Color.GRAY, () -> pickPacing(ClassInfo.MANUAL));
        text(manual
                ? "Manual: you unlock each lesson."
                : "Self-paced: a lesson opens after the previous one.",
                90, 835, 820, 80, 1.3f, MUTED, Align.topLeft).setWrap(true);
    }

    private void pickPacing(String choice) {
        pacing = choice;
        rebuild();
    }

    private void rebuild() {
        clearFrom(base);
        drawPacing();
        if (students.isEmpty()) {
            text("No names needed: when you create the class you get a join code. Students enter it in the game to ask to join; "
                    + "you approve each request on your dashboard.\n\nOr add students by username here (or later under Class management).", 1010, 310, 820, 160,
                    1.5f, MUTED, Align.topLeft, PixelFonts.Face.SANS).setWrap(true);
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
        load("Checking the account...", () -> data.checkStudent(name), result -> {
            switch (result) {
                case ENROLLED -> {
                    students.add(name);
                    studentField.setText("");
                    rebuild();
                }
                case NO_SUCH_STUDENT -> toast("No account named '" + name + "'.", false);
                case NOT_A_STUDENT -> toast("'" + name + "' is not a student account.", false);
                default -> toast("Could not check that username. Check your connection.", false);
            }
        });
    }

    private void create() {
        String section = sectionField.getText().trim();
        String course = courseField.getText().trim();
        if (section.isEmpty()) {
            toast("Give the class a section name.", false);
            return;
        }
        String finalCourse = course.isEmpty() ? "CC102" : course;
        String subject = subjectField.getText().trim();
        String term = termField.getText().trim();
        List<String> toEnroll = new ArrayList<>(students);
        String chosenPacing = pacing;
        String chosenLanguage = language.key();
        load("Creating the class...", () -> {
            long professorId = data.professorId(username);
            return professorId < 0 ? Optional.<ProfessorData.CreatedClass>empty()
                    : data.createClass(professorId, section, finalCourse, chosenLanguage, toEnroll, subject, term, chosenPacing);
        }, created -> {
            if (created.isEmpty()) {
                toast("Could not create the class. Check your connection.", false);
                return;
            }
            ClassInfo info = new ClassInfo(created.get().classId(), finalCourse, section, chosenLanguage,
                    created.get().enrolled().size(), 0, subject.isEmpty() ? null : subject, term.isEmpty() ? null : term, false,
                    created.get().joinCode(), chosenPacing);
            String code = JoinCode.display(created.get().joinCode());
            showNotice("Class created", "Join code:   " + code + "\n\nGive this code to your students. In the game they open "
                    + "Campaign, press Join a class and type it, and they send you a join request that you approve on your dashboard (Join requests). You can find the code again at any "
                    + "time under Class management, and replace it with a new one.",
                    () -> game.setScreen(new ProfessorSectionScreen(game, username, info)));
        });
    }
}
