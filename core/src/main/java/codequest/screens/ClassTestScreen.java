package codequest.screens;

import codequest.Main;
import codequest.data.CampaignData;
import codequest.data.ClassTestData;
import codequest.data.ClassTestData.Participant;
import codequest.data.ClassTestData.Session;
import codequest.data.DatabaseManager;
import codequest.data.Poller;
import codequest.data.RunCache;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class Test Mode, student side: the lobby for a test the professor opened on one
 * of the student's classes. The student clicks Ready; when the professor starts it,
 * a short countdown runs and everyone plays the lesson together. A test left in
 * progress (the game was closed) offers Resume instead.
 */
public class ClassTestScreen extends StudentScreen {

    private record State(List<Session> sessions, List<Participant> participants) {
    }

    /** The result of the first lookup: who the student is and which classes they are in. */
    private record Init(long id, List<Long> classIds) {
    }

    private final ClassTestData tests = new ClassTestData();
    private final CampaignData campaigns = new CampaignData();
    private final Set<Long> joined = ConcurrentHashMap.newKeySet();
    private final int base;

    private long studentId;
    private boolean inAClass;
    private Poller<State> poller;

    private String shown = "";
    private boolean starting;
    private float countdown;
    private boolean launching;
    private boolean cleaned;

    public ClassTestScreen(Main game, String username) {
        super(game, username, "Class Test", () -> game.setScreen(new MainMenuScreen(game, username)));
        base = mark();
        load("Looking for your class test...", () -> {
            long id = new DatabaseManager().profileId(username);
            List<Long> classIds = new ArrayList<>();
            campaigns.studentClasses(id).forEach(c -> classIds.add(c.id()));
            return new Init(id, classIds);
        }, init -> {
            studentId = init.id();
            inAClass = !init.classIds().isEmpty();
            poller = new Poller<>(() -> {
                List<Session> sessions = tests.openSessions(init.classIds());
                List<Participant> participants = List.of();
                if (!sessions.isEmpty()) {
                    Session s = sessions.get(0);
                    if (!joined.contains(s.id()) && tests.join(s.id(), studentId)) {
                        joined.add(s.id());
                    }
                    participants = tests.participants(s.id());
                    if ("completed".equals(tests.endIfOver(s, participants))) {
                        sessions = new ArrayList<>(sessions);
                        sessions.remove(0); // that test just ended
                        participants = List.of();
                    }
                }
                return new State(sessions, participants);
            }, 1500);
        });
    }

    @Override
    public void render(float delta) {
        if (poller == null) {
            super.render(delta); // still finding out which classes the student is in
            return;
        }
        State st = poller.latest();
        if (starting) {
            countdown -= delta;
            if (countdown <= 0 && !launching) {
                launch(st, false);
            }
        }
        if (launching) {
            return; // the next screen has taken over and this one is disposed: do not draw it
        }
        if (st != null && !cleaned) {
            cleaned = true;
            Set<String> open = new HashSet<>();
            st.sessions().forEach(x -> open.add(RunCache.classTestKey(x.id())));
            RunCache.clearExcept("classtest-", username, open); // saves for tests that are over are no use any more
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

        if (session.isActive() && !starting && RunCache.exists(RunCache.classTestKey(session.id()), username)) {
            text("You left this test in progress", 200, 330, 1520, 70, 3.4f, GOLD, Align.center);
            text("Your answers so far are saved. Pick up where you stopped.", 200, 420, 1520, 40, 1.8f, MUTED,
                    Align.center);
            solidButton("Resume test", 660, 520, 600, 100, GOOD, () -> launch(st, true));
            return;
        }
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
        solidButton(isReady ? "Not ready" : "I'm ready", 1280, 355, 440, 90, isReady ? BUTTON : GOOD,
                () -> run("Updating...", () -> tests.setReady(session.id(), studentId, !isReady), () -> shown = ""));
        text(isReady ? "Waiting for your professor to start the test..." : "Click Ready when you are prepared.", 1280,
                470, 440, 120, 1.5f, MUTED, Align.topLeft).setWrap(true);
    }

    private void launch(State st, boolean resume) {
        if (st == null || st.sessions().isEmpty()) {
            starting = false;
            return;
        }
        launching = true;
        Session session = st.sessions().get(0);
        PlayScreen.open(game, username, null, session, resume, () -> game.setScreen(new ClassTestScreen(game, username)));
    }

    private void leaveToResults(Session session) {
        if (!launching) {
            launching = true;
            game.setScreen(new ClassTestResultScreen(game, username, session, null));
        }
    }

    @Override
    public void dispose() {
        if (poller != null) {
            poller.close();
        }
        super.dispose();
    }
}
