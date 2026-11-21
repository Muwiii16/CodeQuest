package codequest.content;

import codequest.content.BuiltInPrograms.Program;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static codequest.content.BuiltInPrograms.arrange;
import static codequest.content.BuiltInPrograms.bug;
import static codequest.content.BuiltInPrograms.choice;
import static codequest.content.BuiltInPrograms.fill;
import static codequest.content.BuiltInPrograms.trueFalse;
import static codequest.content.BuiltInPrograms.withContext;
import static codequest.content.BuiltInPrograms.withErrors;
import static codequest.content.BuiltInPrograms.withExplanations;

/**
 * The built-in building-blocks lessons in C++ (the pilot language): the same three programs as the C ones, written with
 * iostream, cout, cin and using namespace std, and the questions that build each one chunk by chunk, in order. The question
 * ids start with "cpp" so the answer log tells them apart from the C ones.
 */
final class BuiltInProgramsCpp {

    private BuiltInProgramsCpp() {
    }

    static Program forLayout(int layout) {
        return switch (layout) {
            case 2 -> arrays();
            case 3 -> calculator();
            default -> firstProgram();
        };
    }

    // ── program 1: variables, cout and an if ──

    private static Program firstProgram() {
        String text = """
                #include <iostream>
                using namespace std;

                int main() {
                    int age = 18;
                    double price = 9.99;
                    cout << "Age: " << age << endl;
                    cout << "Price: " << price << endl;
                    if (age >= 18) {
                        cout << "Adult" << endl;
                    }
                    return 0;
                }
                """;
        List<Challenge> q = new ArrayList<>();
        q.add(fill("cpp1-1", CTopic.SYNTAX, Difficulty.EASY, "#include <___>\nusing namespace std;", "iostream", List.of(),
                "iostream stands for input and output stream. It gives you cout."));
        q.add(trueFalse("cpp1-2", CTopic.SYNTAX, Difficulty.EASY,
                "Every C++ program starts running at the function called main.", true, "int main() {",
                "The computer looks for main first."));
        q.add(arrange("cpp1-3", CTopic.VARIABLES, Difficulty.EASY, "Declare a whole number named age that is 18:",
                "int age = 18 ;", List.of("double"), "Type first, then the name, then = and the value."));
        q.add(choice("cpp1-4", CTopic.VARIABLES, Difficulty.EASY, "___ price = 9.99;", "double", List.of("int", "char", "bool"),
                "Only decimal types keep the decimal part."));
        q.add(bug("cpp1-5", CTopic.SYNTAX, Difficulty.NORMAL,
                "cout << \"Age: \" << age << endl;\ncout << \"Price: \" << price << endl", 2,
                "cout << \"Age: \" << age << endl;\ncout << \"Price: \" << price << endl;",
                "One statement is missing something at its end."));
        q.add(choice("cpp1-6", CTopic.CONTROL_STRUCTURES, Difficulty.NORMAL, "if (age ___ 18) {", ">=",
                List.of("=>", "=", "=<"), "Bigger than or equal to is written >=."));
        q.add(trueFalse("cpp1-7", CTopic.CONTROL_STRUCTURES, Difficulty.NORMAL,
                "Code inside the { } of an if runs only when the condition is true.", true,
                "cout << \"Adult\" << endl;\n}", "The condition guards the block."));
        q.add(fill("cpp1-8", CTopic.SYNTAX, Difficulty.NORMAL, "return ___;\n}", "0", List.of(),
                "return 0 tells the system the program finished well."));
        return new Program(text, withErrors(withExplanations(withContext(q, Map.of(
                "cpp1-1", "Bring in the library that gives you cout (input and output streams).",
                "cpp1-4", "Store a price, which has decimals like 9.99.",
                "cpp1-6", "Run the next lines only when the person is 18 or older (an adult).",
                "cpp1-8", "End main and tell the system the program finished without errors.")),
                Map.of("cpp1-1", "cout and cin live in the iostream library, and #include <iostream> brings it in. Without that line the compiler does not know what cout is. using namespace std; lets you write cout instead of std::cout.",
                "cpp1-2", "True. When a C++ program runs, the computer starts at the function called main, so every program needs one. Code outside main only runs if main calls it.",
                "cpp1-3", "A declaration goes type, name, =, value, then a semicolon: int age = 18; The type comes first so the computer knows how much room to set aside. double is for decimals, so it does not fit 18 here.",
                "cpp1-4", "A price has decimals, and only double (or float) keeps the .99. int would cut it to 9. char holds one character and bool holds only true or false.",
                "cpp1-5", "Every C++ statement ends with a semicolon. The Price line is missing its ; at the end, so the compiler would stop there. The Age line already has its semicolon.",
                "cpp1-6", ">= means bigger than or equal to, so age 18 itself counts as an adult. => and =< do not exist in C++, and a single = would assign instead of compare.",
                "cpp1-7", "True. The lines inside the { } of an if only run when the condition is true. If age is under 18 the computer skips them and carries on after the closing }.",
                "cpp1-8", "main finishes with return 0; to tell the system everything went well. The } after it closes main. A non-zero value would signal an error.")),
                Map.of("cpp1-1", "cstdlib :: error: 'cout' was not declared in this scope\nstring :: error: 'cout' was not declared in this scope\n* :: fatal error: {answer}: No such file or directory",
                "cpp1-3", "error: expected unqualified-id before '=' token",
                "cpp1-4", "int :: \nchar :: \nbool :: ",
                "cpp1-5", "error: expected ';' before 'if'",
                "cpp1-6", "=> :: error: expected primary-expression before '>' token\n= :: \n=< :: error: expected primary-expression before '<' token",
                "cpp1-8", "error: '{answer}' was not declared in this scope")), "Age: 18\nPrice: 9.99\nAdult");
    }

    // ── program 2: an array and a for loop ──

    private static Program arrays() {
        String text = """
                #include <iostream>
                using namespace std;

                int main() {
                    int nums[3] = {4, 8, 15};
                    int sum = 0;
                    for (int i = 0; i < 3; i++) {
                        sum += nums[i];
                    }
                    cout << "Sum: " << sum << endl;
                    return 0;
                }
                """;
        List<Challenge> q = new ArrayList<>();
        q.add(fill("cpp2-1", CTopic.SYNTAX, Difficulty.EASY, "#include <iostream>\nusing namespace std;\nint ___() {", "main",
                List.of(), "Every program starts at a function called main."));
        q.add(choice("cpp2-2", CTopic.ARRAYS, Difficulty.EASY, "int nums[___] = {4, 8, 15};", "3", List.of("2", "4", "0"),
                "The size is how many numbers the array holds."));
        q.add(arrange("cpp2-3", CTopic.VARIABLES, Difficulty.EASY, "Make a whole number named sum that starts at 0:",
                "int sum = 0 ;", List.of("double"), "Type, name, =, value."));
        q.add(fill("cpp2-4", CTopic.CONTROL_STRUCTURES, Difficulty.NORMAL,
                "for (int i = 0; i < 3; i___) {", "++", List.of("+=1"), "i++ adds one to i each time around the loop."));
        q.add(bug("cpp2-5", CTopic.ARRAYS, Difficulty.NORMAL, "sum += nums(i);\n}", 1, "sum += nums[i];\n}",
                "Array elements are reached with square brackets."));
        q.add(trueFalse("cpp2-6", CTopic.SYNTAX, Difficulty.NORMAL,
                "cout << sum prints the value stored in sum.", true,
                "cout << \"Sum: \" << sum << endl;", "<< sends a value to cout, which prints it."));
        q.add(choice("cpp2-7", CTopic.FUNCTIONS, Difficulty.NORMAL, "return ___;\n}", "0", List.of("1", "void", "-1"),
                "main returns 0 when everything went well."));
        return new Program(text, withErrors(withExplanations(withContext(q, Map.of(
                "cpp2-1", "The function every C++ program starts in.",
                "cpp2-2", "Make room for exactly the three numbers listed.",
                "cpp2-4", "Count up by one after each time around the loop.",
                "cpp2-7", "End main and tell the system the program finished without errors.")),
                Map.of("cpp2-1", "Every C++ program starts in a function called main, and int is the type of the number it returns. #include brings in the library that gives you cout, and using namespace std; lets you write cout instead of std::cout.",
                "cpp2-2", "The size in the brackets is how many numbers the array holds. The list {4, 8, 15} has 3 numbers, so the size is 3. A smaller size would not fit them all.",
                "cpp2-3", "A declaration is type, name, =, value, then a semicolon: int sum = 0; Starting at 0 gives the total a clean start before the loop adds to it.",
                "cpp2-4", "i++ adds 1 to i after each time around the loop, so the loop goes 0, 1, 2 and stops when i < 3 is false. Without a step the loop would never end.",
                "cpp2-5", "Array elements are reached with square brackets, nums[i], not parentheses. nums(i) would look like a function call, which the compiler would reject.",
                "cpp2-6", "True. << sends what is on its right to cout, so cout << sum prints the value of sum. You can chain several << in one line, as the program does with the text and the number.",
                "cpp2-7", "main returns 0 to say the program ended without errors, and the } closes main. Returning 1 would tell the system something went wrong.")),
                Map.of("cpp2-1", "error: undefined reference to 'main' (the program has no function called main)",
                "cpp2-2", "2 :: error: too many initializers for 'int [2]'\n4 :: \n0 :: error: too many initializers for 'int [0]'",
                "cpp2-3", "error: expected unqualified-id before '=' token",
                "cpp2-4", "-- :: \n* :: error: expected primary-expression before ')' token",
                "cpp2-5", "error: 'nums' cannot be used as a function",
                "cpp2-7", "1 :: \n-1 :: \nvoid :: error: expected primary-expression before 'void'")), "Sum: 27");
    }

    // ── program 3: a small calculator ──

    private static Program calculator() {
        String text = """
                #include <iostream>
                using namespace std;

                int main() {
                    double a, b;

                    cout << "Enter first number: ";
                    cin >> a;
                    cout << "Enter second number: ";
                    cin >> b;

                    cout << "Sum: " << a + b << endl;
                    cout << "Difference: " << a - b << endl;
                    cout << "Product: " << a * b << endl;

                    if (b != 0) {
                        cout << "Quotient: " << a / b << endl;
                    } else {
                        cout << "Cannot divide by zero." << endl;
                    }

                    return 0;
                }
                """;
        List<Challenge> q = new ArrayList<>();
        q.add(fill("cpp3-1", CTopic.SYNTAX, Difficulty.EASY, "#include <___>\nusing namespace std;", "iostream", List.of(),
                "iostream gives you cout and cin."));
        q.add(arrange("cpp3-2", CTopic.SYNTAX, Difficulty.EASY, "Build the start of main:", "int main ( ) {", List.of("void"),
                "A function is a type, a name, () and then a { block."));
        q.add(choice("cpp3-3", CTopic.VARIABLES, Difficulty.EASY, "___ a, b;", "double", List.of("int", "char", "bool"),
                "double holds decimal numbers."));
        q.add(bug("cpp3-4", CTopic.SYNTAX, Difficulty.NORMAL,
                "cout << \"Enter first number: \";\ncin << a;", 2,
                "cout << \"Enter first number: \";\ncin >> a;",
                "cin takes what the user types with >>, the arrows point into the variable."));
        q.add(fill("cpp3-5", CTopic.VARIABLES, Difficulty.NORMAL,
                "cout << \"Enter second number: \";\ncin ___ b;", ">>", List.of(),
                ">> puts what the user types into the variable."));
        q.add(trueFalse("cpp3-6", CTopic.SYNTAX, Difficulty.NORMAL,
                "endl ends the line, so the next output starts on a new line.", true,
                "cout << \"Sum: \" << a + b << endl;\ncout << \"Difference: \" << a - b << endl;", "endl is like pressing Enter."));
        q.add(fill("cpp3-7", CTopic.VARIABLES, Difficulty.NORMAL, "cout << \"Product: \" << a ___ b << endl;", "*", List.of(),
                "Multiplication is the * sign."));
        q.add(choice("cpp3-8", CTopic.CONTROL_STRUCTURES, Difficulty.HARD,
                "if (b ___ 0) {\n    cout << \"Quotient: \" << a / b << endl;", "!=", List.of("=!", "=", "<>"),
                "Not equal to is written !="));
        q.add(fill("cpp3-9", CTopic.CONTROL_STRUCTURES, Difficulty.HARD,
                "} ___ {\n    cout << \"Cannot divide by zero.\" << endl;\n}", "else", List.of(),
                "else runs when the if condition was false."));
        q.add(choice("cpp3-10", CTopic.FUNCTIONS, Difficulty.NORMAL, "return ___;\n}", "0", List.of("1", "void", "-1"),
                "main returns 0 when everything went well."));
        return new Program(text, withErrors(withExplanations(withContext(q, Map.of(
                "cpp3-1", "Bring in the library that gives you cout and cin.",
                "cpp3-3", "Make two variables that can hold numbers with decimals.",
                "cpp3-5", "Read the second number the user types into b.",
                "cpp3-7", "Print the product: a multiplied by b.",
                "cpp3-8", "Only divide when b is not zero.",
                "cpp3-9", "What to do when b is zero (the if was false).",
                "cpp3-10", "End main and tell the system the program finished without errors.")),
                Map.of("cpp3-1", "cout and cin come from the iostream library. #include <iostream> loads it, so the compiler knows both, and using namespace std; lets you write them without std:: in front.",
                "cpp3-2", "A function starts with its return type, then its name, then ( ) for the inputs, then { to open its body: int main() {. void is a type for returning nothing, so it does not belong here.",
                "cpp3-3", "a and b will hold numbers that can have decimals, and double is the decimal type. int would cut the decimals off, char holds a single character and bool only true or false.",
                "cpp3-4", "cin reads with >>, written cin >> a; The arrows point from cin into the variable. << is for cout, which sends values out, so cin << a is an error.",
                "cpp3-5", ">> is the input operator: cin >> b; puts what the user types into b. The arrows point into the variable.",
                "cpp3-6", "True. endl ends the line like pressing Enter, so the next output starts on a new line instead of running on after it.",
                "cpp3-7", "The * sign multiplies in C++. a * b is the product. x and ** are not multiplication.",
                "cpp3-8", "Dividing by zero is an error, so the program only divides when b is not zero. != means not equal. =! is not valid C++, = assigns, and <> does not exist in C++.",
                "cpp3-9", "else runs when the if condition was false, so here it handles b being zero and prints the warning instead of dividing. The } closes that block.",
                "cpp3-10", "main returns 0 to tell the system everything went well. The last } closes main and ends the program.")),
                Map.of("cpp3-1", "cstdlib :: error: 'cout' was not declared in this scope\nstring :: error: 'cout' was not declared in this scope\n* :: fatal error: {answer}: No such file or directory",
                "cpp3-2", "error: two or more data types in declaration",
                "cpp3-3", "int :: \nchar :: \nbool :: ",
                "cpp3-5", "<< :: error: no match for 'operator<<' (operand types are 'std::istream' and 'double')\n* :: error: expected ';' before 'b'",
                "cpp3-7", "error: expected primary-expression before '{answer}'",
                "cpp3-8", "=! :: \n= :: \n<> :: error: expected primary-expression before '>' token",
                "cpp3-9", "error: expected ';' before '{' token",
                "cpp3-10", "1 :: \n-1 :: \nvoid :: error: expected primary-expression before 'void'")),
                "Enter first number: 7\nEnter second number: 2\nSum: 9\nDifference: 5\nProduct: 14\nQuotient: 3.5");
    }
}
