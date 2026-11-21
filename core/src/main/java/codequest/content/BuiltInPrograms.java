package codequest.content;

import java.util.ArrayList;
import java.util.List;

/**
 * The built-in building-blocks lessons: a whole C program and the questions that build it chunk by chunk, in order.
 * Lessons that have no questions of their own play one of these (one per map layout).
 */
public final class BuiltInPrograms {

    /** A program and its questions, one per chunk, in program order. */
    public record Program(String text, List<Challenge> slots, String outputs) {

        /** A program with no sample output. */
        public Program(String text, List<Challenge> slots) {
            this(text, slots, null);
        }
    }

    private BuiltInPrograms() {
    }

    /** The program for a map layout in the given language: C or C++ (the pilot language). */
    public static Program forLayout(int layout, Language language) {
        return language == Language.CPP ? BuiltInProgramsCpp.forLayout(layout) : forLayout(layout);
    }

    /** The C program for a map layout (1 to {@link DefaultStages#LAYOUTS}). */
    public static Program forLayout(int layout) {
        return switch (layout) {
            case 2 -> arrays();
            case 3 -> calculator();
            default -> firstProgram();
        };
    }

    // ── helpers ──

    /** Gives each question listed the terminal message its wrong answers cause (see Challenge#errorFor). */
    static List<Challenge> withErrors(List<Challenge> questions, java.util.Map<String, String> texts) {
        List<Challenge> out = new ArrayList<>();
        for (Challenge c : questions) {
            String text = texts.get(c.id());
            out.add(text == null ? c : c.withErrorText(text));
        }
        return out;
    }

    /** Gives each question listed its explanation (why the answer is right), keeping the order. */
    static List<Challenge> withExplanations(List<Challenge> questions, java.util.Map<String, String> texts) {
        List<Challenge> out = new ArrayList<>();
        for (Challenge c : questions) {
            String text = texts.get(c.id());
            out.add(text == null ? c : c.withExplanation(text));
        }
        return out;
    }

    /** Gives each question listed its context line (what the code is for), keeping the order. */
    static List<Challenge> withContext(List<Challenge> questions, java.util.Map<String, String> contexts) {
        List<Challenge> out = new ArrayList<>();
        for (Challenge c : questions) {
            String text = contexts.get(c.id());
            out.add(text == null ? c : c.withContext(text));
        }
        return out;
    }

    static Challenge choice(String id, CTopic t, Difficulty d, String prompt, String correct, List<String> wrong,
            String hint) {
        return new Challenge(id, t, d, prompt, correct, wrong, hint);
    }

    static Challenge fill(String id, CTopic t, Difficulty d, String prompt, String correct, List<String> accepted,
            String hint) {
        return new Challenge(id, t, d, prompt, correct, List.of(), hint, AnswerKind.FILL_BLANK, accepted);
    }

    static Challenge trueFalse(String id, CTopic t, Difficulty d, String statement, boolean isTrue, String chunk,
            String hint) {
        return new Challenge(id, t, d, statement, isTrue ? Challenge.TRUE : Challenge.FALSE, List.of(), hint,
                AnswerKind.TRUE_FALSE, List.of(), chunk);
    }

    static Challenge arrange(String id, CTopic t, Difficulty d, String prompt, String order, List<String> decoys,
            String hint) {
        return new Challenge(id, t, d, prompt, order, decoys, hint, AnswerKind.ARRANGE, List.of());
    }

    static Challenge bug(String id, CTopic t, Difficulty d, String code, int badLine, String fixedChunk,
            String hint) {
        return new Challenge(id, t, d, code, String.valueOf(badLine), List.of(), hint, AnswerKind.FIND_BUG, List.of(),
                fixedChunk);
    }

    // ── program 1: variables, printf and an if ──

    private static Program firstProgram() {
        String text = """
                #include <stdio.h>

                int main() {
                    int age = 18;
                    float price = 9.99;
                    printf("Age: %d\\n", age);
                    printf("Price: %.2f\\n", price);
                    if (age >= 18) {
                        printf("Adult\\n");
                    }
                    return 0;
                }
                """;
        List<Challenge> q = new ArrayList<>();
        q.add(fill("p1-1", CTopic.SYNTAX, Difficulty.EASY, "#include <___.h>", "stdio", List.of(),
                "stdio stands for standard input and output. It gives you printf."));
        q.add(trueFalse("p1-2", CTopic.SYNTAX, Difficulty.EASY,
                "Every C program starts running at the function called main.", true, "int main() {",
                "The computer looks for main first."));
        q.add(arrange("p1-3", CTopic.VARIABLES, Difficulty.EASY, "Declare a whole number named age that is 18:",
                "int age = 18 ;", List.of("float"), "Type first, then the name, then = and the value."));
        q.add(choice("p1-4", CTopic.VARIABLES, Difficulty.EASY, "___ price = 9.99;", "float", List.of("int", "char", "long"),
                "Only floating-point types keep the decimal part."));
        q.add(bug("p1-5", CTopic.SYNTAX, Difficulty.NORMAL,
                "printf(\"Age: %d\\n\", age);\nprintf(\"Price: %.2f\\n\", price)", 2,
                "printf(\"Age: %d\\n\", age);\nprintf(\"Price: %.2f\\n\", price);",
                "One statement is missing something at its end."));
        q.add(choice("p1-6", CTopic.CONTROL_STRUCTURES, Difficulty.NORMAL, "if (age ___ 18) {", ">=",
                List.of("=>", "=", "=<"), "Bigger than or equal to is written >=."));
        q.add(trueFalse("p1-7", CTopic.CONTROL_STRUCTURES, Difficulty.NORMAL,
                "Code inside the { } of an if runs only when the condition is true.", true,
                "printf(\"Adult\\n\");\n}", "The condition guards the block."));
        q.add(fill("p1-8", CTopic.SYNTAX, Difficulty.NORMAL, "return ___;\n}", "0", List.of(),
                "return 0 tells the system the program finished well."));
        return new Program(text, withErrors(withExplanations(withContext(q, java.util.Map.of("p1-1", "Bring in the library that gives you printf (standard input and output).",
                "p1-4", "Store a price, which has decimals like 9.99.",
                "p1-6", "Run the next lines only when the person is 18 or older (an adult).",
                "p1-8", "End main and tell the system the program finished without errors.")),
                java.util.Map.of("p1-1", "printf and scanf live in the standard input/output library, and #include <stdio.h> brings it in. Without that line the compiler does not know what printf is.",
                "p1-2", "True. When a C program runs, the computer starts at the function called main, so every program needs one. Code outside main only runs if main calls it.",
                "p1-3", "A declaration goes type, name, =, value, then a semicolon: int age = 18; The type comes first so the computer knows how much room to set aside. float is for decimals, so it does not fit 18 here.",
                "p1-4", "A price has decimals, and only float (or double) keeps the .99. int would cut it to 9. char holds one character and long holds a bigger whole number.",
                "p1-5", "Every C statement ends with a semicolon. The Price printf is missing its ; at the end, so the compiler would stop there. The Age line already has its semicolon.",
                "p1-6", ">= means bigger than or equal to, so age 18 itself counts as an adult. => and =< do not exist in C, and a single = would assign instead of compare.",
                "p1-7", "True. The lines inside the { } of an if only run when the condition is true. If age is under 18 the computer skips them and carries on after the closing }.",
                "p1-8", "main finishes with return 0; to tell the system everything went well. The } after it closes main. A non-zero value would signal an error.")),
                java.util.Map.of("p1-1", "stdlib :: error: implicit declaration of function 'printf' [-Wimplicit-function-declaration]\nstring :: error: implicit declaration of function 'printf' [-Wimplicit-function-declaration]\n* :: fatal error: {answer}.h: No such file or directory",
                "p1-3", "error: expected identifier or '(' before '=' token",
                "p1-4", "int :: \nchar :: \nlong :: ",
                "p1-5", "error: expected ';' before 'if'",
                "p1-6", "=> :: error: expected expression before '>' token\n= :: \n=< :: error: expected expression before '<' token",
                "p1-8", "error: '{answer}' undeclared (first use in this function)")), "Age: 18\nPrice: 9.99\nAdult");
    }

    // ── program 2: an array and a for loop ──

    private static Program arrays() {
        String text = """
                #include <stdio.h>

                int main() {
                    int nums[3] = {4, 8, 15};
                    int sum = 0;
                    for (int i = 0; i < 3; i++) {
                        sum += nums[i];
                    }
                    printf("Sum: %d\\n", sum);
                    return 0;
                }
                """;
        List<Challenge> q = new ArrayList<>();
        q.add(fill("p2-1", CTopic.SYNTAX, Difficulty.EASY, "#include <stdio.h>\nint ___() {", "main", List.of(),
                "Every program starts at a function called main."));
        q.add(choice("p2-2", CTopic.ARRAYS, Difficulty.EASY, "int nums[___] = {4, 8, 15};", "3", List.of("2", "4", "0"),
                "The size is how many numbers the array holds."));
        q.add(arrange("p2-3", CTopic.VARIABLES, Difficulty.EASY, "Make a whole number named sum that starts at 0:",
                "int sum = 0 ;", List.of("float"), "Type, name, =, value."));
        q.add(fill("p2-4", CTopic.CONTROL_STRUCTURES, Difficulty.NORMAL,
                "for (int i = 0; i < 3; i___) {", "++", List.of("+=1"), "i++ adds one to i each time around the loop."));
        q.add(bug("p2-5", CTopic.ARRAYS, Difficulty.NORMAL, "sum += nums(i);\n}", 1, "sum += nums[i];\n}",
                "Array elements are reached with square brackets."));
        q.add(trueFalse("p2-6", CTopic.SYNTAX, Difficulty.NORMAL, "printf with %d prints a whole number.", true,
                "printf(\"Sum: %d\\n\", sum);", "%d stands for an int."));
        q.add(choice("p2-7", CTopic.FUNCTIONS, Difficulty.NORMAL, "return ___;\n}", "0", List.of("1", "void", "-1"),
                "main returns 0 when everything went well."));
        return new Program(text, withErrors(withExplanations(withContext(q, java.util.Map.of("p2-1", "The function every C program starts in.",
                "p2-2", "Make room for exactly the three numbers listed.",
                "p2-4", "Count up by one after each time around the loop.",
                "p2-7", "End main and tell the system the program finished without errors.")),
                java.util.Map.of("p2-1", "Every C program starts in a function called main, and int is the type of the number it returns. #include brings in the library that gives you printf.",
                "p2-2", "The size in the brackets is how many numbers the array holds. The list {4, 8, 15} has 3 numbers, so the size is 3. A smaller size would not fit them all.",
                "p2-3", "A declaration is type, name, =, value, then a semicolon: int sum = 0; Starting at 0 gives the total a clean start before the loop adds to it.",
                "p2-4", "i++ adds 1 to i after each time around the loop, so the loop goes 0, 1, 2 and stops when i < 3 is false. Without a step the loop would never end.",
                "p2-5", "Array elements are reached with square brackets, nums[i], not parentheses. nums(i) would look like a function call, which the compiler would reject.",
                "p2-6", "True. %d is the format code for a whole number (an int), so printf puts the value of sum there. %f would be for decimals.",
                "p2-7", "main returns 0 to say the program ended without errors, and the } closes main. Returning 1 would tell the system something went wrong.")),
                java.util.Map.of("p2-1", "error: undefined reference to 'main' (the program has no function called main)",
                "p2-2", "2 :: warning: excess elements in array initializer\n4 :: \n0 :: warning: excess elements in array initializer",
                "p2-3", "error: expected identifier or '(' before '=' token",
                "p2-4", "-- :: \n* :: error: expected expression before ')' token",
                "p2-5", "error: called object 'nums' is not a function or function pointer",
                "p2-7", "1 :: \n-1 :: \nvoid :: error: expected expression before 'void'")), "Sum: 27");
    }

    // ── program 3: a small calculator ──

    private static Program calculator() {
        String text = """
                #include <stdio.h>

                int main() {
                    double a, b;

                    printf("Enter first number: ");
                    scanf("%lf", &a);
                    printf("Enter second number: ");
                    scanf("%lf", &b);

                    printf("Sum: %.2f\\n", a + b);
                    printf("Difference: %.2f\\n", a - b);
                    printf("Product: %.2f\\n", a * b);

                    if (b != 0) {
                        printf("Quotient: %.2f\\n", a / b);
                    } else {
                        printf("Cannot divide by zero.\\n");
                    }

                    return 0;
                }
                """;
        List<Challenge> q = new ArrayList<>();
        q.add(fill("p3-1", CTopic.SYNTAX, Difficulty.EASY, "#include <___.h>", "stdio", List.of(),
                "stdio gives you printf and scanf."));
        q.add(arrange("p3-2", CTopic.SYNTAX, Difficulty.EASY, "Build the start of main:", "int main ( ) {", List.of("void"),
                "A function is a type, a name, () and then a { block."));
        q.add(choice("p3-3", CTopic.VARIABLES, Difficulty.EASY, "___ a, b;", "double", List.of("int", "char", "string"),
                "double holds decimal numbers."));
        q.add(bug("p3-4", CTopic.SYNTAX, Difficulty.NORMAL,
                "printf(\"Enter first number: \");\nscanf(\"%lf\", a);", 2,
                "printf(\"Enter first number: \");\nscanf(\"%lf\", &a);",
                "scanf needs the address of the variable."));
        q.add(fill("p3-5", CTopic.POINTERS, Difficulty.NORMAL,
                "printf(\"Enter second number: \");\nscanf(\"%lf\", ___b);", "&", List.of(),
                "& gives the address of a variable."));
        q.add(trueFalse("p3-6", CTopic.SYNTAX, Difficulty.NORMAL,
                "%.2f prints a double with 2 digits after the decimal point.", true,
                "printf(\"Sum: %.2f\\n\", a + b);\nprintf(\"Difference: %.2f\\n\", a - b);", "The .2 is the number of decimals."));
        q.add(fill("p3-7", CTopic.VARIABLES, Difficulty.NORMAL, "printf(\"Product: %.2f\\n\", a ___ b);", "*", List.of(),
                "Multiplication is the * sign."));
        q.add(choice("p3-8", CTopic.CONTROL_STRUCTURES, Difficulty.HARD,
                "if (b ___ 0) {\n    printf(\"Quotient: %.2f\\n\", a / b);", "!=", List.of("=!", "=", "<>"),
                "Not equal to is written !="));
        q.add(fill("p3-9", CTopic.CONTROL_STRUCTURES, Difficulty.HARD,
                "} ___ {\n    printf(\"Cannot divide by zero.\\n\");\n}", "else", List.of(),
                "else runs when the if condition was false."));
        q.add(choice("p3-10", CTopic.FUNCTIONS, Difficulty.NORMAL, "return ___;\n}", "0", List.of("1", "void", "-1"),
                "main returns 0 when everything went well."));
        return new Program(text, withErrors(withExplanations(withContext(q, java.util.Map.of("p3-1", "Bring in the library that gives you printf and scanf.",
                "p3-3", "Make two variables that can hold numbers with decimals.",
                "p3-5", "Give scanf the address of b, so it can store the number the user types.",
                "p3-7", "Print the product: a multiplied by b.",
                "p3-8", "Only divide when b is not zero.",
                "p3-9", "What to do when b is zero (the if was false).",
                "p3-10", "End main and tell the system the program finished without errors.")),
                java.util.Map.of("p3-1", "printf and scanf come from the standard input/output library. #include <stdio.h> loads it, so the compiler knows both functions.",
                "p3-2", "A function starts with its return type, then its name, then ( ) for the inputs, then { to open its body: int main() {. void is a type for returning nothing, so it does not belong here.",
                "p3-3", "a and b will hold numbers that can have decimals, and double is the decimal type. int would cut the decimals off and char holds a single character.",
                "p3-4", "scanf needs the address of the variable so it can put the typed number there. The address is written with &, so it is &a. Writing just a would pass the value, not the place to store it.",
                "p3-5", "& means the address of the variable. scanf needs it to know where in memory to store what the user types, so it is &b.",
                "p3-6", "True. In %.2f the .2 means two digits after the decimal point, so 3.14159 prints as 3.14. The f means a decimal number.",
                "p3-7", "The * sign multiplies in C. x and ** are not multiplication, and & means the address of a variable, so a * b is the product.",
                "p3-8", "Dividing by zero is an error, so the program only divides when b is not zero. != means not equal. =! is not valid C, = assigns, and <> does not exist in C.",
                "p3-9", "else runs when the if condition was false, so here it handles b being zero and prints the warning instead of dividing. The } closes that block.",
                "p3-10", "main returns 0 to tell the system everything went well. The last } closes main and ends the program.")),
                java.util.Map.of("p3-1", "stdlib :: error: implicit declaration of function 'printf' [-Wimplicit-function-declaration]\nstring :: error: implicit declaration of function 'printf' [-Wimplicit-function-declaration]\n* :: fatal error: {answer}.h: No such file or directory",
                "p3-2", "error: two or more data types in declaration specifiers",
                "p3-3", "int :: \nchar :: \nstring :: error: unknown type name 'string'",
                "p3-4", "warning: format '%lf' expects argument of type 'double *', but argument 2 has type 'double' [-Wformat=]",
                "p3-5", "warning: format '%lf' expects argument of type 'double *', but argument 2 has type 'double' [-Wformat=]",
                "p3-7", "error: expected ')' before '{answer}'",
                "p3-8", "=! :: \n= :: \n<> :: error: expected expression before '>' token",
                "p3-9", "error: expected ';' before '{' token",
                "p3-10", "1 :: \n-1 :: \nvoid :: error: expected expression before 'void'")), "Enter first number: 7\nEnter second number: 2\nSum: 9.00\nDifference: 5.00\nProduct: 14.00\nQuotient: 3.50");
    }
}
