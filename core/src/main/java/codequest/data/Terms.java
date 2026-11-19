package codequest.data;

import java.time.LocalDate;

/** The school term a date falls in, used to suggest a term name when a professor creates a class. */
public final class Terms {

    private Terms() {
    }

    /**
     * The term of a date: August to December is the 1st semester of the school year that starts that year, January
     * to May the 2nd semester of the year that began the year before, and June and July the summer term.
     */
    public static String current(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        if (month >= 8) {
            return "1st Sem " + year + "-" + (year + 1);
        }
        if (month <= 5) {
            return "2nd Sem " + (year - 1) + "-" + year;
        }
        return "Summer " + year;
    }
}
