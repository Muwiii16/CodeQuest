-- ============================================================
-- A context line for questions: one sentence saying what the code is for ("Run the next lines only when the person is
-- 18 or older"), shown above the question so a blank like  if (age ___ 18)  has exactly one right answer.
-- Additive only; questions without one are shown as before.
-- ============================================================

alter table challenges add column if not exists context text;
