-- ============================================================
-- Subjects and terms. A class is one subject taught to one section in one term; a student can be in several, and
-- keeps access to the ones that have ended. Additive only.
--
--   classes.subject  the subject's name, e.g. "Programming 1" (null: the course code is shown instead)
--   classes.term     the term, e.g. "1st Sem 2026-2027"
--   classes.ended    the professor marked the class as finished: it moves to the student's "Past subjects" and no
--                    new class tests are taken in it, but its lessons stay playable
-- ============================================================

alter table classes
  add column if not exists subject text,
  add column if not exists term    text,
  add column if not exists ended   boolean not null default false;
