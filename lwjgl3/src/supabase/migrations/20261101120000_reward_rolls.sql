-- ============================================================
-- Reward rolls: a ledger of every gacha roll. It enforces one roll per lesson per student
-- (replaying a lesson gives nothing, so rewards cannot be farmed) and it holds the history the
-- pity counter is worked out from. Cosmetics belong to the student, not to a class, so the ledger
-- is per student across all their classes.
-- ============================================================

create table if not exists reward_rolls (
  id bigint generated always as identity primary key,
  student_id bigint not null references profiles(id) on delete cascade,
  -- the lesson that earned the roll; kept (set to null) if a professor later deletes the lesson
  stage_id bigint references stages(id) on delete set null,
  cosmetic_id bigint references cosmetics(id) on delete set null,
  rarity varchar(20) not null,
  was_pity boolean not null default false,
  was_duplicate boolean not null default false,
  rolled_at timestamptz not null default now()
);

-- one roll per lesson per student
create unique index if not exists reward_rolls_one_per_lesson
  on reward_rolls (student_id, stage_id) where stage_id is not null;
create index if not exists reward_rolls_student_time on reward_rolls (student_id, rolled_at desc);

alter table reward_rolls enable row level security;
create policy "public access" on reward_rolls for all using (true) with check (true);
