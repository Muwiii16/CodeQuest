-- ============================================================
-- Cosmetics system: catalog, per-student unlocks, equipped slots.
-- Adds three tables only; no existing table is altered.
-- ============================================================

-- ============ Catalog ============
-- category examples: avatar_skin, trail, badge, title
-- unlock_type examples: default, score_threshold, stage_completion, certification
-- unlock_criteria holds the rule so it isn't hardcoded,
--   e.g. {"stage_id": 3} or {"min_score": 90}; '{}' for default cosmetics.
create table cosmetics (
  id bigint generated always as identity primary key,
  name varchar(100) not null,
  category varchar(30) not null,
  description text,
  asset_url text, -- null until the art exists
  rarity varchar(20) not null default 'common',
  unlock_type varchar(30) not null default 'default',
  unlock_criteria jsonb not null default '{}'::jsonb,
  created_at timestamptz default now()
);

-- ============ What each student has unlocked ============
create table student_cosmetics (
  student_id bigint references profiles(id) on delete cascade,
  cosmetic_id bigint references cosmetics(id) on delete cascade,
  unlocked_at timestamptz default now(),
  primary key (student_id, cosmetic_id)
);

-- ============ What's currently equipped (one per category) ============
-- The (student_id, category) primary key is what enforces one-per-category.
-- category must match cosmetics.category for the referenced cosmetic; that is
-- not enforced at the database level yet.
create table equipped_cosmetics (
  student_id bigint references profiles(id) on delete cascade,
  category varchar(30) not null,
  cosmetic_id bigint references cosmetics(id) on delete cascade,
  equipped_at timestamptz default now(),
  primary key (student_id, category)
);

-- ============ Default cosmetics ============
insert into cosmetics (name, category, description, rarity, unlock_type) values
  ('Standard Student', 'avatar_skin', 'The standard CodeQuest student avatar.', 'common', 'default'),
  ('Plain Footsteps',  'trail',       'A simple trail with no effects.',        'common', 'default'),
  ('Freshman',         'badge',       'Every first-year starts here.',          'common', 'default'),
  ('Newcomer',         'title',       'Just getting started.',                  'common', 'default');

-- ============ RLS: enabled, permissive for now (same as the other tables) ============
alter table cosmetics enable row level security;
alter table student_cosmetics enable row level security;
alter table equipped_cosmetics enable row level security;

create policy "public access" on cosmetics for all using (true) with check (true);
create policy "public access" on student_cosmetics for all using (true) with check (true);
create policy "public access" on equipped_cosmetics for all using (true) with check (true);
