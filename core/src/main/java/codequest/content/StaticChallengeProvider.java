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
    }
}
