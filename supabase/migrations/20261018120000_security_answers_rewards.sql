-- ============================================================
-- 1. Hashed passwords + server-side sign-in/sign-up
-- 2. Answer log (submissions) columns for gameplay answers
-- 3. More lesson-reward cosmetics (lessons 7-12)
-- ============================================================

create extension if not exists pgcrypto with schema extensions;

-- ---------- passwords ----------
-- Existing plaintext passwords are converted to bcrypt hashes, then the plaintext column is dropped.
alter table profiles add column if not exists password_hash text;
update profiles
   set password_hash = extensions.crypt(password, extensions.gen_salt('bf', 10))
 where password_hash is null and password is not null;
alter table profiles alter column password_hash set not null;
alter table profiles drop column if exists password;

-- The app can no longer read the hash, write profiles directly, or change anyone's role.
-- Signing up and signing in go through the functions below instead.
revoke insert, update, delete on profiles from anon, authenticated;
revoke select on profiles from anon, authenticated;
grant select (id, username, role, full_name, institution, section, created_at) on profiles to anon, authenticated;

-- ---------- private settings (nobody can read this through the API) ----------
create table if not exists app_secrets (
  key text primary key,
  value text not null
);
alter table app_secrets enable row level security;
revoke all on app_secrets from anon, authenticated;

-- Sets the professor invite code. The first call sets it; later calls must give the current code.
-- (Run it as the project owner in the SQL editor to reset a forgotten code:
--   delete from app_secrets where key = 'professor_invite_hash';)
create or replace function set_professor_invite_code(p_new text, p_current text default null)
returns text
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  existing text;
begin
  if p_new is null or length(trim(p_new)) < 6 then
    return 'invalid';
  end if;
  select value into existing from app_secrets where key = 'professor_invite_hash';
  if existing is null then
    insert into app_secrets(key, value) values ('professor_invite_hash', crypt(p_new, gen_salt('bf', 10)));
    return 'set';
  end if;
  if p_current is not null and crypt(p_current, existing) = existing then
    update app_secrets set value = crypt(p_new, gen_salt('bf', 10)) where key = 'professor_invite_hash';
    return 'changed';
  end if;
  return 'denied';
end;
$$;

-- Returns 'ok', 'taken', 'invalid', 'bad_invite' or 'professor_disabled'.
create or replace function register_user(
  p_username text, p_password text, p_role text default 'student', p_invite text default null)
returns text
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  invite_hash text;
begin
  if p_username is null or length(trim(p_username)) < 3 or p_password is null or length(p_password) < 4 then
    return 'invalid';
  end if;
  if p_role is null or p_role not in ('student', 'professor') then
    return 'invalid';
  end if;
  if p_role = 'professor' then
    select value into invite_hash from app_secrets where key = 'professor_invite_hash';
    if invite_hash is null then
      return 'professor_disabled';
    end if;
    if p_invite is null or crypt(p_invite, invite_hash) <> invite_hash then
      return 'bad_invite';
    end if;
  end if;
  begin
    insert into profiles(username, password_hash, role)
    values (trim(p_username), crypt(p_password, gen_salt('bf', 10)), p_role);
  exception when unique_violation then
    return 'taken';
  end;
  return 'ok';
end;
$$;

-- Returns {"id": ..., "role": ...} when the username and password match, otherwise null.
create or replace function login_user(p_username text, p_password text)
returns jsonb
language sql
security definer
set search_path = public, extensions
as $$
  select jsonb_build_object('id', id, 'role', role)
    from profiles
   where username = p_username
     and password_hash = crypt(p_password, password_hash)
   limit 1;
$$;

-- ---------- answer log ----------
-- challenge_id stays empty for the built-in questions (they are not rows); challenge_key names them instead.
alter table submissions alter column challenge_id drop not null;
alter table submissions add column if not exists stage_id bigint references stages(id) on delete cascade;
alter table submissions add column if not exists session_id bigint references class_test_sessions(id) on delete set null;
alter table submissions add column if not exists topic varchar(30);
alter table submissions add column if not exists difficulty varchar(10);
alter table submissions add column if not exists challenge_key text;
alter table submissions add column if not exists points int not null default 0;
alter table submissions add column if not exists time_taken_ms int;
create index if not exists submissions_stage_student on submissions(stage_id, student_id);

-- ---------- more lesson rewards ----------
insert into cosmetics (name, category, description, rarity, unlock_type, unlock_criteria) values
  ('Bug Squasher',   'badge',       'Passed lesson 7.',  'rare',      'stage_completion', '{"lesson_number":7}'),
  ('Aqua Trail',     'trail',       'Passed lesson 8.',  'rare',      'stage_completion', '{"lesson_number":8}'),
  ('Stack Master',   'title',       'Passed lesson 9.',  'epic',      'stage_completion', '{"lesson_number":9}'),
  ('Terminal Ghost', 'avatar_skin', 'Passed lesson 10.', 'epic',      'stage_completion', '{"lesson_number":10}'),
  ('Golden Sparks',  'trail',       'Passed lesson 11.', 'legendary', 'stage_completion', '{"lesson_number":11}'),
  ('Compiler King',  'title',       'Passed lesson 12.', 'legendary', 'stage_completion', '{"lesson_number":12}');
