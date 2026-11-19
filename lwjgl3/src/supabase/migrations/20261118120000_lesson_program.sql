-- ============================================================
-- Building-blocks lessons: a lesson can hold one full C program. Its questions are written chunk by chunk
-- in program order; every answered question adds its chunk of the program to the student's code block
-- (green when right, red when wrong), so the whole program is built up over the lesson.
-- Additive only: lessons without a program keep working exactly as before.
--
--   stages.program        the full program the lesson builds (null = no program)
--   challenges.code_chunk the lines of the program this question adds. Optional: for multiple choice, fill in the
--                         blank and arrange questions it is worked out from the question; true/false and
--                         find-the-bug questions need it written.
-- ============================================================

alter table stages     add column if not exists program text;
alter table challenges add column if not exists code_chunk text;
