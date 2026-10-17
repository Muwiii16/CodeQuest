package codequest.session;

public enum Rank {
    S, A, B, C, D, F;

    /** B or higher earns a stage completion certificate. */
    public boolean passes() {
        return ordinal() <= B.ordinal();
    }
}
