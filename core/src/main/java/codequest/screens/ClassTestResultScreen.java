package codequest.screens;

import codequest.Main;
import codequest.data.ClassTestData;
import codequest.data.ClassTestData.Participant;
import codequest.data.ClassTestData.Session;
import codequest.data.Poller;
import codequest.session.RunSummary;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.List;
import java.util.Optional;

/**
 * After a class test, the Kahoot way: first a "you finished" card with your points and
 * place while the rest of the class catches up, then, once everyone is done (or the
 * professor ends the test), the podium reveal with the top three and the full ranking.
 * There are no rewards for class tests, only points and ranking.
 */
public class ClassTestResultScreen extends StudentScreen {

    private record Standings(List<Participant> participants, String status) {
    }

    private final Session session;
    private final RunSummary summary;
    private final Poller<Standings> poller;
    private final int base;
    private String shown = "";
    private boolean podiumShown;

    public ClassTestResultScreen(Main game, String username, Session session, RunSummary summary) {
        super(game, username, "Class Test", () -> game.setScreen(new MainMenuScreen(game, username)));
        this.session = session;
        this.summary = summary;
        ClassTestData tests = new ClassTestData();
        base = mark();
        poller = new Poller<>(() -> {
            List<Participant> ps = tests.participants(session.id());
            String status = tests.session(session.id()).map(s -> tests.endIfOver(s, ps)).orElse("active");
            return new Standings(ps, status);
        }, 1500);
    }

    @Override
    public void render(float delta) {
        Standings st = poller.latest();
        if (!podiumShown) {
            boolean over = st != null && ("completed".equals(st.status()) || (!st.participants().isEmpty()
                    && st.participants().stream().allMatch(Participant::finished)));
            if (over) {
                podiumShown = true;
                showPodium(st);
            } else {
                StringBuilder sig = new StringBuilder(st == null ? "loading" : st.status());
                if (st != null) {
                    st.participants().forEach(p -> sig.append(p.username()).append(p.score()).append(p.finished()));
                }
                if (!sig.toString().equals(shown)) {
                    shown = sig.toString();
                    showWaiting(st);
                }
            }
        }
        super.render(delta);
    }

    private Optional<Integer> myPlace(List<Participant> ps) {
        for (int i = 0; i < ps.size(); i++) {
            if (ps.get(i).username().equals(username)) {
                return Optional.of(i + 1);
            }
        }
        return Optional.empty();
    }

    private void showWaiting(Standings st) {
        clearFrom(base);
        card(60, 140, 1800, 880);
        text("You finished!", 90, 160, 1740, 90, 4.5f, Color.WHITE, Align.center);
        int points = summary != null ? summary.score() : st == null ? 0 : st.participants().stream()
                .filter(p -> p.username().equals(username)).mapToInt(Participant::score).findFirst().orElse(0);
        text(points + " points", 90, 270, 1740, 110, 6f, GOLD, Align.center);

        if (st != null) {
            int total = st.participants().size();
            Optional<Integer> place = myPlace(st.participants());
            if (place.isPresent()) {
                text("Right now you are #" + place.get() + " of " + total, 90, 400, 1740, 50, 2.4f, TEAL, Align.center);
            }
            long done = st.participants().stream().filter(Participant::finished).count();
            text("Waiting for your classmates...   " + done + " of " + total + " finished", 90, 470, 1740, 40, 1.8f, MUTED,
                    Align.center);
        }
        if (summary != null) {
            text(summary.correct() + " of " + summary.total() + " correct   -   accuracy "
                    + Math.round(summary.accuracy() * 100) + "%   -   best streak " + summary.bestStreak(), 90, 540, 1740,
                    36, 1.6f, MUTED, Align.center);
        }
        text("The podium appears when everyone is done.", 90, 960, 1740, 30, 1.4f, MUTED, Align.center);

        if (st != null) {
            float y = 610;
            for (int i = 0; i < Math.min(st.participants().size(), 5); i++) {
                Participant p = st.participants().get(i);
                boolean you = p.username().equals(username);
                rect(560, y, 800, 56, you ? new Color(0.1f, 0.3f, 0.34f, 1f) : ROW);
                text("#" + (i + 1), 580, y, 80, 56, 1.7f, i == 0 ? GOLD : Color.WHITE, Align.left);
                text(p.username() + (you ? "  (you)" : ""), 670, y, 420, 56, 1.7f, Color.WHITE, Align.left);
                text(p.score() + " pts" + (p.finished() ? "" : "  ..."), 1100, y, 240, 56, 1.6f,
                        p.finished() ? new Color(0.6f, 0.9f, 0.7f, 1f) : MUTED, Align.right);
                y += 62;
            }
        }
    }

    private void showPodium(Standings st) {
        clearFrom(base);
        card(60, 140, 1800, 880);
        text("Final results", 90, 155, 1740, 70, 3.6f, Color.WHITE, Align.center);

        List<Participant> ranked = st.participants();
        PodiumBuilder.add(this, ranked, 120, 1000, 780);

        // your place, under the podium
        Optional<Integer> place = myPlace(ranked);
        if (place.isPresent()) {
            int me = place.get();
            int myPoints = ranked.get(me - 1).score();
            Color c = me <= 3 ? GOLD : Color.WHITE;
            text("You finished #" + me + " of " + ranked.size() + " with " + myPoints + " points", 90, 830, 1180, 50, 2.1f,
                    c, Align.center);
            if (summary != null) {
                text(summary.correct() + " of " + summary.total() + " correct   -   best streak " + summary.bestStreak(), 90,
                        890, 1180, 36, 1.5f, MUTED, Align.center);
            }
        }
        text("Class tests do not give rewards. Your points and ranking are what count.", 90, 960, 1180, 30, 1.3f, MUTED,
                Align.center);

        // everyone else
        text("Full ranking", 1330, 215, 500, 40, 1.9f, TEAL, Align.left);
        float y = 270;
        for (int i = 0; i < Math.min(ranked.size(), 11); i++) {
            Participant p = ranked.get(i);
            boolean you = p.username().equals(username);
            rect(1330, y, 500, 54, you ? new Color(0.1f, 0.3f, 0.34f, 1f) : ROW);
            text("#" + (i + 1), 1345, y, 70, 54, 1.6f, i < 3 ? GOLD : Color.WHITE, Align.left);
            text(p.username() + (you ? " (you)" : ""), 1420, y, 260, 54, 1.5f, Color.WHITE, Align.left);
            text(String.valueOf(p.score()), 1660, y, 150, 54, 1.5f, MUTED, Align.right);
            y += 60;
        }
    }

    @Override
    public void dispose() {
        poller.close();
        super.dispose();
    }
}
