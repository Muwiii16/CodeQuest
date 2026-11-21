-- ============================================================
-- Lesson pacing. A professor chooses, per class, how lessons open for students:
--   classes.pacing = 'self'    lessons open one after another: a lesson is playable once the one before it is completed
--                              (how the game has always worked; the default)
--   classes.pacing = 'manual'  every lesson is locked until the professor unlocks it (stages.unlocked); the order does not matter
-- stages.unlocked is only used by manual classes. The app can already change it only through the existing rules: a
-- professor can edit the stages of their own classes, nobody else can.
-- ============================================================

alter table classes add column if not exists pacing text not null default 'self';
alter table classes drop constraint if exists classes_pacing_check;
alter table classes add constraint classes_pacing_check check (pacing in ('self', 'manual'));

alter table stages add column if not exists unlocked boolean not null default false;
