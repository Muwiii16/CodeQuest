package codequest.certificate;

import java.time.LocalDate;

/** What goes on a lesson badge: who earned it, for which lesson of which subject, with what rank, and when. */
public record BadgeData(String studentName, String subject, String section, int lessonNumber, String lessonTitle,
        String period, String rank, int gradePercent, LocalDate earnedOn) {
}
