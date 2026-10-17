package codequest.session;

import codequest.content.Challenge;
import codequest.content.ChallengeProvider;
import codequest.content.Difficulty;
import codequest.content.Section;
import codequest.content.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One play-through of a Stage: which tower is next, the player's health and
 * score, the growing code block, and per-section accuracy. Pure rules with no
 * LibGDX dependency, so the whole game logic can be unit tested.
 *
 * Correctness is judged locally and instantly (the token equals the stored
 * correct one) — AI never runs during play. Class Test Mode keeps difficulty
 * fixed; Campaign Mode adapts it after every section.
 */
public class StageRun {

    public enum Mode { CAMPAIGN, CLASS_TEST }

    /** correctToken is always filled in so the UI can show the right answer after a miss. */
    public record Outcome(boolean correct, int damage, String correctToken, SectionResult sectionClosed) {
    }

    public static final int MAX_HP = 100;
    // 20 so that failing is possible on the placeholder 8-tower stage (5 misses); retune with the real stage length.
    public static final int DAMAGE_PER_MISS = 20;
    public static final int POINTS_PER_CORRECT = 10;

    private final Stage stage;
    private final ChallengeProvider provider;
    private final Mode mode;
    private final boolean adaptive;
    private final CodeBlock codeBlock = new CodeBlock();
    private final List<SectionResult> sectionResults = new ArrayList<>();

    private Difficulty difficulty;
    private int sectionIdx = 0;
    private int slotIdx = 0;
    private int hp = MAX_HP;
    private int score = 0;
    private int sectionCorrect = 0;
    private int sectionTotal = 0;
    private int totalCorrect = 0;
    private int totalAnswered = 0;
    private int hintsUsed = 0;
    private Challenge pending;
    private boolean finished = false;
    private boolean failed = false;

    public StageRun(Stage stage, Difficulty startDifficulty, Mode mode, ChallengeProvider provider) {
        this(stage, startDifficulty, mode, provider, mode == Mode.CAMPAIGN);
    }

    /** adaptive = whether difficulty changes between sections (always off in Class Test Mode). */
    public StageRun(Stage stage, Difficulty startDifficulty, Mode mode, ChallengeProvider provider, boolean adaptive) {
        this.adaptive = adaptive && mode == Mode.CAMPAIGN;
        this.stage = stage;
        this.difficulty = startDifficulty;
        this.mode = mode;
        this.provider = provider;
    }

    /** Seconds the player has before the projectile lands. Easier = more time. */
    public static float flightSeconds(Difficulty d) {
        return switch (d) {
            case EASY -> 10f;
            case NORMAL -> 7f;
            case HARD -> 5f;
        };
    }

    /** The challenge for the next tower, or empty once the stage is done or failed. Repeat calls return the same one until answered. */
    public Optional<Challenge> nextChallenge() {
        if (finished || failed) {
            return Optional.empty();
        }
        if (pending != null) {
            return Optional.of(pending);
        }
        while (sectionIdx < stage.sections().size()) {
            Section section = stage.sections().get(sectionIdx);
            if (slotIdx >= section.towerTopics().size()) {
                closeSection();
                continue;
            }
            Optional<Challenge> c = provider.getChallenge(section.towerTopics().get(slotIdx), difficulty);
            if (c.isPresent()) {
                pending = c.get();
                return c;
            }
            slotIdx++;
        }
        finished = true;
        return Optional.empty();
    }

    /** Answer the pending challenge. A null token means the player ran out of time. */
    public Outcome answer(String token) {
        if (pending == null) {
            throw new IllegalStateException("no active challenge");
        }
        Challenge c = pending;
        pending = null;

        boolean correct = token != null && token.equals(c.correctToken());
        int damage = 0;
        if (correct) {
            codeBlock.append(new CodeBlock.Token(token, true));
            score += POINTS_PER_CORRECT;
            sectionCorrect++;
            totalCorrect++;
        } else {
            if (token != null) {
                codeBlock.append(new CodeBlock.Token(token, false));
            }
            hp = Math.max(0, hp - DAMAGE_PER_MISS);
            damage = DAMAGE_PER_MISS;
        }
        sectionTotal++;
        totalAnswered++;
        slotIdx++;

        SectionResult closed = null;
        if (hp == 0) {
            failed = true;
        } else if (slotIdx >= stage.sections().get(sectionIdx).towerTopics().size()) {
            closed = closeSection();
            if (sectionIdx >= stage.sections().size()) {
                finished = true;
            }
        }
        return new Outcome(correct, damage, c.correctToken(), closed);
    }

    public void useHint() {
        hintsUsed++;
    }

    private SectionResult closeSection() {
        Difficulty played = difficulty;
        double accuracy = sectionTotal == 0 ? 0 : (double) sectionCorrect / sectionTotal;
        if (adaptive && sectionTotal > 0) {
            difficulty = AdaptiveDifficulty.next(difficulty, accuracy);
        }
        SectionResult result = new SectionResult(
                stage.sections().get(sectionIdx).index(), sectionCorrect, sectionTotal, accuracy, played, difficulty);
        sectionResults.add(result);
        sectionIdx++;
        slotIdx = 0;
        sectionCorrect = 0;
        sectionTotal = 0;
        return result;
    }

    public double overallAccuracy() {
        return totalAnswered == 0 ? 0 : (double) totalCorrect / totalAnswered;
    }

    public Rank rank() {
        return failed ? Rank.F : RankCalculator.rankFor(overallAccuracy());
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isFailed() {
        return failed;
    }

    public int hp() {
        return hp;
    }

    public int score() {
        return score;
    }

    public int hintsUsed() {
        return hintsUsed;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    /** 1-based number of the section currently being played, capped at the last one. */
    public int currentSectionNumber() {
        return Math.min(sectionIdx + 1, stage.sections().size());
    }

    public int sectionCount() {
        return stage.sections().size();
    }

    public CodeBlock codeBlock() {
        return codeBlock;
    }

    public List<SectionResult> sectionResults() {
        return Collections.unmodifiableList(sectionResults);
    }
}
