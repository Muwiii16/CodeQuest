package codequest.certificate;

import java.time.LocalDate;

/**
 * What goes on a certificate: student name, section, what was completed, rank achieved and date, per the manuscript.
 * {@code grand} is the fancier design, used for finishing a whole subject; the certificate of a single grading period
 * (Prelims, Midterms, Semis, Finals) uses the plain one.
 */
public record CertificateData(
        String studentName,
        String section,
        String stageTitle,
        String rank,
        double scorePercentage,
        LocalDate issuedOn,
        boolean grand
) {

    /** A plain certificate (one grading period). */
    public CertificateData(String studentName, String section, String stageTitle, String rank, double scorePercentage,
            LocalDate issuedOn) {
        this(studentName, section, stageTitle, rank, scorePercentage, issuedOn, false);
    }
}
