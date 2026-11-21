-- ============================================================
-- Sign in with the school Google account (@ub.edu.ph).
--
-- The game opens Google in the browser; the Edge Function google-login checks with Google who signed in and passes the
-- verified email and name here. This file decides what the email means:
--   2102122@ub.edu.ph            digits only            -> student
--   rhuelizatordecilla@ub.edu.ph letters (. _ - ok)     -> professor
--   anything else (juan2@..., other domains)            -> refused
-- Then it finds or creates the profile and hands back a session token, exactly as login_user does.
-- ============================================================

-- The email of an account lives in its own table, closed to the app: classmates must not be able to read student numbers.
create table if not exists account_emails (
  profile_id bigint primary key references profiles(id) on delete cascade,
  email text not null unique check (email = lower(email))
);
alter table account_emails enable row level security;
revoke all on account_emails from anon, authenticated;

-- What an email address means. Pure function: null = not allowed.
create or replace function app_email_role(p_email text)
returns text
language sql
immutable
as $$
  select case
    when p_email is null or p_email !~ '^[a-z0-9._-]+@ub\.edu\.ph$' then null
    when split_part(p_email, '@', 1) ~ '^[0-9]+$' then 'student'
    when split_part(p_email, '@', 1) ~ '^[a-z][a-z._-]*$' then 'professor'
    else null
  end
$$;

-- Called only by the Edge Function (service role) after Google confirmed the address. Returns
-- {error:'not_allowed'} or {id, role, username, full_name, token, created}.
create or replace function app_google_login(p_email text, p_name text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_email text := lower(trim(coalesce(p_email, '')));
  v_role text := app_email_role(lower(trim(coalesce(p_email, ''))));
  v_name text := nullif(left(trim(coalesce(p_name, '')), 100), '');
  v_pid bigint;
  v_row profiles%rowtype;
  v_base text;
  v_user text;
  v_try int := 0;
  v_created boolean := false;
  v_token text;
begin
  if v_role is null then
    return jsonb_build_object('error', 'not_allowed');
  end if;

  select profile_id into v_pid from account_emails where email = v_email;

  if v_pid is null then
    -- first time: the game name is the first name (a student number is not shown to classmates); a number is added when taken
    v_base := regexp_replace(split_part(coalesce(v_name, ''), ' ', 1), '[^A-Za-z]', '', 'g');
    if length(v_base) < 3 then
      v_base := case v_role when 'student' then 'Student' else 'Professor' end;
    end if;
    v_base := left(initcap(lower(v_base)), 14);
    loop
      v_user := case when v_try = 0 then v_base else v_base || (1000 + floor(random() * 9000))::int end;
      begin
        insert into profiles (username, password_hash, role, full_name)
        values (v_user, crypt(encode(gen_random_bytes(24), 'hex'), gen_salt('bf')), v_role, v_name)
        returning id into v_pid;
        exit;
      exception when unique_violation then
        v_try := v_try + 1;
        if v_try > 25 then
          return jsonb_build_object('error', 'busy');
        end if;
      end;
    end loop;
    begin
      insert into account_emails (profile_id, email) values (v_pid, v_email);
    exception when unique_violation then
      -- two sign-ins for the same address at the same moment: keep the first profile
      delete from profiles where id = v_pid;
      select profile_id into v_pid from account_emails where email = v_email;
    end;
    v_created := true;
  elsif v_name is not null then
    update profiles set full_name = v_name where id = v_pid and full_name is distinct from v_name;
  end if;

  select * into v_row from profiles where id = v_pid;
  delete from sessions where expires_at < now();
  v_token := encode(gen_random_bytes(32), 'hex');
  insert into sessions (token_hash, profile_id, role, expires_at)
  values (encode(digest(v_token, 'sha256'), 'hex'), v_row.id, v_row.role, now() + interval '14 days');
  return jsonb_build_object('id', v_row.id, 'role', v_row.role, 'username', v_row.username,
                            'full_name', v_row.full_name, 'token', v_token, 'created', v_created);
end;
$$;
revoke all on function app_google_login(text, text) from public, anon, authenticated;
grant execute on function app_google_login(text, text) to service_role;

-- Real names are shown only where they matter: to the person themselves, and to a professor for the students in their own
-- classes. (After the lockdown the full_name column is no longer readable directly, see the phase B file.)
create or replace function app_full_names(p_ids bigint[])
returns table(id bigint, full_name text)
language sql
stable
security definer
set search_path = public
as $$
  select p.id, p.full_name::text
    from profiles p
   where p.id = any(p_ids)
     and app_me() is not null
     and (p.id = app_me()
          or (app_role() = 'professor'
              and exists (select 1 from class_enrollments e join classes c on c.id = e.class_id
                           where e.student_id = p.id and c.professor_id = app_me())))
$$;
revoke all on function app_full_names(bigint[]) from public;
grant execute on function app_full_names(bigint[]) to anon, authenticated;
