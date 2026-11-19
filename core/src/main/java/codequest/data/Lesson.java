package codequest.data;

import codequest.content.CTopic;
import codequest.content.Difficulty;
import codequest.content.ScopeCheck;

import java.util.Set;

/**
 * A lesson (stage) in a class's campaign. {@code number} is its position in that campaign, starting at 1.
 * {@code program} is what it builds (several small programs may be separated by a line of ---), {@code outputs} what
 * each of them prints, and {@code topics} the topics it teaches (null: no limit, for lessons made before topics existed).
 */
public record Lesson(long stageId, long campaignId, int number, String title, String baseDifficulty, boolean adaptive,
        String prompt, String program, String topics, String outputs, boolean test) {

    /** A lesson of the campaign (not a class-test stage). */
    public Lesson(long stageId, long campaignId, int number, String title, String baseDifficulty, boolean adaptive,
            String prompt, String program, String topics, String outputs) {
        this(stageId, campaignId, number, title, baseDifficulty, adaptive, prompt, program, topics, outputs, false);
    }

    /** What it is called: "Lesson 3" for a lesson, "Class test 2" for a stage made only for class tests. */
    public String label() {
        return label(test, number);
    }

    public static String label(boolean test, int number) {
        return (test ? "Class test " : "Lesson ") + number;
    }

    /** A lesson without a program. */
    public Lesson(long stageId, long campaignId, int number, String title, String baseDifficulty, boolean adaptive,
            String prompt) {
        this(stageId, campaignId, number, title, baseDifficulty, adaptive, prompt, null, null, null);
    }

    /** A lesson with a program but no topics or sample outputs. */
    public Lesson(long stageId, long campaignId, int number, String title, String baseDifficulty, boolean adaptive,
            String prompt, String program) {
        this(stageId, campaignId, number, title, baseDifficulty, adaptive, prompt, program, null, null);
    }

    /** The full C program this lesson builds, one question at a time; null or blank when it has none. */
    public boolean hasProgram() {
        return program != null && !program.isBlank();
    }

    /** The topics the lesson teaches; empty for a lesson made before topics existed. */
    public Set<CTopic> topicSet() {
        return ScopeCheck.parseTopics(topics);
    }

    public Lesson withProgram(String newProgram) {
        return withProgram(newProgram, outputs);
    }

    public Lesson withProgram(String newProgram, String newOutputs) {
        return new Lesson(stageId, campaignId, number, title, baseDifficulty, adaptive, prompt,
                newProgram == null || newProgram.isBlank() ? null : newProgram, topics,
                newOutputs == null || newOutputs.isBlank() ? null : newOutputs, test);
    }

    public Lesson withTopics(Set<CTopic> newTopics) {
        return new Lesson(stageId, campaignId, number, title, baseDifficulty, adaptive, prompt, program,
                newTopics.isEmpty() ? null : ScopeCheck.formatTopics(newTopics), outputs, test);
    }

    public Lesson withDetails(String newTitle, String difficulty, boolean isAdaptive, String newPrompt) {
        return new Lesson(stageId, campaignId, number, newTitle, difficulty, isAdaptive,
                newPrompt == null || newPrompt.isBlank() ? null : newPrompt, program, topics, outputs, test);
    }

    public Difficulty difficulty() {
        try {
            return Difficulty.valueOf(baseDifficulty.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return Difficulty.NORMAL;
        }
    }
}
