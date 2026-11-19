package codequest.content;

/**
 * The ways a student can answer a question. Every kind still ends in one piece of text (the answer), so scoring,
 * the saved run and the answer log work the same for all of them. Nothing here compiles or runs C code.
 */
public enum AnswerKind {

    /** Pick the right token from a row of choices (the original way). */
    CHOICE("Multiple choice", "Pick the right token", 1f, true),
    /** Type the token that goes in the blank. */
    FILL_BLANK("Fill in the blank", "Type what goes in the blank", 1.7f, true),
    /** Say whether a statement about C is true or false. */
    TRUE_FALSE("True or false", "Decide if the statement is true", 1f, false),
    /** Put the tokens in order to build a line of code. */
    ARRANGE("Arrange the code", "Tap the tokens in the right order", 2.2f, true),
    /** Tap the line of code that has the mistake. */
    FIND_BUG("Find the bug", "Tap the line with the mistake, then press Check", 1.8f, false);

    private final String label;
    private final String instruction;
    private final float timeFactor;
    private final boolean addsToCode;

    AnswerKind(String label, String instruction, float timeFactor, boolean addsToCode) {
        this.label = label;
        this.instruction = instruction;
        this.timeFactor = timeFactor;
        this.addsToCode = addsToCode;
    }

    /** Name shown to students and professors. */
    public String label() {
        return label;
    }

    /** One short line telling the student what to do. */
    public String instruction() {
        return instruction;
    }

    /** Typing and building take longer than tapping a choice, so these get more of the projectile's flight time. */
    public float timeFactor() {
        return timeFactor;
    }

    /** Whether a right answer is a piece of code that belongs in the growing code block on the side. */
    public boolean addsToCode() {
        return addsToCode;
    }

    /** The value stored in the database (lowercase name). */
    public String dbName() {
        return name().toLowerCase();
    }

    /** Reads the database value; anything missing or unknown is the original multiple choice. */
    public static AnswerKind fromDb(String value) {
        if (value != null) {
            for (AnswerKind k : values()) {
                if (k.dbName().equalsIgnoreCase(value.trim())) {
                    return k;
                }
            }
        }
        return CHOICE;
    }
}
