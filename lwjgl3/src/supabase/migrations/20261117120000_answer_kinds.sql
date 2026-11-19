-- ============================================================
-- Answer kinds: a question can be multiple choice (the original), fill in the blank, true/false,
-- arrange the tokens, or find the bug. Additive only: every existing row stays a multiple-choice
-- question, and no existing column is changed or dropped.
--
--   kind             how the student answers (see AnswerKind in the game)
--   accepted_answers other spellings that also count (fill in the blank only)
--
-- What correct_token / distractors mean for each kind is documented on the Challenge record.
-- ============================================================

alter table challenges
  add column if not exists kind text not null default 'choice',
  add column if not exists accepted_answers jsonb not null default '[]'::jsonb;

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'challenges_kind_check') then
    alter table challenges
      add constraint challenges_kind_check
      check (kind in ('choice', 'fill_blank', 'true_false', 'arrange', 'find_bug'));
  end if;
end $$;
