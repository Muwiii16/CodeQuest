package codequest.session;

import codequest.content.Difficulty;

/**
 * The manuscript's adaptive rule, applied at the end of each map section:
 * 80% accuracy or higher raises difficulty, below 50% lowers it, anything
 * in between leaves it unchanged. Campaign Mode only.
 */
public final class AdaptiveDifficulty {

    public static final double RAISE_AT = 0.80;
    public static final double LOWER_BELOW = 0.50;

    private AdaptiveDifficulty() {
    }

    public static Difficulty next(Difficulty current, double accuracy) {
        Difficulty[] all = Difficulty.values();
        if (accuracy >= RAISE_AT) {
            return all[Math.min(current.ordinal() + 1, all.length - 1)];
        }
        if (accuracy < LOWER_BELOW) {
            return all[Math.max(current.ordinal() - 1, 0)];
        }
        return current;
    }
}
