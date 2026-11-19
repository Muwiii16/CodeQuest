package codequest.content;

import java.util.*;

/**
 * Fixed, hand-authored challenge pool: one placeholder question per (topic,
 * difficulty). Not real CC102-aligned authoring — that comes from professor-made
 * stages stored in Supabase. If a requested difficulty has no challenge, the
 * nearest available difficulty is used so a tower is never left empty.
 */
public class StaticChallengeProvider extends PooledChallengeProvider {

    public StaticChallengeProvider() {
        this(new java.util.Random(), new KindRotation());
    }

    /** Seeded, and sharing the play-through's rotation: the same questions for everyone with the same seed. */
    public StaticChallengeProvider(long seed, KindRotation rotation) {
        this(new java.util.Random(seed), rotation);
    }

    private StaticChallengeProvider(java.util.Random random, KindRotation rotation) {
        super(random, rotation);
        seed();
    }

    private void add(String id, CTopic topic, Difficulty difficulty, String prompt,
            String correct, List<String> distractors, String hint) {
        add(new Challenge(id, topic, difficulty, prompt, correct, distractors, hint));
    }

    private void seed() {
        add("syntax-easy-1", CTopic.SYNTAX, Difficulty.EASY,
                "printf(\"Hello\")___",
                ";", List.of(":", ",", "."),
                "Every C statement ends with a semicolon.");
        add("syntax-normal-1", CTopic.SYNTAX, Difficulty.NORMAL,
                "int main() {\n    printf(\"Hi\")___\n    return 0;\n}",
                ";", List.of(":", ",", "."),
                "The statement before return needs its semicolon too.");
        add("syntax-hard-1", CTopic.SYNTAX, Difficulty.HARD,
                "char grade = ___;",
                "'A'", List.of("\"A\"", "A", "`A`"),
                "A single character uses single quotes; double quotes make a string.");

        add("variables-easy-1", CTopic.VARIABLES, Difficulty.EASY,
                "___ score = 0;",
                "int", List.of("integer", "Int", "num"),
                "C's whole-number type is lowercase int.");
        add("variables-normal-1", CTopic.VARIABLES, Difficulty.NORMAL,
                "___ price = 9.99;   /* keeps the decimals */",
                "float", List.of("int", "char", "long"),
                "Only floating-point types keep the decimal part.");
        add("variables-hard-1", CTopic.VARIABLES, Difficulty.HARD,
                "int x = 7 ___ 2;   /* x becomes 3 */",
                "/", List.of("%", "*", "//"),
                "Integer division drops the remainder; % gives the remainder instead.");

        add("control-easy-1", CTopic.CONTROL_STRUCTURES, Difficulty.EASY,
                "for (int i = 0; i < 3; i___) {   /* count up */\n    printf(\"%d\", i);\n}",
                "++", List.of("+", "--", "=+"),
                "i++ adds one to i each time around the loop.");
        add("control-normal-1", CTopic.CONTROL_STRUCTURES, Difficulty.NORMAL,
                "if (x ___ 5) {   /* is x equal to 5? */\n    printf(\"five\");\n}",
                "==", List.of("=", "=>", "==="),
                "A single = assigns; == compares.");
        add("control-hard-1", CTopic.CONTROL_STRUCTURES, Difficulty.HARD,
                "switch (day) {\n    case 1: printf(\"Mon\");\n    ___\n    case 2: printf(\"Tue\");\n}",
                "break;", List.of("stop;", "continue;", "end;"),
                "Without break, execution falls through into the next case.");

        add("functions-easy-1", CTopic.FUNCTIONS, Difficulty.EASY,
                "___ add(int a, int b) {\n    return a + b;\n}",
                "int", List.of("void", "function", "def"),
                "The function returns a number, so its return type is int.");
        add("functions-normal-1", CTopic.FUNCTIONS, Difficulty.NORMAL,
                "void greet(void) {\n    printf(\"Hi\");\n}\n\nint main(void) {\n    ___;\n    return 0;\n}",
                "greet()", List.of("greet", "void greet()", "call greet"),
                "Calling a function needs its parentheses.");
        add("functions-hard-1", CTopic.FUNCTIONS, Difficulty.HARD,
                "int square(int n) {\n    ___ n * n;\n}",
                "return", List.of("give", "print", "yield"),
                "return sends a value back to the caller.");

        add("arrays-easy-1", CTopic.ARRAYS, Difficulty.EASY,
                "int scores___ = {90, 85, 77};",
                "[3]", List.of("(3)", "<3>", "{3}"),
                "Array size goes in square brackets after the name.");
        add("arrays-normal-1", CTopic.ARRAYS, Difficulty.NORMAL,
                "int nums[5] = {1, 2, 3, 4, 5};\nprintf(\"%d\", nums[___]);   /* the first element */",
                "0", List.of("1", "5", "-1"),
                "C arrays start counting at index 0.");
        add("arrays-hard-1", CTopic.ARRAYS, Difficulty.HARD,
                "int nums[5];\nnums[___] = 9;   /* the last element */",
                "4", List.of("5", "6", "0"),
                "A 5-element array has indexes 0 through 4.");

        add("pointers-easy-1", CTopic.POINTERS, Difficulty.EASY,
                "int x = 5;\nint ___px = &x;",
                "*", List.of("&", "#", "@"),
                "A pointer variable is declared with an asterisk.");
        add("pointers-normal-1", CTopic.POINTERS, Difficulty.NORMAL,
                "int x = 5;\nint *p = ___x;   /* address of x */",
                "&", List.of("*", "@", "#"),
                "The & operator gives a variable's address.");
        add("pointers-hard-1", CTopic.POINTERS, Difficulty.HARD,
                "int x = 5;\nint *p = &x;\nprintf(\"%d\", ___p);   /* prints 5 */",
                "*", List.of("&", "**", "++"),
                "* in front of a pointer reads the value it points to.");

        seedOtherKinds();
    }

    // ── the other ways to answer: type it, true/false, arrange, find the bug ──

    private void fill(String id, CTopic topic, Difficulty d, String prompt, String correct, List<String> accepted,
            String hint) {
        add(new Challenge(id, topic, d, prompt, correct, List.of(), hint, AnswerKind.FILL_BLANK, accepted));
    }

    private void trueFalse(String id, CTopic topic, Difficulty d, String statement, boolean isTrue, String hint) {
        add(new Challenge(id, topic, d, statement, isTrue ? Challenge.TRUE : Challenge.FALSE, List.of(), hint,
                AnswerKind.TRUE_FALSE, List.of()));
    }

    /** order = the tokens in the right order, separated by spaces; decoys = extra tokens that do not belong. */
    private void arrange(String id, CTopic topic, Difficulty d, String prompt, String order, List<String> decoys,
            String hint) {
        add(new Challenge(id, topic, d, prompt, order, decoys, hint, AnswerKind.ARRANGE, List.of()));
    }

    private void bug(String id, CTopic topic, Difficulty d, String code, int badLine, String hint) {
        add(new Challenge(id, topic, d, code, String.valueOf(badLine), List.of(), hint, AnswerKind.FIND_BUG, List.of()));
    }

    private void seedOtherKinds() {
        // syntax
        fill("syntax-fill-1", CTopic.SYNTAX, Difficulty.EASY,
                "#include <___.h>   /* gives you printf */", "stdio", List.of(),
                "stdio stands for standard input and output.");
        trueFalse("syntax-tf-1", CTopic.SYNTAX, Difficulty.NORMAL,
                "Every C program starts running at the function called main.", true,
                "The computer looks for main first.");
        arrange("syntax-arrange-1", CTopic.SYNTAX, Difficulty.NORMAL,
                "Build the line that prints Hi:", "printf ( \"Hi\" ) ;", List.of("print"),
                "The function is printf, its input goes in parentheses, and the line ends with ;");
        bug("syntax-bug-1", CTopic.SYNTAX, Difficulty.HARD,
                "int main() {\n    printf(\"Hi\")\n    return 0;\n}", 2,
                "One statement is missing something at its end.");

        // variables
        fill("variables-fill-1", CTopic.VARIABLES, Difficulty.EASY,
                "___ score = 0;   /* a whole number */", "int", List.of(),
                "C's whole-number type is lowercase int.");
        trueFalse("variables-tf-1", CTopic.VARIABLES, Difficulty.EASY,
                "A variable of type char holds a single character.", true,
                "char is short for character.");
        arrange("variables-arrange-1", CTopic.VARIABLES, Difficulty.EASY,
                "Build the line that makes a whole number named age equal to 18:", "int age = 18 ;", List.of("float"),
                "Type first, then the name, then = and the value.");
        bug("variables-bug-1", CTopic.VARIABLES, Difficulty.NORMAL,
                "int x = 5;\nfloat y = 2.5;\nint 3z = x;", 3,
                "A variable name cannot start with a digit.");
        trueFalse("variables-tf-2", CTopic.VARIABLES, Difficulty.HARD,
                "In C, 7 / 2 gives 3.5 when both numbers are ints.", false,
                "Integer division drops the decimals.");

        // control structures
        fill("control-fill-1", CTopic.CONTROL_STRUCTURES, Difficulty.EASY,
                "for (int i = 0; i < 3; i___) {   /* count up by one */", "++", List.of("+=1"),
                "i++ adds one to i each time around the loop.");
        trueFalse("control-tf-1", CTopic.CONTROL_STRUCTURES, Difficulty.EASY,
                "A for loop can repeat the same code a set number of times.", true,
                "Loops exist so you do not copy and paste code.");
        fill("control-fill-2", CTopic.CONTROL_STRUCTURES, Difficulty.NORMAL,
                "while (n ___ 0) {   /* keep going while n is bigger than 0 */", ">", List.of(),
                "Bigger than is the > sign.");
        arrange("control-arrange-1", CTopic.CONTROL_STRUCTURES, Difficulty.NORMAL,
                "Build the condition that checks if x is bigger than 5:", "if ( x > 5 )", List.of("then"),
                "if comes first, and the condition goes inside parentheses.");
        bug("control-bug-1", CTopic.CONTROL_STRUCTURES, Difficulty.HARD,
                "int n = 3;\nif (n = 5) {\n    printf(\"five\");\n}", 2,
                "A single = assigns. Which sign compares?");

        // functions
        fill("functions-fill-1", CTopic.FUNCTIONS, Difficulty.EASY,
                "___ greet(void) {   /* gives nothing back */", "void", List.of(),
                "void means no value is returned.");
        trueFalse("functions-tf-1", CTopic.FUNCTIONS, Difficulty.NORMAL,
                "A function declared as void gives no value back.", true,
                "void means empty.");
        arrange("functions-arrange-1", CTopic.FUNCTIONS, Difficulty.NORMAL,
                "Build the line that calls add with 3 and 4:", "add ( 3 , 4 ) ;", List.of("call"),
                "A call is the name, then the inputs in parentheses.");
        bug("functions-bug-1", CTopic.FUNCTIONS, Difficulty.HARD,
                "int add(int a, int b) {\n    return a + b;\n}\nint main() {\n    int r = add(1);\n    return 0;\n}", 5,
                "add needs two inputs, but only one is given.");

        // arrays
        fill("arrays-fill-1", CTopic.ARRAYS, Difficulty.EASY,
                "int nums[___] = {1, 2, 3};   /* room for 3 numbers */", "3", List.of(),
                "The size goes between the square brackets.");
        trueFalse("arrays-tf-1", CTopic.ARRAYS, Difficulty.NORMAL,
                "The first element of a C array is at index 1.", false,
                "C starts counting at 0.");
        bug("arrays-bug-1", CTopic.ARRAYS, Difficulty.NORMAL,
                "int nums[3] = {1, 2, 3};\nprintf(\"%d\", nums[0]);\nprintf(\"%d\", nums[3]);", 3,
                "A 3-element array has indexes 0, 1 and 2.");
        arrange("arrays-arrange-1", CTopic.ARRAYS, Difficulty.HARD,
                "Build the line that sets the last of 5 numbers to 9:", "nums [ 4 ] = 9 ;", List.of("5"),
                "The last of 5 elements is at index 4.");

        // pointers
        trueFalse("pointers-tf-1", CTopic.POINTERS, Difficulty.EASY,
                "A pointer stores the address of another variable.", true,
                "A pointer points to a place in memory.");
        arrange("pointers-arrange-1", CTopic.POINTERS, Difficulty.NORMAL,
                "Build the line that makes p hold the address of x:", "p = &x ;", List.of("*x"),
                "& gives the address of a variable.");
        fill("pointers-fill-1", CTopic.POINTERS, Difficulty.HARD,
                "int x = 5;\nint *p = &x;\n___p = 9;   /* now x is 9 */", "*", List.of(),
                "* in front of a pointer reaches the value it points to.");
        bug("pointers-bug-1", CTopic.POINTERS, Difficulty.HARD,
                "int x = 5;\nint *p = x;\nprintf(\"%d\", *p);", 2,
                "A pointer must be given an address, not a plain value.");
    }
}
