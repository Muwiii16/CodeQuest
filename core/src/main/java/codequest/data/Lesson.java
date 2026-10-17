package codequest.data;

import codequest.content.Difficulty;

/** A lesson (stage) in a class's campaign. {@code number} is its position in that campaign, starting at 1. */
public record Lesson(long stageId, long campaignId, int number, String title, String baseDifficulty, boolean adaptive,
        String prompt) {

    public Difficulty difficulty() {
        try {
            return Difficulty.valueOf(baseDifficulty.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return Difficulty.NORMAL;
        }
    }
}
