package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.ClassTestData.Participant;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.List;

/** The final results of a class test: the podium for the top three and the whole ranking beside it. */
public class ProfessorTestResultsScreen extends ProfessorScreen {

    public ProfessorTestResultsScreen(Main game, String username, ClassInfo cls, String lessonTitle, int lessonNumber,
            List<Participant> ranked) {
        super(game, username, "Class test results  -  " + cls.sectionName() + "  -  Lesson " + lessonNumber,
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        card(60, 140, 1800, 880);
        text(lessonTitle + "   -   TEST ENDED", 90, 158, 1500, 44, 2.2f, MUTED, Align.left);
        text("Points are 500-1000 per correct answer by speed, plus streak bonuses. No rewards are given for class tests.",
                90, 212, 1700, 34, 1.4f, MUTED, Align.left);

        if (ranked.isEmpty()) {
            text("Nobody took this test.", 90, 300, 1700, 40, 1.8f, MUTED, Align.left);
        } else {
            PodiumBuilder.add(this, ranked, 90, 1000, 800);
        }
        text("Full ranking", 1180, 270, 600, 40, 1.9f, AMBER, Align.left);
        float y = 325;
        for (int i = 0; i < Math.min(ranked.size(), 9); i++) {
            Participant p = ranked.get(i);
            rect(1180, y, 640, 56, ROW);
            text("#" + (i + 1), 1195, y, 80, 56, 1.6f, i < 3 ? AMBER : Color.WHITE, Align.left);
            text(p.username(), 1280, y, 330, 56, 1.6f, Color.WHITE, Align.left);
            text(p.score() + " pts" + (p.finished() ? "" : "  (left early)"), 1560, y, 245, 56, 1.4f, MUTED, Align.right);
            y += 62;
        }
        if (ranked.size() > 9) {
            text("+ " + (ranked.size() - 9) + " more", 1180, 890, 640, 30, 1.3f, MUTED, Align.right);
        }
        solidButton("Back to section", 1180, 940, 640, 60, BUTTON,
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
    }
}
