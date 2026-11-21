-- ============================================================
-- Join approval: a second layer after the join code.
-- A student who types a valid join code only sends a REQUEST (class_enrollments.status = 'pending'); they are in the class
-- (see its lessons, play, appear in its roster) only after the class's professor approves it ('approved').
-- A student the professor adds by hand is approved at once. Rows that existed before this change are approved.
--   join_class(code) now answers:  requested (new request sent) | pending (already asked, still waiting) | already (approved)
--                                  | invalid | ended | not_student | too_many | not_signed_in
-- ============================================================

alter table class_enrollments add column if not exists status text not null default 'approved';
alter table class_enrollments drop constraint if exists class_enrollments_status_check;
alter table class_enrollments add constraint class_enrollments_status_check check (status in ('pending', 'approved'));

-- Being "in" a class needs the approval. Everything that checks membership (classes, lessons, tests...) goes through this.
create or replace function app_in_class(c bigint)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from class_enrollments where class_id = c and student_id = app_me() and status = 'approved')
$$;

-- The class's professor approves a request (the student cannot: they only see their own row).
drop policy if exists sess_update on class_enrollments;
create policy sess_update on class_enrollments for update
  using (app_owns_class(class_id)) with check (app_owns_class(class_id));

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
  v_status text;
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
  select status into v_status from class_enrollments where class_id = v_class.id and student_id = v_me;
  if found then
    return jsonb_build_object('status', case when v_status = 'approved' then 'already' else 'pending' end,
                              'class_id', v_class.id, 'section_name', v_class.section_name, 'subject', v_class.subject);
  end if;
  insert into class_enrollments (class_id, student_id, status) values (v_class.id, v_me, 'pending');
  perform app_attempt_clear(v_key);
  return jsonb_build_object('status', 'requested', 'class_id', v_class.id, 'section_name', v_class.section_name,
                            'subject', v_class.subject);
end;
$$;
revoke execute on function join_class(text) from public;
grant execute on function join_class(text) to anon, authenticated, service_role;
