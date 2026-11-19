-- ============================================================
-- An explanation for each question: after the student answers, the game shows the right answer and why it is right
-- (and, for a wrong answer, why theirs was not). Additive only; a question without one falls back to its hint.
-- ============================================================

alter table challenges add column if not exists explanation text;
