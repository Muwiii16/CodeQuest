package codequest.content;

import java.util.List;

/**
 * Placeholder stage layouts until professor-authored stages load from
 * Supabase. Every stage gets four sections of two towers each.
 */
public final class DefaultStages {

    private DefaultStages() {
    }

    /** Number of distinct built-in layouts and map pictures; lessons beyond this reuse them in turn. */
    public static final int LAYOUTS = 3;

    /** The layout/map number (1..LAYOUTS) a lesson plays on. */
    public static int layoutFor(int lessonNumber) {
        return (Math.max(1, lessonNumber) - 1) % LAYOUTS + 1;
    }

    public static Stage forNumber(int stageNumber) {
        return new Stage("Stage " + stageNumber, "map" + stageNumber, List.of(
                new Section(1, List.of(CTopic.SYNTAX, CTopic.VARIABLES)),
                new Section(2, List.of(CTopic.CONTROL_STRUCTURES, CTopic.FUNCTIONS)),
                new Section(3, List.of(CTopic.ARRAYS, CTopic.POINTERS)),
                new Section(4, List.of(CTopic.SYNTAX, CTopic.CONTROL_STRUCTURES))));
    }
}
