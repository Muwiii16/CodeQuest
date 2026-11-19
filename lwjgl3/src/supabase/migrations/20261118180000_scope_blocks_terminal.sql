-- ============================================================
-- Scope, several programs per lesson, and a terminal. Additive only.
--
--   stages.topics      the topics this lesson teaches, e.g. 'SYNTAX,VARIABLES' (null: no limit, as before). A lesson may
--                      only use its own topics and those of the lessons before it.
--   stages.outputs     what each program of the lesson prints when it runs, in the same order, programs separated by a
--                      line of ---  (stages.program is split the same way, so a lesson can hold several small programs)
--   challenges.error_text  the compiler message the student sees in the terminal if a wrong answer breaks the code
-- ============================================================

alter table stages     add column if not exists topics  text;
alter table stages     add column if not exists outputs text;
alter table challenges add column if not exists error_text text;
