-- ============================================================
-- CodeQuest schema — replaces the old Tower-of-Engkanto tables
-- (users / leaderboard / game_saves / leaderboard_totals)
-- ============================================================

drop view if exists leaderboard_totals;
drop table if exists leaderboard;
drop table if exists game_saves;
drop table if exists users;

-- ============ Accounts ============
-- NOTE: password is still plaintext, same as the original prototype — not
-- something this migration introduces, but worth replacing with Supabase
-- Auth in a future pass instead of this custom table.
create table profiles (
  id bigint generated always as identity primary key,
  username varchar(50) unique not null,
  password varchar(255) not null,
  role varchar(20) not null default 'student' check (role in ('student', 'professor')),
  full_name varchar(100),
  institution varchar(100) default 'UB CICT',
  section varchar(50), -- e.g. "BSCS 1-A"; null for professors
  created_at timestamptz default now()
);

-- ============ Classes (professor-run sections) ============
create table classes (
  id bigint generated always as identity primary key,
  professor_id bigint references profiles(id) on delete set null,
  course_code varchar(20) default 'CC102',
  section_name varchar(50) not null,
  created_at timestamptz default now()
);

create table class_enrollments (
  class_id bigint references classes(id) on delete cascade,
  student_id bigint references profiles(id) on delete cascade,
  enrolled_at timestamptz default now(),
  primary key (class_id, student_id)
);

-- ============ Campaign Mode content (professor-authored) ============
create table campaigns (
  id bigint generated always as identity primary key,
  professor_id bigint references profiles(id) on delete set null, -- null = system default campaign
  title varchar(100) not null,
  topic varchar(100), -- e.g. "Loops", "Arrays", "Pointers"
  created_at timestamptz default now()
);

create table stages (
  id bigint generated always as identity primary key,
  campaign_id bigint references campaigns(id) on delete cascade,
  order_index int not null default 1,
  title varchar(100) not null,
  campus_background varchar(100), -- asset key for the campus-themed background
  base_difficulty varchar(10) not null default 'normal' check (base_difficulty in ('easy','normal','hard')),
  adaptive_enabled boolean not null default true,
  ai_generated boolean not null default false,
  created_at timestamptz default now()
);

create table challenges (
  id bigint generated always as identity primary key,
  stage_id bigint references stages(id) on delete cascade,
  sequence_index int not null default 1,
  language varchar(20) not null default 'C', -- Python/C++/Java later, per your scope doc
  prompt text not null,
  starter_code text,
  expected_output text,
  difficulty varchar(10) not null default 'normal' check (difficulty in ('easy','normal','hard')),
  points int not null default 10,
  ai_generated boolean not null default false,
  created_at timestamptz default now()
);

create table hints (
  id bigint generated always as identity primary key,
  challenge_id bigint references challenges(id) on delete cascade,
  hint_order int not null default 1,
  hint_text text not null,
  generated_by varchar(20) not null default 'manual' check (generated_by in ('gemini','claude','manual')),
  created_at timestamptz default now()
);

-- ============ Student progress & attempts ============
create table campaign_progress (
  id bigint generated always as identity primary key,
  student_id bigint references profiles(id) on delete cascade,
  stage_id bigint references stages(id) on delete cascade,
  status varchar(20) not null default 'locked' check (status in ('locked','in_progress','completed')),
  current_difficulty varchar(10) not null default 'normal' check (current_difficulty in ('easy','normal','hard')),
  score int not null default 0,
  rank varchar(5), -- e.g. 'S','A','B','C'
  attempts int not null default 0,
  last_played_at timestamptz,
  unique (student_id, stage_id)
);

create table submissions (
  id bigint generated always as identity primary key,
  student_id bigint references profiles(id) on delete cascade,
  challenge_id bigint references challenges(id) on delete cascade,
  mode varchar(20) not null default 'campaign' check (mode in ('campaign','class_test')),
  submitted_code text,
  is_correct boolean,
  time_taken_seconds int,
  hints_used int not null default 0,
  ai_feedback text, -- Claude's nuanced feedback
  ai_grade_score numeric(5,2), -- Gemini's grading pass
  created_at timestamptz default now()
);

-- ============ Class Test Mode (standardized, multiplayer) ============
create table class_test_sessions (
  id bigint generated always as identity primary key,
  class_id bigint references classes(id) on delete cascade,
  stage_id bigint references stages(id) on delete set null, -- the one standardized activity
  session_mode varchar(20) not null default 'competitive' check (session_mode in ('competitive','cooperative')),
  status varchar(20) not null default 'scheduled' check (status in ('scheduled','active','completed')),
  started_at timestamptz,
  ended_at timestamptz,
  created_at timestamptz default now()
);

create table class_test_participants (
  session_id bigint references class_test_sessions(id) on delete cascade,
  student_id bigint references profiles(id) on delete cascade,
  score int not null default 0,
  rank_in_session int,
  joined_at timestamptz default now(),
  finished_at timestamptz,
  primary key (session_id, student_id)
);

-- ============ Certification (B-rank / 65%+ required) ============
create table certifications (
  id bigint generated always as identity primary key,
  student_id bigint references profiles(id) on delete cascade,
  stage_id bigint references stages(id) on delete set null,
  rank varchar(5) not null,
  score_percentage numeric(5,2) not null,
  pdf_url text, -- Supabase Storage reference once PDFBox generation is wired up
  issued_at timestamptz default now()
);

-- ============ Leaderboard (derived — PostgREST can't GROUP BY directly) ============
create or replace view leaderboard_totals as
select
  p.username,
  sum(cp.score) as total_points,
  max(s.order_index) as best_stage,
  max(cp.current_difficulty) as best_difficulty,
  max(cp.last_played_at) as last_played
from campaign_progress cp
join profiles p on p.id = cp.student_id
join stages s on s.id = cp.stage_id
group by p.username
order by total_points desc;

-- ============ RLS: enabled, permissive for now (no per-user auth wired in yet) ============
alter table profiles enable row level security;
alter table classes enable row level security;
alter table class_enrollments enable row level security;
alter table campaigns enable row level security;
alter table stages enable row level security;
alter table challenges enable row level security;
alter table hints enable row level security;
alter table campaign_progress enable row level security;
alter table submissions enable row level security;
alter table class_test_sessions enable row level security;
alter table class_test_participants enable row level security;
alter table certifications enable row level security;

create policy "public access" on profiles for all using (true) with check (true);
create policy "public access" on classes for all using (true) with check (true);
create policy "public access" on class_enrollments for all using (true) with check (true);
create policy "public access" on campaigns for all using (true) with check (true);
create policy "public access" on stages for all using (true) with check (true);
create policy "public access" on challenges for all using (true) with check (true);
create policy "public access" on hints for all using (true) with check (true);
create policy "public access" on campaign_progress for all using (true) with check (true);
create policy "public access" on submissions for all using (true) with check (true);
create policy "public access" on class_test_sessions for all using (true) with check (true);
create policy "public access" on class_test_participants for all using (true) with check (true);
create policy "public access" on certifications for all using (true) with check (true);
