package codequest.session;

/**
 * Maps overall accuracy to S–F. Only "B or higher passes" is documented in
 * the manuscript; the exact cutoffs below are a proposal to confirm with the adviser.
 */
public final class RankCalculator {

    private RankCalculator() {
    }

    public static Rank rankFor(double accuracy) {
        if (accuracy >= 0.95) return Rank.S;
        if (accuracy >= 0.85) return Rank.A;
        if (accuracy >= 0.65) return Rank.B;
        if (accuracy >= 0.50) return Rank.C;
        if (accuracy >= 0.35) return Rank.D;
        return Rank.F;
    }
}
