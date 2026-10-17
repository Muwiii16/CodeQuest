-- ============================================================
-- Token-selection challenges, per map section.
-- The original challenges table (prompt/starter_code/expected_output) could
-- not represent the actual mechanic: a code snippet with a blank, one correct
-- token, several wrong tokens, tied to a topic and a position on the map.
-- Additive only; no existing column is changed or dropped.
-- ============================================================

alter table challenges
  add column topic varchar(30),
  add column section_index int not null default 1,
  add column correct_token text,
  add column distractors jsonb not null default '[]'::jsonb;

-- sequence_index = the tower's position within its section (1, 2, ...).
-- A tower slot holds up to three rows, one per difficulty, so adaptive
-- difficulty can swap in an easier or harder version of the same tower.
create unique index challenges_tower_variant
  on challenges (stage_id, section_index, sequence_index, difficulty);
