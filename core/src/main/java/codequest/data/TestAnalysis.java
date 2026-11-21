package codequest.data;

import codequest.data.ClassTestData.Participant;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The numbers of one finished class test: who took part and how they scored, how accurately and how fast each student
 * answered, which questions and topics the class got wrong most, and the class accuracy. It is worked out from the ranked
 * participants and the answer log of the test session. Pure arithmetic (no screen, no network), so it can be tested.
 */
public record TestAnalysis(int rosterSize, int joined, int finished, int average, int median, int highest, int lowest,
        int totalAnswers, int accuracyPercent, int averageSeconds, List<StudentLine> students,
        List<ProfessorData.QuestionStat> questions, Map<String, int[]> topics) {

    /** One participant with their place and how they answered. averageSeconds is 0 when they gave no answer. */
    public record StudentLine(Participant participant, int rank, int answered, int correct, int averageSeconds) {
        public int accuracyPercent() {
            return answered == 0 ? 0 : Math.round(correct * 100f / answered);
        }
    }

    /**
     * @param ranked     everyone who joined, best score first (as {@link ClassTestData#participants} returns them)
     * @param rosterSize how many students the class has
     * @param answers    answer-log rows of the session: student_id, topic, is_correct, time_taken_ms, challenge_id,
     *                   challenge_key and the embedded challenge (may be null)
     */
    public static TestAnalysis of(List<Participant> ranked, int rosterSize, JsonValue answers) {
        Map<Long, int[]> perStudent = new HashMap<>(); // answered, correct, total milliseconds
        Map<String, int[]> topics = new TreeMap<>();
        int total = 0;
        int right = 0;
        long totalMs = 0;
        if (answers != null) {
            for (JsonValue r : answers) {
                boolean correct = r.getBoolean("is_correct", false);
                int ms = Math.max(0, r.getInt("time_taken_ms", 0));
                int[] s = perStudent.computeIfAbsent(r.getLong("student_id", -1), k -> new int[3]);
                s[0]++;
                s[2] += ms;
                total++;
                totalMs += ms;
                if (correct) {
                    s[1]++;
                    right++;
                }
                String topic = r.getString("topic", null);
                if (topic != null) {
                    int[] t = topics.computeIfAbsent(topic, k -> new int[2]);
                    t[1]++;
                    if (correct) {
                        t[0]++;
                    }
                }
            }
        }
        List<StudentLine> lines = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Participant p = ranked.get(i);
            int[] s = perStudent.getOrDefault(p.studentId(), new int[3]);
            lines.add(new StudentLine(p, i + 1, s[0], s[1], s[0] == 0 ? 0 : Math.round(s[2] / 1000f / s[0])));
        }
        List<Integer> scores = new ArrayList<>();
        ranked.forEach(p -> scores.add(p.score()));
        java.util.Collections.sort(scores);
        int n = scores.size();
        int median = n == 0 ? 0 : n % 2 == 1 ? scores.get(n / 2) : Math.round((scores.get(n / 2 - 1) + scores.get(n / 2)) / 2f);
        int average = n == 0 ? 0 : Math.round((float) scores.stream().mapToInt(Integer::intValue).sum() / n);
        return new TestAnalysis(rosterSize, n, (int) ranked.stream().filter(Participant::finished).count(), average, median,
                n == 0 ? 0 : scores.get(n - 1), n == 0 ? 0 : scores.get(0), total, total == 0 ? 0 : Math.round(right * 100f / total),
                total == 0 ? 0 : Math.round(totalMs / 1000f / total), lines, ProfessorData.questionStats(answers), topics);
    }

    /** Students who joined and left before finishing. */
    public int leftEarly() {
        return joined - finished;
    }

    /** Students of the class who did not join at all. */
    public int absent() {
        return Math.max(0, rosterSize - joined);
    }
}
