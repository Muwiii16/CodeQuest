-- ============================================================
-- SECURITY, PHASE A (additive: nothing that works today stops working)
--
-- The game used one public key for everything, so anyone holding it could read and change every table. This migration
-- gives every signed-in person an identity the database can check:
--   * login_user now also returns a random SESSION TOKEN (only its hash is stored, in `sessions`);
--   * the game sends the token in the `x-session` header of every request;
--   * app_me() / app_role() read that header, and the row-level-security policies below say who may see and change what.
-- The new policies are added next to the old open policy ("public access"), so the game that is live today keeps working.
-- PHASE B (pending/20261121130000_security_lockdown.sql) removes the open policy once the new game is released.
-- Also here: limits on wrong passwords, wrong professor access codes and wrong join codes; join codes and invite checks
-- use the signed-in identity instead of a typed username; internal functions are no longer callable from the app.
-- ============================================================

create extension if not exists pgcrypto with schema extensions;

-- ---------- sessions and attempt counters (nobody can read these through the API) ----------
create table if not exists sessions (
  token_hash text primary key,
  profile_id bigint not null references profiles(id) on delete cascade,
  role text not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null
);
create index if not exists sessions_profile on sessions (profile_id);
alter table sessions enable row level security;
revoke all on sessions from anon, authenticated;

create table if not exists login_attempts (
  key text primary key,
  failures int not null default 0,
  window_start timestamptz not null default now()
);
alter table login_attempts enable row level security;
revoke all on login_attempts from anon, authenticated;

-- ---------- who is calling ----------
create or replace function app_token_hash()
returns text
language sql
stable
security definer
set search_path = public, extensions
as $$
  select encode(digest(coalesce(((nullif(current_setting('request.headers', true), ''))::json ->> 'x-session'), '-'), 'sha256'), 'hex')
$$;

create or replace function app_me()
returns bigint
language sql
stable
security definer
set search_path = public, extensions
as $$
  select profile_id from sessions where token_hash = app_token_hash() and expires_at > now() limit 1
$$;

create or replace function app_role()
returns text
language sql
stable
security definer
set search_path = public, extensions
as $$
  select role from sessions where token_hash = app_token_hash() and expires_at > now() limit 1
$$;

-- ---------- what the caller owns or may see (security definer so the policies do not loop through each other) ----------
create or replace function app_owns_class(c bigint)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from classes where id = c and professor_id = app_me())
$$;

create or replace function app_in_class(c bigint)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from class_enrollments where class_id = c and student_id = app_me())
$$;

create or replace function app_owns_campaign(c bigint)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from campaigns where id = c and (professor_id = app_me() or app_owns_class(class_id)))
$$;

create or replace function app_sees_campaign(c bigint)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from campaigns
                  where id = c and (professor_id = app_me() or app_owns_class(class_id) or app_in_class(class_id)))
$$;

create or replace function app_stage_campaign(s bigint)
returns bigint language sql stable security definer set search_path = public as $$
  select campaign_id from stages where id = s
$$;

create or replace function app_owns_stage(s bigint)
returns boolean language sql stable security definer set search_path = public as $$
  select app_owns_campaign(app_stage_campaign(s))
$$;

create or replace function app_sees_stage(s bigint)
returns boolean language sql stable security definer set search_path = public as $$
  select app_sees_campaign(app_stage_campaign(s))
$$;

create or replace function app_challenge_stage(ch bigint)
returns bigint language sql stable security definer set search_path = public as $$
  select stage_id from challenges where id = ch
$$;

create or replace function app_session_class(sid bigint)
returns bigint language sql stable security definer set search_path = public as $$
  select class_id from class_test_sessions where id = sid
$$;

grant execute on function app_token_hash(), app_me(), app_role(), app_owns_class(bigint), app_in_class(bigint),
  app_owns_campaign(bigint), app_sees_campaign(bigint), app_stage_campaign(bigint), app_owns_stage(bigint),
  app_sees_stage(bigint), app_challenge_stage(bigint), app_session_class(bigint) to anon, authenticated, service_role;

-- ---------- attempt limits (internal: only the functions below use them) ----------
create or replace function app_attempts_blocked(p_key text, p_max int, p_window interval)
returns boolean language sql security definer set search_path = public as $$
  select coalesce((select failures >= p_max and window_start > now() - p_window from login_attempts where key = p_key), false)
$$;

create or replace function app_attempt_failed(p_key text, p_window interval)
returns void language plpgsql security definer set search_path = public as $$
begin
  insert into login_attempts (key, failures, window_start) values (p_key, 1, now())
  on conflict (key) do update set
    failures = case when login_attempts.window_start > now() - p_window then login_attempts.failures + 1 else 1 end,
    window_start = case when login_attempts.window_start > now() - p_window then login_attempts.window_start else now() end;
end;
$$;

create or replace function app_attempt_clear(p_key text)
returns void language sql security definer set search_path = public as $$
  delete from login_attempts where key = p_key
$$;

revoke execute on function app_attempts_blocked(text, int, interval), app_attempt_failed(text, interval),
  app_attempt_clear(text) from public, anon, authenticated;

-- ---------- sign in: returns {id, role, token}; after 8 wrong passwords in 15 minutes {locked: true} ----------
create or replace function login_user(p_username text, p_password text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_key text := 'login:' || lower(coalesce(p_username, ''));
  v_row profiles%rowtype;
  v_token text;
begin
  if app_attempts_blocked(v_key, 8, interval '15 minutes') then
    return jsonb_build_object('locked', true);
  end if;
  select * into v_row from profiles
   where username = p_username and password_hash = crypt(p_password, password_hash)
   limit 1;
  if not found then
    perform app_attempt_failed(v_key, interval '15 minutes');
    return null;
  end if;
  perform app_attempt_clear(v_key);
  delete from sessions where expires_at < now();
  v_token := encode(gen_random_bytes(32), 'hex');
  insert into sessions (token_hash, profile_id, role, expires_at)
  values (encode(digest(v_token, 'sha256'), 'hex'), v_row.id, v_row.role, now() + interval '14 days');
  return jsonb_build_object('id', v_row.id, 'role', v_row.role, 'token', v_token);
end;
$$;

-- Ends the caller's session (log out).
create or replace function logout_user()
returns void
language sql
security definer
set search_path = public, extensions
as $$
  delete from sessions where token_hash = app_token_hash()
$$;

-- Sign up: as before, plus a limit on wrong professor access codes (20 per 15 minutes in total) -> 'too_many'.
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
    if app_attempts_blocked('invite', 20, interval '15 minutes') then
      return 'too_many';
    end if;
    select value into invite_hash from app_secrets where key = 'professor_invite_hash';
    if invite_hash is null then
      return 'professor_disabled';
    end if;
    if p_invite is null or crypt(p_invite, invite_hash) <> invite_hash then
      perform app_attempt_failed('invite', interval '15 minutes');
      return 'bad_invite';
    end if;
  end if;
  begin
    insert into profiles (username, password_hash, role)
    values (trim(p_username), crypt(p_password, gen_salt('bf', 10)), p_role);
  exception when unique_violation then
    return 'taken';
  end;
  return 'ok';
end;
$$;

-- ---------- joining a class: the student is whoever is signed in (no typed username), 10 wrong codes per 15 minutes ----------
create or replace function join_class(p_code text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_me bigint := app_me();
  v_code text := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  v_key text;
  v_class classes%rowtype;
begin
  if v_me is null then
    return jsonb_build_object('status', 'not_signed_in');
  end if;
  if app_role() <> 'student' then
    return jsonb_build_object('status', 'not_student');
  end if;
  v_key := 'join:' || v_me;
  if app_attempts_blocked(v_key, 10, interval '15 minutes') then
    return jsonb_build_object('status', 'too_many');
  end if;
  if length(v_code) <> 6 then
    return jsonb_build_object('status', 'invalid');
  end if;
  select * into v_class from classes where join_code = v_code limit 1;
  if not found then
    perform app_attempt_failed(v_key, interval '15 minutes');
    return jsonb_build_object('status', 'invalid');
  end if;
  if v_class.ended then
    return jsonb_build_object('status', 'ended', 'class_id', v_class.id, 'section_name', v_class.section_name);
  end if;
  if exists (select 1 from class_enrollments where class_id = v_class.id and student_id = v_me) then
    return jsonb_build_object('status', 'already', 'class_id', v_class.id, 'section_name', v_class.section_name,
                              'subject', v_class.subject);
  end if;
  insert into class_enrollments (class_id, student_id) values (v_class.id, v_me);
  perform app_attempt_clear(v_key);
  return jsonb_build_object('status', 'ok', 'class_id', v_class.id, 'section_name', v_class.section_name,
                            'subject', v_class.subject);
end;
$$;

-- Only the class's own professor can replace its code.
create or replace function regenerate_join_code(p_class_id bigint)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  c text;
begin
  if not app_owns_class(p_class_id) then
    return null;
  end if;
  c := new_join_code();
  update classes set join_code = c where id = p_class_id;
  return c;
end;
$$;

revoke execute on function join_class(text), logout_user() from public;
grant execute on function join_class(text), logout_user() to anon, authenticated, service_role;

-- ---------- functions the app never calls ----------
-- The professor access code is set by the project owner in the SQL editor; the AI day-counter is used by the Edge Functions
-- (service role) only. new_join_code stays callable: it is the default of classes.join_code.
revoke execute on function set_professor_invite_code(text, text) from public, anon, authenticated;
revoke execute on function ai_take_call(integer), ai_take_call(integer, text) from public, anon, authenticated;
grant execute on function ai_take_call(integer), ai_take_call(integer, text) to service_role;

-- The leaderboard view ran with its owner's rights (so it ignored every policy). It is unused by the game: make it obey them.
alter view leaderboard_totals set (security_invoker = true);
revoke all on leaderboard_totals from anon;

-- ---------- row-level-security policies (added beside the old open ones; they take effect in phase B) ----------
-- profiles: any signed-in person can look people up (the columns are limited to id, username, role, ... by grant); no writes.
create policy sess_read on profiles for select using ((select app_me()) is not null);

-- classes: a professor sees and edits their own; a student sees the classes they are in.
create policy sess_select on classes for select using (professor_id = (select app_me()) or app_in_class(id));
create policy sess_insert on classes for insert
  with check (professor_id = (select app_me()) and (select app_role()) = 'professor');
create policy sess_update on classes for update
  using (professor_id = (select app_me())) with check (professor_id = (select app_me()));
create policy sess_delete on classes for delete using (professor_id = (select app_me()));

-- class_enrollments: a professor manages their class's roster; a student sees their own rows (they join with join_class).
create policy sess_select on class_enrollments for select
  using (student_id = (select app_me()) or app_owns_class(class_id));
create policy sess_insert on class_enrollments for insert with check (app_owns_class(class_id));
create policy sess_delete on class_enrollments for delete using (app_owns_class(class_id));

-- campaigns and stages: the class's professor writes; the class's students read.
create policy sess_select on campaigns for select using (app_sees_campaign(id));
create policy sess_insert on campaigns for insert with check (app_owns_class(class_id));
create policy sess_update on campaigns for update using (app_owns_campaign(id)) with check (app_owns_campaign(id));
create policy sess_delete on campaigns for delete using (app_owns_campaign(id));

create policy sess_select on stages for select using (app_sees_campaign(campaign_id));
create policy sess_insert on stages for insert with check (app_owns_campaign(campaign_id));
create policy sess_update on stages for update using (app_owns_campaign(campaign_id)) with check (app_owns_campaign(campaign_id));
create policy sess_delete on stages for delete using (app_owns_campaign(campaign_id));

-- questions and hints follow their stage.
create policy sess_select on challenges for select using (app_sees_stage(stage_id));
create policy sess_insert on challenges for insert with check (app_owns_stage(stage_id));
create policy sess_update on challenges for update using (app_owns_stage(stage_id)) with check (app_owns_stage(stage_id));
create policy sess_delete on challenges for delete using (app_owns_stage(stage_id));

create policy sess_select on hints for select using (app_sees_stage(app_challenge_stage(challenge_id)));
create policy sess_insert on hints for insert with check (app_owns_stage(app_challenge_stage(challenge_id)));
create policy sess_update on hints for update
  using (app_owns_stage(app_challenge_stage(challenge_id))) with check (app_owns_stage(app_challenge_stage(challenge_id)));
create policy sess_delete on hints for delete using (app_owns_stage(app_challenge_stage(challenge_id)));

-- a student's own progress, answers and certificates; the professor of the stage can read them.
create policy sess_student on campaign_progress for all
  using (student_id = (select app_me())) with check (student_id = (select app_me()));
create policy sess_professor_read on campaign_progress for select using (app_owns_stage(stage_id));

create policy sess_student on submissions for all
  using (student_id = (select app_me())) with check (student_id = (select app_me()));
create policy sess_professor_read on submissions for select using (app_owns_stage(stage_id));

create policy sess_student on certifications for all
  using (student_id = (select app_me())) with check (student_id = (select app_me()));
create policy sess_professor_read on certifications for select using (app_owns_stage(stage_id));

-- class tests: the professor runs them; the class's students join and play.
create policy sess_select on class_test_sessions for select using (app_owns_class(class_id) or app_in_class(class_id));
create policy sess_insert on class_test_sessions for insert with check (app_owns_class(class_id));
create policy sess_update on class_test_sessions for update using (app_owns_class(class_id)) with check (app_owns_class(class_id));
create policy sess_delete on class_test_sessions for delete using (app_owns_class(class_id));

create policy sess_select on class_test_participants for select
  using (student_id = (select app_me()) or app_owns_class(app_session_class(session_id))
         or app_in_class(app_session_class(session_id)));
create policy sess_insert on class_test_participants for insert
  with check (student_id = (select app_me()) and app_in_class(app_session_class(session_id)));
create policy sess_update on class_test_participants for update
  using (student_id = (select app_me())) with check (student_id = (select app_me()));
create policy sess_delete on class_test_participants for delete using (app_owns_class(app_session_class(session_id)));

-- cosmetics: the catalogue is public to read and nobody writes it through the API; a student owns their own collection.
create policy sess_read on cosmetics for select using (true);

create policy sess_student on student_cosmetics for all
  using (student_id = (select app_me())) with check (student_id = (select app_me()));

create policy sess_read on equipped_cosmetics for select using ((select app_me()) is not null);
create policy sess_insert on equipped_cosmetics for insert with check (student_id = (select app_me()));
create policy sess_update on equipped_cosmetics for update
  using (student_id = (select app_me())) with check (student_id = (select app_me()));
create policy sess_delete on equipped_cosmetics for delete using (student_id = (select app_me()));

create policy sess_student on reward_rolls for all
  using (student_id = (select app_me())) with check (student_id = (select app_me()));
