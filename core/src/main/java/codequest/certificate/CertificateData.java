package codequest.certificate;

import java.time.LocalDate;

/** What goes on a stage completion certificate: student name, section, rank achieved and date, per the manuscript. */
public record CertificateData(
        String studentName,
        String section,
        String stageTitle,
        String rank,
        double scorePercentage,
        LocalDate issuedOn
) {
}
