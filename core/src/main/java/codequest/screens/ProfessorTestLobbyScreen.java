package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData.ClassInfo;
import codequest.data.ClassTestData;
import codequest.data.ClassTestData.Participant;
import codequest.data.ClassTestData.Session;
import codequest.data.Lesson;
import codequest.data.Poller;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.List;
import java.util.Optional;

/**
 * Class Test Mode, professor side: open the lobby for a lesson, watch students join
 * and click Ready, start the test, follow live points, and end it. When the test ends
 * (by hand, when everyone has finished, or at the time limit) the podium opens.
 * Leaving this screen does not stop a running test.
 */
public class ProfessorTestLobbyScreen extends ProfessorScreen {

    private record Live(String status, List<Participant> participants) {
    }

    private final ClassTestData tests = new ClassTestData();
    private final ClassInfo cls;
    private final Lesson lesson;
    private final int base;
    private Session session;
    private Poller<Live> poller;
    private String shown = "";
    private boolean leaving;

    public ProfessorTestLobbyScreen(Main game, String username, ClassInfo cls, Lesson lesson) {
        super(game, username, "Class test  -  " + cls.sectionName() + "  -  Lesson " + lesson.number(),
                () -> game.setScreen(new ProfessorSectionScreen(game, username, cls)));
        this.cls = cls;
        this.lesson = lesson;
        card(60, 140, 1800, 880);
        base = mark();
        load("Opening the lobby...", () -> {
            Optional<Session> open = tests.openSession(cls.id());
            if (open.isPresent() && open.get().stageId() == lesson.stageId()) {
                return open;
            }
            return open.isEmpty() ? tests.openLobby(cls.id(), lesson.stageId()) : Optional.<Session>empty();
        }, opened -> {
            if (opened.isEmpty()) {
                text("Could not open the class test lobby.\nAnother test may already be open on this class, or the server "
                        + "could not be reached.", 100, 140, 1720, 880, 2f, MUTED, Align.center);
                return;
            }
            session = opened.get();
            poller = new Poller<>(() -> {
                List<Participant> ps = tests.participants(session.id());
                String status = tests.session(session.id()).map(s -> tests.endIfOver(s, ps)).orElse("scheduled");
                return new Live(status, ps);
            }, 1500);
        });
    }

    @Override
    public void render(float delta) {
        if (poller != null) {
            Live live = poller.latest();
            if (live != null && "completed".equals(live.status())) {
                if (!leaving) {
                    leaving = true;
                    game.setScreen(new ProfessorTestResultsScreen(game, username, cls, lesson.title(), lesson.number(),
                            live.participants()));
                }
                return; // the results screen has taken over and this one is disposed: do not draw it
            }
            StringBuilder sig = new StringBuilder(live == null ? "loading" : live.status());
            if (live != null) {
                live.participants().forEach(p -> sig.append(p.username()).append(p.ready()).append(p.score())
                        .append(p.finished()));
            }
            if (!sig.toString().equals(shown)) {
                shown = sig.toString();
                rebuild(live);
            }
        }
        super.render(delta);
    }

    private void rebuild(Live live) {
        clearFrom(base);
        if (live == null) {
            text("Opening the lobby...", 100, 140, 1720, 880, 2f, MUTED, Align.center);
            return;
        }
        boolean lobby = "scheduled".equals(live.status());
        List<Participant> ps = live.participants();
        long ready = ps.stream().filter(Participant::ready).count();
        long finished = ps.stream().filter(Participant::finished).count();

        text(lesson.title() + "   -   " + (lobby ? "LOBBY OPEN" : "TEST RUNNING"), 90, 158, 1200, 44, 2.2f,
                lobby ? AMBER : new Color(0.55f, 0.9f, 0.65f, 1f), Align.left);
        text(lobby ? "Students open Class Test in their menu to join. When everyone is ready, start the test."
                : "Points update live. The test ends when everyone has finished, after 20 minutes, or when you end it.",
                90, 212, 1200, 34, 1.5f, MUTED, Align.left);

        // ── participants ──
        float y = 270;
        for (int i = 0; i < Math.min(ps.size(), 10); i++) {
            Participant p = ps.get(i);
            rect(90, y, 1160, 58, ROW);
            text(p.username(), 110, y, 500, 58, 1.7f, Color.WHITE, Align.left);
            String right;
            Color c;
            if (lobby) {
                right = p.ready() ? "READY" : "not ready";
                c = p.ready() ? new Color(0.55f, 0.9f, 0.65f, 1f) : MUTED;
            } else {
                right = p.score() + " pts" + (p.finished() ? "   finished" : "   playing...");
                c = p.finished() ? new Color(0.55f, 0.9f, 0.65f, 1f) : MUTED;
            }
            text(right, 800, y, 430, 58, 1.6f, c, Align.right);
            y += 66;
        }
        if (ps.isEmpty()) {
            text("Nobody has joined yet.", 90, 280, 1100, 40, 1.7f, MUTED, Align.left);
        }

        // ── controls ──
        float x = 1330;
        float w = 490;
        if (lobby) {
            text(ps.size() + " in the lobby\n" + ready + " ready", x, 270, w, 110, 2f, Color.WHITE, Align.topLeft);
            boolean canStart = !ps.isEmpty() && ready == ps.size();
            if (canStart) {
                solidButton("Start test", x, 420, w, 90, GOOD, () -> change("active", "Test started."));
            } else {
                solidButton("Start test", x, 420, w, 90, new Color(0.14f, 0.16f, 0.21f, 1f), Color.GRAY,
                        () -> toast(ps.isEmpty() ? "Wait for at least one student to join."
                                : "Wait until every student in the lobby is ready.", false));
                text(ps.isEmpty() ? "Waiting for students to join." : "Waiting for everyone to be ready.", x, 520, w, 60,
                        1.4f, MUTED, Align.topLeft);
            }
            solidButton("Cancel lobby", x, 610, w, 70, new Color(0.35f, 0.18f, 0.18f, 1f), this::cancelLobby);
        } else {
            text(finished + " of " + ps.size() + " finished", x, 270, w, 60, 2f, Color.WHITE, Align.topLeft);
            solidButton("End test", x, 420, w, 90, new Color(0.5f, 0.2f, 0.2f, 1f),
                    () -> change("completed", "Test ended."));
        }
    }

    /** Closes a lobby that never started and goes back, instead of showing an empty results page. */
    private void cancelLobby() {
        run("Cancelling the lobby...", () -> tests.setStatus(session.id(), "completed"), () -> {
            leaving = true;
            game.setScreen(new ProfessorSectionScreen(game, username, cls));
        });
    }

    private void change(String status, String message) {
        run("Updating the test...", () -> tests.setStatus(session.id(), status), () -> {
            shown = "";
            toast(message, true);
        });
    }

    @Override
    public void dispose() {
        if (poller != null) {
            poller.close();
        }
        super.dispose();
    }
}
