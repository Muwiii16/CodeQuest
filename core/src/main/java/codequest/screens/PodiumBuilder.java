package codequest.screens;

import codequest.data.ClassTestData.Participant;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Align;

import java.util.List;

/**
 * The Kahoot-style podium: the top three rise one after another, third place
 * first, then second, then the winner. Drawn with plain shapes until there is art.
 */
final class PodiumBuilder {

    private static final Color GOLD = new Color(1f, 0.78f, 0.2f, 1f);
    private static final Color SILVER = new Color(0.78f, 0.82f, 0.9f, 1f);
    private static final Color BRONZE = new Color(0.8f, 0.5f, 0.25f, 1f);

    private PodiumBuilder() {
    }

    /**
     * Adds the podium to the screen. ranked is best first; baseYFromTop is the line the pillars stand on;
     * left/width describe the area (the three pillars are centered in it).
     */
    static void add(BaseScreen screen, List<Participant> ranked, float left, float width, float baseYFromTop) {
        float pillarW = Math.min(300f, (width - 40f) / 3f);
        float gap = 20f;
        float center = left + width / 2f;
        // place index (0 = winner) -> x of the pillar, pillar height, color, reveal delay
        int shown = Math.min(3, ranked.size());
        float[] xs = { center - pillarW / 2f, center - pillarW * 1.5f - gap, center + pillarW / 2f + gap };
        float[] heights = { 340f, 250f, 180f };
        Color[] colors = { GOLD, SILVER, BRONZE };
        float[] delays = { 3.2f, 2.0f, 0.8f };
        String[] labels = { "1", "2", "3" };

        for (int i = 0; i < shown; i++) {
            Participant p = ranked.get(i);
            Image pillar = screen.rect(xs[i], baseYFromTop, pillarW, 0, colors[i]);
            float bottomY = BaseScreen.VIRTUAL_HEIGHT - baseYFromTop;
            pillar.setBounds(xs[i], bottomY, pillarW, 0);
            pillar.addAction(Actions.sequence(Actions.delay(delays[i]),
                    Actions.sizeTo(pillarW, heights[i], 0.7f, Interpolation.pow2Out)));

            Label place = screen.text(labels[i], xs[i], baseYFromTop - heights[i], pillarW, heights[i], 7f,
                    new Color(0.1f, 0.12f, 0.2f, 0.85f), Align.top);
            place.getColor().a = 0f;
            place.addAction(Actions.sequence(Actions.delay(delays[i] + 0.6f), Actions.fadeIn(0.3f)));

            Label name = screen.text(p.username(), xs[i] - 20, baseYFromTop - heights[i] - 100, pillarW + 40, 44, 2.3f,
                    Color.WHITE, Align.center);
            name.getColor().a = 0f;
            name.addAction(Actions.sequence(Actions.delay(delays[i] + 0.6f), Actions.fadeIn(0.3f)));

            Label points = screen.text(p.score() + " pts", xs[i] - 20, baseYFromTop - heights[i] - 52, pillarW + 40, 40,
                    1.9f, colors[i], Align.center);
            points.getColor().a = 0f;
            points.addAction(Actions.sequence(Actions.delay(delays[i] + 0.6f), Actions.fadeIn(0.3f)));
        }
        screen.rect(left, baseYFromTop, width, 8, new Color(1f, 1f, 1f, 0.25f));
    }
}
