package codequest.screens;

import codequest.Main;
import codequest.data.AwardsData;
import codequest.data.AwardsData.Badge;
import codequest.data.AwardsData.Subject;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.List;
import java.util.Locale;

/**
 * What the student has earned. Every lesson they completed gives a badge, proof of accomplishment they can show their
 * professor. Finishing every lesson of a grading period (Prelims, Midterms, Semis, Finals) gives that period's
 * certificate, and finishing every lesson of the subject gives a second, fancier one. Certificates open and save as PDF.
 */
public class CertificatesScreen extends StudentScreen {

    private static final int PAGE_SIZE = 6;

    private record Loaded(List<Badge> badges, List<Subject> subjects) {
    }

    private List<Badge> badges = List.of();
    private List<Subject> subjects = List.of();
    private boolean showCertificates;
    private final int base;
    private int page;

    public CertificatesScreen(Main game, String username) {
        super(game, username, "Badges and certificates", () -> game.setScreen(new MainMenuScreen(game, username)));
        base = mark();
        load("Loading what you earned...", () -> {
            AwardsData awards = new AwardsData();
            return new Loaded(awards.badges(username), awards.subjects(username));
        }, loaded -> {
            badges = loaded.badges();
            subjects = loaded.subjects();
            // open on the certificates when one is waiting to be seen
            showCertificates = badges.isEmpty() && subjects.stream().anyMatch(s -> s.certificate() != null
                    || s.periods().stream().anyMatch(p -> p.certificate() != null));
            rebuild();
        });
    }

    private void rebuild() {
        clearFrom(base);
        long certs = subjects.stream().mapToLong(s -> (s.certificate() != null ? 1 : 0)
                + s.periods().stream().filter(p -> p.certificate() != null).count()).sum();
        Color on = new Color(0.2f, 0.42f, 0.78f, 1f);
        solidButton("Badges  (" + badges.size() + ")", 160, 130, 330, 52, showCertificates ? BUTTON : on, () -> pick(false));
        solidButton("Certificates  (" + certs + ")", 510, 130, 400, 52, showCertificates ? on : BUTTON, () -> pick(true));
        text(showCertificates ? "One certificate per grading period you finish, and a grand one for the whole subject."
                : "A badge is earned for every lesson you complete. Show it to your professor as proof.", 940, 130, 820, 52,
                1.2f, MUTED, Align.left);
        if (showCertificates) {
            drawCertificates();
        } else {
            drawBadges();
        }
    }

    private void pick(boolean certificates) {
        showCertificates = certificates;
        page = 0;
        rebuild();
    }

    private void drawBadges() {
        if (badges.isEmpty()) {
            card(160, 220, 1600, 200);
            text("No badges yet.\nComplete a lesson to earn your first one.", 200, 220, 1520, 200, 2f, MUTED, Align.center);
            return;
        }
        int from = page * PAGE_SIZE;
        int to = Math.min(badges.size(), from + PAGE_SIZE);
        float y = 210;
        for (int i = from; i < to; i++) {
            Badge b = badges.get(i);
            rect(160, y, 1600, 116, ROW);
            rect(160, y, 8, 116, GOLD);
            text(b.rank(), 190, y, 90, 116, 3.6f, GOLD, Align.center);
            text("Lesson " + b.lessonNumber() + ": " + b.lessonTitle(), 300, y + 14, 1000, 44, 2.2f, Color.WHITE, Align.left)
                    .setEllipsis(true);
            String date = b.earnedAt() != null && b.earnedAt().length() >= 10 ? b.earnedAt().substring(0, 10) : "";
            text(b.period().label() + "  -  " + b.gradePercent() + "%  -  " + b.subject()
                    + (b.section().isBlank() ? "" : " (" + b.section() + ")") + (date.isEmpty() ? "" : "  -  " + date), 300,
                    y + 64, 1050, 34, 1.4f, MUTED, Align.left).setEllipsis(true);
            solidButton("View badge", 1440, y + 28, 280, 60, GOOD, () -> game.setScreen(new BadgeScreen(game, username, b)));
            y += 130;
        }
        pager(badges.size());
    }

    /** One line of the certificates list: the whole subject (period null), or one grading period of it. */
    private record Entry(Subject subject, codequest.data.Period period) {
    }

    private List<Entry> entries() {
        List<Entry> out = new java.util.ArrayList<>();
        for (Subject s : subjects) {
            out.add(new Entry(s, null));
            for (AwardsData.PeriodAward a : s.periods()) {
                out.add(new Entry(s, a.period()));
            }
        }
        return out;
    }

    private void drawCertificates() {
        if (subjects.isEmpty()) {
            card(160, 220, 1600, 200);
            text("You are not in a subject yet. Join a class, then finish its lessons to earn certificates.", 200, 220,
                    1520, 200, 2f, MUTED, Align.center);
            return;
        }
        List<Entry> all = entries();
        int from = page * PAGE_SIZE;
        int to = Math.min(all.size(), from + PAGE_SIZE);
        float y = 210;
        for (int i = from; i < to; i++) {
            Entry e = all.get(i);
            Subject s = e.subject();
            boolean whole = e.period() == null;
            AwardsData.PeriodAward award = whole ? null : s.award(e.period());
            AwardsData.Certificate cert = whole ? s.certificate() : award.certificate();
            boolean earned = cert != null;
            float x = whole ? 160 : 220; // a period sits under its subject, a little to the right
            rect(x, y, 1760 - x, 116, whole ? new Color(0.1f, 0.13f, 0.3f, 1f) : ROW);
            rect(x, y, 8, 116, earned ? GOLD : new Color(0.35f, 0.38f, 0.5f, 1f));
            String title = whole ? s.name() + "  (" + s.section() + ")  -  whole subject"
                    : e.period().label() + "  -  " + s.name() + "  (" + s.section() + ")";
            text(title, x + 40, y + 14, 1100, 44, whole ? 2.3f : 2f, earned ? Color.WHITE : MUTED, Align.left).setEllipsis(true);
            int done = whole ? s.done() : award.done();
            int total = whole ? s.total() : award.total();
            String scope = whole ? "every lesson" : "every " + e.period().label() + " lesson";
            if (earned) {
                String date = cert.issuedAt().length() >= 10 ? cert.issuedAt().substring(0, 10) : "";
                text("Finished " + scope + "  -  Rank " + cert.rank() + "  -  " + String.format(Locale.ROOT, "%.0f", cert.percent())
                        + "%  -  earned " + date + (whole ? "  -  the grand certificate" : ""), x + 40, y + 64, 1100, 34, 1.4f,
                        whole ? GOLD : MUTED, Align.left).setEllipsis(true);
                solidButton("View / Save PDF", 1400, y + 28, 320, 60, GOOD,
                        () -> game.setScreen(new CertificateScreen(game, username, s, e.period(),
                                () -> game.setScreen(new CertificatesScreen(game, username)), "Back")));
            } else {
                text(total == 0 ? "No lessons here yet."
                        : done + " of " + total + " lessons finished  -  finish " + scope + " to earn it", x + 40, y + 64, 1100, 34,
                        1.4f, MUTED, Align.left).setEllipsis(true);
                solidButton("Not yet", 1400, y + 28, 320, 60, new Color(0.2f, 0.22f, 0.3f, 1f), Color.GRAY, INERT);
            }
            y += 130;
        }
        pager(all.size());
    }

    private void pager(int count) {
        if (count <= PAGE_SIZE) {
            return;
        }
        int pages = (count + PAGE_SIZE - 1) / PAGE_SIZE;
        if (page > 0) {
            solidButton("< Prev", 700, 995, 200, 50, BUTTON, () -> {
                page--;
                rebuild();
            });
        }
        text("Page " + (page + 1) + " / " + pages, 920, 995, 200, 50, 1.5f, MUTED, Align.center);
        if (page < pages - 1) {
            solidButton("Next >", 1140, 995, 200, 50, BUTTON, () -> {
                page++;
                rebuild();
            });
        }
    }
}
