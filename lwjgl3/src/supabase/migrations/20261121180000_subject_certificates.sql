-- ============================================================
-- Badges and subject certificates.
--   badge        one per lesson a student completed. Not stored: it is read from campaign_progress (a completed lesson).
--   certificate  only for finishing the WHOLE subject: every lesson of the class completed. Stored in certifications
--                (kind = 'subject', class_id = the class), one per student and class.
-- Certificates used to be written by the game itself after a lesson (rank B or better). Now only issue_subject_certificate()
-- writes them, and it checks on the server that the caller really completed every lesson of the class; the app can no longer
-- insert or change certifications directly. Rows from before this change keep kind = 'lesson' and are not shown any more
-- (their lessons show up as badges).
-- ============================================================

alter table certifications add column if not exists class_id bigint references classes(id) on delete set null;
alter table certifications add column if not exists kind text not null default 'lesson';
alter table certifications drop constraint if exists certifications_kind_check;
alter table certifications add constraint certifications_kind_check check (kind in ('lesson', 'subject'));
create unique index if not exists certifications_one_subject_per_student
  on certifications (student_id, class_id) where kind = 'subject';

-- Students only read their own; nobody writes except through the function below.
drop policy if exists sess_student on certifications;
create policy sess_student_read on certifications for select using (student_id = (select app_me()));
drop policy if exists sess_professor_read on certifications;
create policy sess_professor_read on certifications for select
  using (app_owns_class(class_id) or (stage_id is not null and app_owns_stage(stage_id)));

-- Issues (or returns) the subject certificate when the caller completed every lesson of the class. The rank and percentage
-- are the game's average over the lessons (shown on the certificate); the completion itself is checked here.
create or replace function issue_subject_certificate(p_class_id bigint, p_rank text, p_percent numeric)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_me bigint := app_me();
  v_total int;
  v_done int;
  v_row certifications%rowtype;
  v_rank text := case when p_rank in ('S', 'A', 'B', 'C', 'D', 'F') then p_rank else 'F' end;
  v_pct numeric := greatest(0, least(100, coalesce(p_percent, 0)));
begin
  if v_me is null or app_role() <> 'student' then
    return jsonb_build_object('status', 'not_signed_in');
  end if;
  if not app_in_class(p_class_id) then
    return jsonb_build_object('status', 'not_in_class');
  end if;
  select count(*) into v_total
    from stages s join campaigns c on c.id = s.campaign_id
   where c.class_id = p_class_id and c.kind = 'lesson';
  select count(*) into v_done
    from stages s join campaigns c on c.id = s.campaign_id
    join campaign_progress p on p.stage_id = s.id and p.student_id = v_me and p.status = 'completed'
   where c.class_id = p_class_id and c.kind = 'lesson';
  if v_total = 0 or v_done < v_total then
    return jsonb_build_object('status', 'not_finished', 'done', v_done, 'total', v_total);
  end if;
  select * into v_row from certifications where student_id = v_me and class_id = p_class_id and kind = 'subject';
  if not found then
    insert into certifications (student_id, class_id, kind, rank, score_percentage)
    values (v_me, p_class_id, 'subject', v_rank, round(v_pct, 2))
    on conflict do nothing;
    select * into v_row from certifications where student_id = v_me and class_id = p_class_id and kind = 'subject';
  end if;
  return jsonb_build_object('status', 'ok', 'id', v_row.id, 'rank', v_row.rank,
                            'score_percentage', v_row.score_percentage, 'issued_at', v_row.issued_at);
end;
$$;
revoke execute on function issue_subject_certificate(bigint, text, numeric) from public;
grant execute on function issue_subject_certificate(bigint, text, numeric) to anon, authenticated, service_role;
