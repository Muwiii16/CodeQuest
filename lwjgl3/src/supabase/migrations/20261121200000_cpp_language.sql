-- ============================================================
-- C++ is the pilot language. A class teaches one language (classes.language: 'C' or 'C++'); new classes default to C++, the
-- classes that already exist stay C. The dictionary gets its C++ entries (iostream style: cout, cin, using namespace std).
-- ============================================================

alter table classes alter column language set default 'C++';

insert into dictionary_entries (language, term, topic, meaning, example, sort_order) values
 ('C++', '#include',  'SYNTAX', 'Brings a library into your program so you can use what it offers.', '#include <iostream>', 1),
 ('C++', 'using namespace std;', 'SYNTAX', 'Lets you write cout and cin instead of std::cout and std::cin. It goes under the #include lines.', '#include <iostream>' || chr(10) || 'using namespace std;', 2),
 ('C++', 'main',      'SYNTAX', 'The function every C++ program starts running in.', 'int main() {' || chr(10) || '    return 0;' || chr(10) || '}', 3),
 ('C++', 'cout',      'SYNTAX', 'Prints text, and the values of variables, on the screen. It comes from iostream and is used with <<.', 'cout << "Age: " << age << endl;', 4),
 ('C++', 'cin',       'SYNTAX', 'Reads what the user types into a variable. It is used with >> (the arrows point into the variable).', 'cin >> age;', 5),
 ('C++', '<<  >>',    'SYNTAX', 'Stream operators. << sends values out to cout; >> takes values in from cin. You can chain several in one line.', 'cout << "Sum: " << a + b;', 6),
 ('C++', 'endl',      'SYNTAX', 'Ends the line, like pressing Enter, so the next output starts on a new line.', 'cout << "Hello" << endl;', 7),
 ('C++', ';',         'SYNTAX', 'The semicolon ends a statement. Forgetting it is the most common beginner error.', 'int x = 5;', 8),
 ('C++', '{ }',       'SYNTAX', 'Curly braces group statements into one block, such as a function body or an if body.', 'if (x > 0) {' || chr(10) || '    cout << "positive";' || chr(10) || '}', 9),
 ('C++', '//  /* */', 'SYNTAX', 'A comment: text for people to read that the computer ignores. // comments one line, /* */ comments several.', '// count the students', 10),
 ('C++', 'return',    'FUNCTIONS', 'Ends the function and sends a value back to whoever called it. main returns 0 when all went well.', 'return 0;', 11),
 ('C++', 'int',       'VARIABLES', 'The type for whole numbers, with no decimals.', 'int score = 90;', 12),
 ('C++', 'double',    'VARIABLES', 'The type for numbers with decimals. It holds more digits than float, so it is the usual choice.', 'double price = 9.99;', 13),
 ('C++', 'float',     'VARIABLES', 'A decimal type with less precision than double.', 'float total = 12.5f;', 14),
 ('C++', 'char',      'VARIABLES', 'The type for a single character, written in single quotes.', 'char grade = ''A'';', 15),
 ('C++', 'bool',      'VARIABLES', 'The type for true or false. Conditions give a bool.', 'bool passed = true;', 16),
 ('C++', 'string',    'VARIABLES', 'The type for text, written in double quotes. It needs #include <string> (iostream usually brings it in).', 'string name = "Ana";', 17),
 ('C++', '=',         'VARIABLES', 'Assignment: puts the value on the right into the variable on the left. It does not compare.', 'age = 18;', 18),
 ('C++', 'if',        'CONTROL_STRUCTURES', 'Runs a block only when its condition is true.', 'if (age >= 18) {' || chr(10) || '    cout << "Adult";' || chr(10) || '}', 19),
 ('C++', 'else',      'CONTROL_STRUCTURES', 'The block that runs when the if condition was false.', 'if (b != 0) { ... } else { ... }', 20),
 ('C++', 'for',       'CONTROL_STRUCTURES', 'A loop for a known number of repeats: start, condition, step.', 'for (int i = 0; i < 3; i++) {' || chr(10) || '    cout << i;' || chr(10) || '}', 21),
 ('C++', 'while',     'CONTROL_STRUCTURES', 'A loop that repeats as long as its condition stays true.', 'while (n > 0) {' || chr(10) || '    n--;' || chr(10) || '}', 22),
 ('C++', '==  !=  >=  <=', 'CONTROL_STRUCTURES', 'Comparison operators: equal, not equal, bigger or equal, smaller or equal. A single = assigns instead.', 'if (x == 5)', 23),
 ('C++', '&&  ||  !', 'CONTROL_STRUCTURES', 'Logic operators: and, or, not. They combine conditions.', 'if (age >= 18 && age < 60)', 24),
 ('C++', 'break',     'CONTROL_STRUCTURES', 'Leaves the loop or switch case immediately.', 'case 1: cout << "Mon"; break;', 25),
 ('C++', 'switch',    'CONTROL_STRUCTURES', 'Chooses between many cases by comparing one value. Each case usually ends with break.', 'switch (day) {' || chr(10) || '    case 1: cout << "Mon"; break;' || chr(10) || '}', 26),
 ('C++', 'void',      'FUNCTIONS', 'Means nothing: a function that returns nothing.', 'void greet() {' || chr(10) || '    cout << "Hi";' || chr(10) || '}', 27),
 ('C++', 'function',  'FUNCTIONS', 'A named block of code you can run again and again by calling its name.', 'int add(int a, int b) {' || chr(10) || '    return a + b;' || chr(10) || '}', 28),
 ('C++', 'parameter', 'FUNCTIONS', 'A variable in a function''s parentheses that receives the value the caller passes in.', 'int square(int n)   // n is the parameter', 29),
 ('C++', 'call',      'FUNCTIONS', 'Running a function by writing its name with parentheses and the values to pass.', 'int r = add(3, 4);', 30),
 ('C++', 'array',     'ARRAYS', 'Several values of the same type stored together under one name.', 'int nums[3] = {4, 8, 15};', 31),
 ('C++', '[ ]  index', 'ARRAYS', 'Square brackets reach one element of an array by its position. Counting starts at 0.', 'cout << nums[0];   // the first one', 32),
 ('C++', 'vector',    'ARRAYS', 'A list that can grow and shrink. It needs #include <vector>.', 'vector<int> nums = {4, 8, 15};' || chr(10) || 'nums.push_back(23);', 33),
 ('C++', 'pointer',   'POINTERS', 'A variable that stores the address of another variable.', 'int x = 5;' || chr(10) || 'int *p = &x;', 34),
 ('C++', '&',         'POINTERS', 'Address-of: gives the address in memory where a variable lives. (After a type it makes a reference, a second name for a variable.)', 'int *p = &x;', 35),
 ('C++', '*',         'POINTERS', 'Next to a pointer, reads or changes the value the pointer points to (dereference).', '*p = 9;   // now x is 9', 36),
 ('C++', 'nullptr',   'POINTERS', 'A pointer that points to nothing. Check for it before using a pointer that might be empty.', 'int *p = nullptr;', 37)
on conflict (language, term) do nothing;
