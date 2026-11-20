-- ============================================================
-- Join codes: every class has a short code (6 characters, no 0/O/1/I) that a professor gives to the students, who then
-- enroll themselves in the game. The code is made by the database when the class is created (column default), existing
-- classes get one here, and a professor can replace it (the old code then stops working).
-- ============================================================

alter table classes add column if not exists join_code text;

create or replace function new_join_code()
returns text
language plpgsql
volatile
security definer
set search_path = public, extensions
as $$
declare
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; -- 32 characters
  candidate text;
  bytes bytea;
  i int;
begin
  loop
    bytes := gen_random_bytes(6);
    candidate := '';
    for i in 0..5 loop
      candidate := candidate || substr(alphabet, 1 + (get_byte(bytes, i) % 32), 1);
    end loop;
    exit when not exists (select 1 from classes where join_code = candidate);
  end loop;
  return candidate;
end;
$$;

update classes set join_code = new_join_code() where join_code is null;
alter table classes alter column join_code set default new_join_code();
alter table classes alter column join_code set not null;
create unique index if not exists classes_join_code_key on classes (join_code);

-- A student joins with a code. Returns {status, class_id, section_name, subject}; status is
-- ok | already | ended | invalid | not_student.
create or replace function join_class(p_username text, p_code text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_code text := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  v_student_id bigint;
  v_class classes%rowtype;
begin
  if length(v_code) <> 6 then
    return jsonb_build_object('status', 'invalid');
  end if;
  select id into v_student_id from profiles where username = p_username and role = 'student' limit 1;
  if v_student_id is null then
    return jsonb_build_object('status', 'not_student');
  end if;
  select * into v_class from classes where join_code = v_code limit 1;
  if not found then
    return jsonb_build_object('status', 'invalid');
  end if;
  if v_class.ended then
    return jsonb_build_object('status', 'ended', 'class_id', v_class.id, 'section_name', v_class.section_name);
  end if;
  if exists (select 1 from class_enrollments where class_id = v_class.id and student_id = v_student_id) then
    return jsonb_build_object('status', 'already', 'class_id', v_class.id, 'section_name', v_class.section_name,
                              'subject', v_class.subject);
  end if;
  insert into class_enrollments (class_id, student_id) values (v_class.id, v_student_id);
  return jsonb_build_object('status', 'ok', 'class_id', v_class.id, 'section_name', v_class.section_name,
                            'subject', v_class.subject);
end;
$$;

-- A professor replaces a class's code (for example when it was shared too widely). Returns the new code.
create or replace function regenerate_join_code(p_class_id bigint)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  c text := new_join_code();
begin
  update classes set join_code = c where id = p_class_id;
  if not found then
    return null;
  end if;
  return c;
end;
$$;

revoke execute on function new_join_code() from public;
revoke execute on function join_class(text, text) from public;
revoke execute on function regenerate_join_code(bigint) from public;
grant execute on function new_join_code() to anon, authenticated, service_role;
grant execute on function join_class(text, text) to anon, authenticated, service_role;
grant execute on function regenerate_join_code(bigint) to anon, authenticated, service_role;
