package codequest.screens;

import codequest.Main;
import codequest.data.DatabaseManager;
import codequest.data.DatabaseManager.CertificateListing;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import java.util.List;
import java.util.Locale;

/** The certificates the student has earned (rank B or higher on a lesson), each openable and savable as a PDF. */
public class CertificatesScreen extends StudentScreen {

    private static final int PAGE_SIZE = 6;

    private final List<CertificateListing> certificates;
    private final int base;
    private int page;

    public CertificatesScreen(Main game, String username) {
        super(game, username, "Certificates", () -> game.setScreen(new MainMenuScreen(game, username)));
        certificates = new DatabaseManager().listCertificates(username);
        base = mark();
        rebuild();
    }

    private void rebuild() {
        clearFrom(base);
        if (certificates.isEmpty()) {
            card(160, 200, 1600, 200);
            text("No certificates yet.\nFinish a lesson with rank B or higher to earn one.", 200, 200, 1520, 200, 2f,
                    MUTED, Align.center);
            return;
        }
        int from = page * PAGE_SIZE;
        int to = Math.min(certificates.size(), from + PAGE_SIZE);
        float y = 150;
        for (int i = from; i < to; i++) {
            CertificateListing c = certificates.get(i);
            rect(160, y, 1600, 120, ROW);
            rect(160, y, 8, 120, GOLD);
            text("Lesson " + c.lessonNumber() + ": " + c.lessonTitle(), 200, y + 18, 900, 44, 2.2f, Color.WHITE, Align.left);
            String date = c.issuedAt().length() >= 10 ? c.issuedAt().substring(0, 10) : c.issuedAt();
            text("Rank " + c.rank() + "  -  " + String.format(Locale.ROOT, "%.0f", c.scorePercentage()) + "%  -  earned "
                    + date, 200, y + 68, 900, 34, 1.5f, MUTED, Align.left);
            solidButton("View / Save PDF", 1400, y + 30, 320, 60, GOOD,
                    () -> game.setScreen(new CertificateScreen(game, username, c)));
            y += 138;
        }
        if (certificates.size() > PAGE_SIZE) {
            int pages = (certificates.size() + PAGE_SIZE - 1) / PAGE_SIZE;
            if (page > 0) {
                solidButton("< Prev", 700, 985, 200, 56, BUTTON, () -> {
                    page--;
                    rebuild();
                });
            }
            text("Page " + (page + 1) + " / " + pages, 920, 985, 200, 56, 1.5f, MUTED, Align.center);
            if (page < pages - 1) {
                solidButton("Next >", 1140, 985, 200, 56, BUTTON, () -> {
                    page++;
                    rebuild();
                });
            }
        }
    }
}
