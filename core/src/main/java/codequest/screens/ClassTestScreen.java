package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData;
import codequest.data.ClassTestData;
import codequest.data.ClassTestData.Participant;
import codequest.data.ClassTestData.Session;
import codequest.data.DatabaseManager;
import codequest.data.Lesson;
import codequest.data.Poller;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class Test Mode, student side: the lobby for a test the professor opened on one
 * of the student's classes. The student clicks Ready; when the professor starts it,
 * a short countdown runs and everyone plays the lesson together.
 */
public class ClassTestScreen extends StudentScreen {

    private record State(List<Session> sessions, List<Participant> participants) {
    }

    private final ClassTestData tests = new ClassTestData();
    private final CampaignData campaigns = new CampaignData();
    private final long studentId;
    private final boolean inAClass;
    private final Poller<State> poller;
    private final Set<Long> joined = ConcurrentHashMap.newKeySet();
    private final int base;

    private String shown = "";
    private boolean starting;
    private float countdown;
    private boolean launching;

    public ClassTestScreen(Main game, String username) {
        super(game, username, "Class Test", () -> game.setScreen(new MainMenuScreen(game, username)));
        studentId = new DatabaseManager().profileId(username);
        List<Long> classIds = new ArrayList<>();
        campaigns.studentClasses(studentId).forEach(c -> classIds.add(c.id()));
        inAClass = !classIds.isEmpty();
        base = mark();
        poller = new Poller<>(() -> {
            List<Session> sessions = tests.openSessions(classIds);
            List<Participant> participants = List.of();
            if (!sessions.isEmpty()) {
                Session s = sessions.get(0);
                if (!joined.contains(s.id()) && tests.join(s.id(), studentId)) {
                    joined.add(s.id());
                }
                participants = tests.participants(s.id());
            }
            return new State(sessions, participants);
        }, 1500);
    }

    @Override
    public void render(float delta) {
        State st = poller.latest();
        if (starting) {
            countdown -= delta;
            if (countdown <= 0 && !launching) {
                launch(st);
            }
        }
        if (launching) {
            return; // the next screen has taken over and this one is disposed: do not draw it
        }
        String sig = signature(st);
        if (!sig.equals(shown)) {
            shown = sig;
            rebuild(st);
            if (launching) {
                return;
            }
        }
        super.render(delta);
    }

    private String signature(State st) {
        if (st == null) {
            return "loading";
        }
        StringBuilder sb = new StringBuilder();
        for (Session s : st.sessions()) {
            sb.append(s.id()).append(s.status());
        }
        for (Participant p : st.participants()) {
            sb.append(p.username()).append(p.ready()).append(p.finished());
        }
        return sb.append(starting).append((int) Math.ceil(Math.max(0, countdown))).toString();
    }

    private Optional<Participant> me(State st) {
        return st.participants().stream().filter(p -> p.studentId() == studentId).findFirst();
    }

    private void rebuild(State st) {
        clearFrom(base);
        card(160, 150, 1600, 840);

        if (st == null) {
            text("Looking for your class test...", 200, 150, 1520, 840, 2f, MUTED, Align.center);
            return;
        }
        if (st.sessions().isEmpty()) {
            text(inAClass ? "No class test is open right now.\nYour professor will open a lobby when it is time."
                    : "You are not in a class yet.\nAsk your professor to add you to their class.", 200, 150, 1520, 840, 2f,
                    MUTED, Align.center);
            return;
        }

        Session session = st.sessions().get(0);
        Optional<Participant> mine = me(st);
        if (session.isActive() && mine.map(Participant::finished).orElse(false)) {
            leaveToResults(session);
            return;
        }

        text(session.sectionName() + "   -   Lesson " + session.lessonNumber() + ": " + session.lessonTitle(), 200, 175,
                1520, 50, 2.4f, Color.WHITE, Align.left);
        text("Everyone plays the same questions at the same time. Difficulty stays fixed.", 200, 235, 1520, 36, 1.5f,
                MUTED, Align.left);

        if (session.isActive()) {
            if (!starting) {
                starting = true;
                countdown = 3f;
            }
            text(String.valueOf((int) Math.ceil(Math.max(0.1f, countdown))), 200, 330, 1520, 300, 14f, GOLD,
                    Align.center);
            text("The test is starting!", 200, 700, 1520, 60, 2.4f, Color.WHITE, Align.center);
            return;
        }

        long ready = st.participants().stream().filter(Participant::ready).count();
        text("In the lobby:  " + st.participants().size() + "      Ready:  " + ready, 200, 300, 900, 40, 1.8f, TEAL,
                Align.left);
        float y = 355;
        for (int i = 0; i < Math.min(st.participants().size(), 8); i++) {
            Participant p = st.participants().get(i);
            rect(200, y, 1000, 56, ROW);
            text(p.username() + (p.studentId() == studentId ? "   (you)" : ""), 220, y, 600, 56, 1.7f, Color.WHITE,
                    Align.left);
            text(p.ready() ? "READY" : "not ready", 900, y, 280, 56, 1.6f,
                    p.ready() ? new Color(0.55f, 0.9f, 0.65f, 1f) : MUTED, Align.right);
            y += 64;
        }

        boolean isReady = mine.map(Participant::ready).orElse(false);
        solidButton(isReady ? "Not ready" : "I'm ready", 1280, 355, 440, 90, isReady ? BUTTON : GOOD, () -> {
            tests.setReady(session.id(), studentId, !isReady);
            shown = "";
        });
        text(isReady ? "Waiting for your professor to start the test..." : "Click Ready when you are prepared.", 1280,
                470, 440, 120, 1.5f, MUTED, Align.topLeft).setWrap(true);
    }

    private void launch(State st) {
        if (st == null || st.sessions().isEmpty()) {
            starting = false;
            return;
        }
        launching = true;
        Session session = st.sessions().get(0);
        Optional<Lesson> lesson = campaigns.lesson(session.stageId());
        if (lesson.isEmpty()) {
            launching = false;
            starting = false;
            toast("Could not load the lesson. Check your connection.", false);
            return;
        }
        game.setScreen(new PlayScreen(game, username, lesson.get(), session));
    }

    private void leaveToResults(Session session) {
        if (!launching) {
            launching = true;
            game.setScreen(new ClassTestResultScreen(game, username, session, null));
        }
    }

    @Override
    public void dispose() {
        poller.close();
        super.dispose();
    }
}
