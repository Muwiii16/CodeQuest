package codequest.session;

import codequest.content.AnswerKind;
import codequest.content.Challenge;
import codequest.content.ProgramLayout;
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
 * Correctness is judged locally and instantly (the answer matches the stored
 * one, see AnswerMatcher) — AI never runs during play. Class Test Mode keeps difficulty
 * fixed; Campaign Mode adapts it after every section.
 */
public class StageRun {

    public enum Mode { CAMPAIGN, CLASS_TEST }

    /**
     * correctToken is always filled in (as the right answer in words, e.g. "line 3") so the UI can show it after a miss.
     * points is what this answer added to the score; streak is the current run of correct answers.
     */
    public record Outcome(boolean correct, int damage, String correctToken, SectionResult sectionClosed, int points,
            int streak) {
    }

    public static final int MAX_HP = 100;
    // 20 so that failing is possible on the placeholder 8-tower stage (5 misses); retune with the real stage length.
    public static final int DAMAGE_PER_MISS = 20;
    public static final int POINTS_PER_CORRECT = 10;

    // Class Test Mode scores like Kahoot: a correct answer is worth 500-1000 points depending on how fast it was,
    // plus a bonus for answering several in a row. Nobody loses health, so everyone plays to the end.
    public static final int SPEED_POINTS_MIN = 500;
    public static final int SPEED_POINTS_MAX = 1000;
    public static final int STREAK_BONUS_STEP = 100;
    public static final int STREAK_BONUS_MAX = 500;

    private final Stage stage;
    private final ChallengeProvider provider;
    private final Mode mode;
    private final boolean adaptive;
    private final CodeBlock codeBlock = new CodeBlock();
    private ProgramLayout layout;
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
    private int streak = 0;
    private int bestStreak = 0;
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

    /** Seconds the player has to read a one-line question before the projectile lands. Easier = more time. */
    public static float flightSeconds(Difficulty d) {
        return switch (d) {
            case EASY -> 16f;
            case NORMAL -> 13f;
            case HARD -> 10f;
        };
    }

    /** Extra seconds for every line of code after the first one, up to this many lines. */
    public static final float SECONDS_PER_EXTRA_LINE = 2f;
    public static final int MAX_EXTRA_LINES = 6;

    /**
     * Seconds the player has for one question: the difficulty's time, longer for the kinds that take longer to answer
     * (typing, building), plus a little for every extra line of code there is to read.
     */
    public static float flightSeconds(Challenge c, Difficulty d) {
        int lines = c.prompt().split("\n", -1).length;
        float extra = Math.min(MAX_EXTRA_LINES, Math.max(0, lines - 1)) * SECONDS_PER_EXTRA_LINE;
        return flightSeconds(d) * c.kind().timeFactor() + extra;
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

    /** Points for a correct class-test answer given the share of the question's time still left (0 to 1). */
    public static int speedPoints(float timeLeftFraction) {
        float f = Math.max(0f, Math.min(1f, timeLeftFraction));
        return SPEED_POINTS_MIN + Math.round((SPEED_POINTS_MAX - SPEED_POINTS_MIN) * f);
    }

    /** Extra points for a streak: nothing for the first correct answer, then 100 more each, up to 500. */
    public static int streakBonus(int streak) {
        return streak < 2 ? 0 : Math.min(STREAK_BONUS_MAX, STREAK_BONUS_STEP * (streak - 1));
    }

    /** Answer the pending challenge as if with no time left (what the campaign uses; it does not score on speed). */
    public Outcome answer(String token) {
        return answer(token, 0f);
    }

    /**
     * Answer the pending challenge. A null token means the player ran out of time.
     * timeLeftFraction is the share of the question's time that was still left; only Class Test scoring uses it.
     */
    public Outcome answer(String token, float timeLeftFraction) {
        if (pending == null) {
            throw new IllegalStateException("no active challenge");
        }
        Challenge c = pending;
        pending = null;

        boolean speedScoring = mode == Mode.CLASS_TEST;
        boolean correct = c.isCorrect(token);
        int damage = 0;
        int points = 0;
        boolean build = provider.buildsProgram();
        String chunk = build ? c.codeChunk() : null;
        // which program of the lesson this question belongs to, and where its first line sits in it
        ProgramLayout.Slot where = layout != null && totalAnswered < layout.slots().size() ? layout.slots().get(totalAnswered)
                : new ProgramLayout.Slot(0, 1);
        if (correct) {
            if (chunk != null) {
                codeBlock.append(new CodeBlock.Token(chunk, true, 0, where.block(), where.startLine(), null));
            } else if (!build && c.kind().addsToCode()) {
                codeBlock.append(new CodeBlock.Token(c.correctToken(), true)); // the canonical spelling, not the typed one
            }
            streak++;
            bestStreak = Math.max(bestStreak, streak);
            points = speedScoring ? speedPoints(timeLeftFraction) + streakBonus(streak) : POINTS_PER_CORRECT;
            score += points;
            sectionCorrect++;
            totalCorrect++;
        } else {
            streak = 0;
            if (chunk != null) {
                // The student's answer goes into the code, in red, so the code really has their mistake in it and the
                // terminal can show what the compiler would say. A missed find-the-bug leaves the buggy code, with only
                // the line that held the bug in red. A wrong true/false changes no code.
                String wrong = c.wrongCode(token);
                String text = wrong == null ? chunk : wrong;
                codeBlock.append(new CodeBlock.Token(text, false, c.kind() == AnswerKind.FIND_BUG ? bugLine(c) : 0,
                        where.block(), where.startLine(), wrong == null ? null : c.errorFor(token)));
            } else if (!build && token != null && c.kind().addsToCode()) {
                codeBlock.append(new CodeBlock.Token(token, false));
            }
            if (!speedScoring) {
                hp = Math.max(0, hp - DAMAGE_PER_MISS);
                damage = DAMAGE_PER_MISS;
            }
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
        return new Outcome(correct, damage, c.answerLabel(), closed, points, streak);
    }

    private static int bugLine(Challenge c) {
        try {
            return Integer.parseInt(c.correctToken().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Tells the run where each question's chunk sits among the lesson's programs (needed for the terminal). */
    public void useLayout(ProgramLayout layout) {
        this.layout = layout;
    }

    public ProgramLayout layout() {
        return layout;
    }

    /** The program of the lesson the student is building now: the one the next question belongs to. */
    public int displayBlock() {
        if (layout == null || layout.slots().isEmpty()) {
            return 0;
        }
        int next = Math.min(totalAnswered, layout.slots().size() - 1);
        return layout.slots().get(next).block();
    }

    /** The terminal of the latest program that has all its questions answered; empty until one is finished. */
    public List<Terminal.Row> terminal() {
        if (layout == null) {
            return List.of();
        }
        for (int program = layout.blocks() - 1; program >= 0; program--) {
            int answeredIn = 0;
            for (CodeBlock.Token t : codeBlock.tokens()) {
                answeredIn += t.block() == program ? 1 : 0;
            }
            if (layout.slotsIn(program) > 0 && answeredIn >= layout.slotsIn(program)) {
                return Terminal.run(codeBlock, layout, program);
            }
        }
        return List.of();
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

    /** Whether the code block is the lesson's program being built chunk by chunk. */
    public boolean buildsProgram() {
        return provider.buildsProgram();
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

    /** Correct answers in a row right now. */
    public int streak() {
        return streak;
    }

    public int bestStreak() {
        return bestStreak;
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
