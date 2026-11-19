-- ============================================================
-- Dictionary: the terms of a programming language with a plain meaning and an example, for students to look up
-- from their menu. One shared set per language (C for now); the app can read it but not change it.
-- ============================================================

create table if not exists dictionary_entries (
  id         bigint generated always as identity primary key,
  language   varchar(20) not null default 'C',
  term       text not null,
  topic      varchar(30) not null,
  meaning    text not null,
  example    text,
  sort_order int not null default 0,
  unique (language, term)
);

alter table dictionary_entries enable row level security;
drop policy if exists dictionary_read on dictionary_entries;
create policy dictionary_read on dictionary_entries for select using (true);
revoke insert, update, delete on dictionary_entries from anon, authenticated;
grant select on dictionary_entries to anon, authenticated;

insert into dictionary_entries (language, term, topic, meaning, example, sort_order) values
 ('C', '#include',  'SYNTAX', 'Brings a library into your program so you can use what it offers.', '#include <stdio.h>', 1),
 ('C', 'main',      'SYNTAX', 'The function every C program starts running in.', 'int main() {' || chr(10) || '    return 0;' || chr(10) || '}', 2),
 ('C', 'printf',    'SYNTAX', 'Prints text, and the values of variables, on the screen. It comes from stdio.h.', 'printf("Age: %d\n", age);', 3),
 ('C', 'scanf',     'SYNTAX', 'Reads what the user types into a variable. It needs the variable''s address (&).', 'scanf("%d", &age);', 4),
 ('C', ';',         'SYNTAX', 'The semicolon ends a statement. Forgetting it is the most common beginner error.', 'int x = 5;', 5),
 ('C', '{ }',       'SYNTAX', 'Curly braces group statements into one block, such as a function body or an if body.', 'if (x > 0) {' || chr(10) || '    printf("positive");' || chr(10) || '}', 6),
 ('C', '/* */',     'SYNTAX', 'A comment: text for people to read that the computer ignores. // comments one line.', '/* count the students */', 7),
 ('C', '\n',        'SYNTAX', 'Inside a string, the code for a new line.', 'printf("Hello\n");', 8),
 ('C', 'return',    'FUNCTIONS', 'Ends the function and sends a value back to whoever called it. main returns 0 when all went well.', 'return 0;', 9),
 ('C', 'int',       'VARIABLES', 'The type for whole numbers, with no decimals.', 'int score = 90;', 10),
 ('C', 'float',     'VARIABLES', 'The type for numbers with decimals. double holds more digits.', 'float price = 9.99;', 11),
 ('C', 'double',    'VARIABLES', 'A decimal type with more precision than float. Read it with %lf in scanf.', 'double total = 12.5;', 12),
 ('C', 'char',      'VARIABLES', 'The type for a single character, written in single quotes.', 'char grade = ''A'';', 13),
 ('C', '=',         'VARIABLES', 'Assignment: puts the value on the right into the variable on the left. It does not compare.', 'age = 18;', 14),
 ('C', '%d, %f, %c', 'VARIABLES', 'Format codes for printf and scanf: %d a whole number, %f a decimal, %c a character, %s a string.', 'printf("%d and %.2f", 5, 2.5);', 15),
 ('C', 'if',        'CONTROL_STRUCTURES', 'Runs a block only when its condition is true.', 'if (age >= 18) {' || chr(10) || '    printf("Adult");' || chr(10) || '}', 16),
 ('C', 'else',      'CONTROL_STRUCTURES', 'The block that runs when the if condition was false.', 'if (b != 0) { ... } else { ... }', 17),
 ('C', 'for',       'CONTROL_STRUCTURES', 'A loop for a known number of repeats: start, condition, step.', 'for (int i = 0; i < 3; i++) {' || chr(10) || '    printf("%d", i);' || chr(10) || '}', 18),
 ('C', 'while',     'CONTROL_STRUCTURES', 'A loop that repeats as long as its condition stays true.', 'while (n > 0) {' || chr(10) || '    n--;' || chr(10) || '}', 19),
 ('C', '==  !=  >=  <=', 'CONTROL_STRUCTURES', 'Comparison operators: equal, not equal, bigger or equal, smaller or equal. A single = assigns instead.', 'if (x == 5)', 20),
 ('C', '&&  ||  !', 'CONTROL_STRUCTURES', 'Logic operators: and, or, not. They combine conditions.', 'if (age >= 18 && age < 60)', 21),
 ('C', 'break',     'CONTROL_STRUCTURES', 'Leaves the loop or switch case immediately.', 'case 1: printf("Mon"); break;', 22),
 ('C', 'switch',    'CONTROL_STRUCTURES', 'Chooses between many cases by comparing one value. Each case usually ends with break.', 'switch (day) {' || chr(10) || '    case 1: printf("Mon"); break;' || chr(10) || '}', 23),
 ('C', 'void',      'FUNCTIONS', 'Means nothing: a function that returns nothing, or takes no inputs.', 'void greet(void) {' || chr(10) || '    printf("Hi");' || chr(10) || '}', 24),
 ('C', 'function',  'FUNCTIONS', 'A named block of code you can run again and again by calling its name.', 'int add(int a, int b) {' || chr(10) || '    return a + b;' || chr(10) || '}', 25),
 ('C', 'parameter', 'FUNCTIONS', 'A variable in a function''s parentheses that receives the value the caller passes in.', 'int square(int n)   /* n is the parameter */', 26),
 ('C', 'call',      'FUNCTIONS', 'Running a function by writing its name with parentheses and the values to pass.', 'int r = add(3, 4);', 27),
 ('C', 'array',     'ARRAYS', 'Several values of the same type stored together under one name.', 'int nums[3] = {4, 8, 15};', 28),
 ('C', '[ ]  index', 'ARRAYS', 'Square brackets reach one element of an array by its position. Counting starts at 0.', 'printf("%d", nums[0]);   /* the first one */', 29),
 ('C', 'string',    'ARRAYS', 'Text in C is an array of char that ends with a hidden 0 character.', 'char name[] = "Ana";', 30),
 ('C', 'pointer',   'POINTERS', 'A variable that stores the address of another variable.', 'int x = 5;' || chr(10) || 'int *p = &x;', 31),
 ('C', '&',         'POINTERS', 'Address-of: gives the address in memory where a variable lives.', 'scanf("%d", &age);', 32),
 ('C', '*',         'POINTERS', 'Next to a pointer, reads or changes the value the pointer points to (dereference).', '*p = 9;   /* now x is 9 */', 33),
 ('C', 'NULL',      'POINTERS', 'A pointer that points to nothing. Check for it before using a pointer that might be empty.', 'int *p = NULL;', 34)
on conflict (language, term) do nothing;
