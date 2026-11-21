-- ============================================================
-- Period certificates. A student now earns a certificate for each grading period (Prelims, Midterms, Semis, Finals) once
-- every lesson of that period is completed, and a second, fancier one for the whole subject (every lesson of the class).
--   certifications.kind = 'period'   with class_id and period     (plain design)
--   certifications.kind = 'subject'  with class_id                (the grand design)
--   certifications.kind = 'lesson'   the old per-lesson ones, no longer shown
-- As before, only the server writes them, after checking that the caller really completed the lessons.
-- ============================================================

alter table certifications add column if not exists period text;
alter table certifications drop constraint if exists certifications_kind_check;
alter table certifications add constraint certifications_kind_check check (kind in ('lesson', 'period', 'subject'));
alter table certifications drop constraint if exists certifications_period_check;
alter table certifications add constraint certifications_period_check
  check (period is null or period in ('prelims', 'midterms', 'semis', 'finals'));
create unique index if not exists certifications_one_period_per_student
  on certifications (student_id, class_id, period) where kind = 'period';

create or replace function issue_period_certificate(p_class_id bigint, p_period text, p_rank text, p_percent numeric)
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
  if p_period is null or p_period not in ('prelims', 'midterms', 'semis', 'finals') then
    return jsonb_build_object('status', 'bad_period');
  end if;
  if not app_in_class(p_class_id) then
    return jsonb_build_object('status', 'not_in_class');
  end if;
  select count(*) into v_total
    from stages s join campaigns c on c.id = s.campaign_id
   where c.class_id = p_class_id and c.kind = 'lesson' and s.period = p_period;
  select count(*) into v_done
    from stages s join campaigns c on c.id = s.campaign_id
    join campaign_progress p on p.stage_id = s.id and p.student_id = v_me and p.status = 'completed'
   where c.class_id = p_class_id and c.kind = 'lesson' and s.period = p_period;
  if v_total = 0 or v_done < v_total then
    return jsonb_build_object('status', 'not_finished', 'done', v_done, 'total', v_total);
  end if;
  select * into v_row from certifications
   where student_id = v_me and class_id = p_class_id and kind = 'period' and period = p_period;
  if not found then
    insert into certifications (student_id, class_id, kind, period, rank, score_percentage)
    values (v_me, p_class_id, 'period', p_period, v_rank, round(v_pct, 2))
    on conflict do nothing;
    select * into v_row from certifications
     where student_id = v_me and class_id = p_class_id and kind = 'period' and period = p_period;
  end if;
  return jsonb_build_object('status', 'ok', 'id', v_row.id, 'rank', v_row.rank,
                            'score_percentage', v_row.score_percentage, 'issued_at', v_row.issued_at);
end;
$$;
revoke execute on function issue_period_certificate(bigint, text, text, numeric) from public;
grant execute on function issue_period_certificate(bigint, text, text, numeric) to anon, authenticated, service_role;
