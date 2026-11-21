package codequest.screens;

import codequest.Main;
import codequest.data.ProfessorData;
import codequest.data.ProfessorData.JoinRequest;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.List;

/**
 * Join requests: students who typed the join code of one of the professor's classes. The code alone does not let them in;
 * the professor approves each one here (second layer of protection), or turns it down.
 */
public class ProfessorJoinRequestsScreen extends ProfessorScreen {

    private static final int ROWS = 8;

    private final ProfessorData data = new ProfessorData();
    private final int base;
    private List<JoinRequest> requests = List.of();

    public ProfessorJoinRequestsScreen(Main game, String username) {
        super(game, username, "Join requests", () -> game.setScreen(new ProfessorDashboardScreen(game, username)));
        card(60, 140, 1800, 880);
        base = mark();
        refresh();
    }

    private void refresh() {
        load("Loading the requests...", data::pendingRequests, list -> {
            requests = list;
            rebuild();
        });
    }

    private void rebuild() {
        clearFrom(base);
        text(requests.isEmpty() ? "No requests waiting"
                : requests.size() + (requests.size() == 1 ? " request waiting" : " requests waiting"), 90, 160, 900, 44, 2f,
                AMBER, Align.left);
        text("A student typed your class's join code. Approve only the students who belong in the class; "
                + "the code alone does not let anyone in.", 90, 212, 1740, 60, 1.3f, MUTED, Align.topLeft,
                PixelFonts.Face.SANS).setWrap(true);
        if (requests.isEmpty()) {
            text("When a student asks to join one of your classes, they appear here.", 90, 330, 1740, 60, 1.6f, MUTED,
                    Align.left, PixelFonts.Face.SANS);
            return;
        }
        for (int i = 0; i < Math.min(requests.size(), ROWS); i++) {
            JoinRequest r = requests.get(i);
            float y = 290 + i * 84;
            rect(90, y, 1740, 74, ROW);
            text(r.username(), 115, y, 380, 74, 1.7f, Color.WHITE, Align.left);
            text(r.fullName() == null ? "" : r.fullName(), 500, y, 480, 74, 1.4f, MUTED, Align.left, PixelFonts.Face.SANS)
                    .setEllipsis(true);
            text(r.classLabel(), 990, y, 480, 74, 1.4f, AMBER, Align.left, PixelFonts.Face.SANS).setEllipsis(true);
            solidButton("Approve", 1480, y + 10, 160, 54, GOOD, () -> decide(r, true));
            solidButton("Reject", 1655, y + 10, 160, 54, new Color(0.35f, 0.18f, 0.18f, 1f), () -> decide(r, false));
        }
        if (requests.size() > ROWS) {
            text("+ " + (requests.size() - ROWS) + " more: they appear as you answer these.", 90, 970, 1740, 30, 1.3f, MUTED,
                    Align.left);
        }
    }

    private void decide(JoinRequest r, boolean approve) {
        load(approve ? "Approving..." : "Rejecting...",
                () -> approve ? data.approve(r.classId(), r.studentId()) : data.reject(r.classId(), r.studentId()), ok -> {
                    if (!ok) {
                        toast("Could not save your answer. Check your connection.", false);
                        return;
                    }
                    toast(approve ? r.username() + " is now in " + r.classLabel() + "." : "Request from " + r.username()
                            + " rejected.", true);
                    refresh();
                });
    }
}
