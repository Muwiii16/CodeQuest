-- Professors can search the student directory by name, username or student number (the part of the school email before @).
-- Only the student number is returned, never the email. Students and signed-out callers get nothing.
create or replace function public.app_student_search(p_q text)
returns table(id bigint, username text, full_name text, student_no text)
language sql stable security definer
set search_path to 'public'
as $$
  with q as (
    select replace(replace(replace(lower(trim(coalesce(p_q, ''))), '\', '\\'), '%', '\%'), '_', '\_') as s
  )
  select p.id, p.username::text, p.full_name::text, split_part(e.email, '@', 1)::text
    from profiles p
    left join account_emails e on e.profile_id = p.id, q
   where app_role() = 'professor'
     and length(q.s) >= 2
     and p.role = 'student'
     and (lower(p.username) like '%' || q.s || '%'
          or lower(coalesce(p.full_name, '')) like '%' || q.s || '%'
          or lower(split_part(coalesce(e.email, ''), '@', 1)) like q.s || '%')
   order by (lower(p.username) like q.s || '%' or lower(coalesce(p.full_name, '')) like q.s || '%') desc,
            coalesce(p.full_name, p.username), p.id
   limit 8
$$;

-- The student numbers of students the calling professor already teaches (same rule as app_full_names).
create or replace function public.app_student_numbers(p_ids bigint[])
returns table(id bigint, student_no text)
language sql stable security definer
set search_path to 'public'
as $$
  select p.id, split_part(e.email, '@', 1)::text
    from profiles p
    join account_emails e on e.profile_id = p.id
   where p.id = any(p_ids)
     and app_role() = 'professor'
     and exists (select 1 from class_enrollments en join classes c on c.id = en.class_id
                  where en.student_id = p.id and c.professor_id = app_me())
$$;

revoke all on function public.app_student_search(text) from public;
revoke all on function public.app_student_numbers(bigint[]) from public;
grant execute on function public.app_student_search(text) to anon, authenticated;
grant execute on function public.app_student_numbers(bigint[]) to anon, authenticated;
