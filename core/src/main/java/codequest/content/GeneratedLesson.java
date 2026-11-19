package codequest.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A lesson drafted by the AI: a title, the program (or several small programs separated by a line of ---), what each
 * prints, and the questions that build it, in order. Nothing from the AI is trusted: {@link #problems} runs the same
 * rules a professor's own questions must meet, checks that the questions rebuild the program exactly, and that the lesson
 * stays inside the topics it may teach, before the professor ever sees it.
 */
public record GeneratedLesson(String title, String program, String outputs, List<QuestionDraft> questions) {

    public GeneratedLesson {
        questions = List.copyOf(questions);
    }

    /** A draft with no sample output. */
    public GeneratedLesson(String title, String program, List<QuestionDraft> questions) {
        this(title, program, null, questions);
    }

    /** Every problem at once, with no limit on topics (used where the lesson has none). */
    public Optional<String> problems() {
        return problems(null);
    }

    /**
     * What is wrong with the draft, every problem at once (so a rewrite can fix them all in one go), in words for the
     * professor or to send back to the AI; empty when the draft is usable.
     *
     * @param allowed the topics the lesson may use (SYNTAX is always allowed); null means no limit
     */
    public Optional<String> problems(Set<CTopic> allowed) {
        if (program == null || program.isBlank()) {
            return Optional.of("The program is empty.");
        }
        if (questions.isEmpty()) {
            return Optional.of("There are no questions.");
        }
        if (questions.size() > ProgramChallengeProvider.MAX_SLOTS) {
            return Optional.of("There are " + questions.size() + " questions; at most "
                    + ProgramChallengeProvider.MAX_SLOTS + " are allowed (one per tower).");
        }
        List<String> found = new ArrayList<>();
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Optional<String> p = questions.get(i).problem(true);
            if (p.isPresent()) {
                found.add("Question " + (i + 1) + ": " + p.get());
            }
            if (allowed != null && questions.get(i).topic() != CTopic.SYNTAX && !allowed.contains(questions.get(i).topic())) {
                found.add("Question " + (i + 1) + " is about " + questions.get(i).topic().name().toLowerCase().replace('_', ' ')
                        + ", which is not one of this lesson's topics.");
            }
            chunks.add(questions.get(i).codeChunk());
        }
        ProgramCoverage.Report report = ProgramCoverage.check(program, chunks);
        if (!report.complete()) {
            found.add(ProgramCoverage.describe(report));
        }
        if (allowed != null) {
            found.addAll(ScopeCheck.violations(program, allowed));
        }
        int blocks = ProgramCoverage.blockCount(program);
        if (outputs == null || outputs.isBlank()) {
            found.add("Say what the program prints when it runs (the outputs).");
        } else if (ProgramLayout.splitOutputs(outputs, blocks).size() != blocks) {
            found.add("There are " + blocks + " programs but the outputs describe "
                    + ProgramLayout.splitOutputs(outputs, 0).size() + ". Give one output for each program, separated by a line of ---.");
        }
        return found.isEmpty() ? Optional.empty() : Optional.of(String.join("\n", found));
    }
}
