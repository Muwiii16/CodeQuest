-- ============================================================
-- Professor/student flow: lessons belong to a class, classes have a
-- programming language, class-test lobby needs a ready flag, and lesson
-- rewards are cosmetics unlocked by lesson number.
-- ============================================================

-- A class teaches one programming language (only 'C' is playable for now).
alter table classes add column if not exists language varchar(20) not null default 'C';

-- Each class owns one campaign (its lessons). null = the built-in default campaign.
alter table campaigns add column if not exists class_id bigint references classes(id) on delete cascade;
create index if not exists campaigns_class_id on campaigns(class_id);

-- What the professor typed in the stage-creation prompt box (kept for the AI step).
alter table stages add column if not exists prompt text;

-- Class-test lobby: a student clicks Ready before the professor can start.
alter table class_test_participants add column if not exists ready boolean not null default false;

-- Built-in stages: replace the old Tower of Engkanto names.
update stages set title = 'Orientation Grounds' where title = 'Gubat ng mga Nilalang';
update stages set title = 'Library Lane' where title = 'Bundok ng Sumpa';
update stages set title = 'Computer Lab Approach' where title = 'Tore ng Engkanto';

-- Lesson rewards: unlock_criteria {"lesson_number": n} = the reward for passing lesson n.
insert into cosmetics (name, category, description, rarity, unlock_type, unlock_criteria) values
  ('Lab Rookie',   'badge',       'Passed lesson 1.',                'common', 'stage_completion', '{"lesson_number":1}'),
  ('Green Sparks', 'trail',       'Passed lesson 2.',                'common', 'stage_completion', '{"lesson_number":2}'),
  ('Tower Breaker','title',       'Passed lesson 3.',                'rare',   'stage_completion', '{"lesson_number":3}'),
  ('Night Coder',  'avatar_skin', 'Passed lesson 4.',                'rare',   'stage_completion', '{"lesson_number":4}'),
  ('Neon Path',    'trail',       'Passed lesson 5.',                'epic',   'stage_completion', '{"lesson_number":5}'),
  ('Code Warden',  'badge',       'Passed lesson 6.',                'epic',   'stage_completion', '{"lesson_number":6}');
