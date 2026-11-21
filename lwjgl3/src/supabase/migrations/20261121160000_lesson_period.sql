-- ============================================================
-- Grading periods. Every lesson of a class belongs to one of the four periods of a semester:
-- prelims, midterms, semis, finals. Students see their lessons grouped by period; the professor can look at the
-- class performance (and the AI analysis) one period at a time.
-- Lessons that existed before this change are Prelims; the professor can move them from the lesson editor.
-- ============================================================

alter table stages add column if not exists period text not null default 'prelims';
alter table stages drop constraint if exists stages_period_check;
alter table stages add constraint stages_period_check check (period in ('prelims', 'midterms', 'semis', 'finals'));
