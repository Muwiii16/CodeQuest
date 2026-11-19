-- ============================================================
-- The daily AI cap is counted per kind of call, so student feedback and class analysis (many small calls)
-- cannot use up the lesson-generation budget, and the other way round. Existing rows are lesson generations.
-- ============================================================

alter table ai_usage add column if not exists kind text not null default 'lesson';
alter table ai_usage drop constraint if exists ai_usage_pkey;
alter table ai_usage add primary key (day, kind);

create or replace function ai_take_call(p_cap int, p_kind text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  n int;
begin
  insert into ai_usage(day, kind, calls) values (current_date, p_kind, 1)
  on conflict (day, kind) do update set calls = ai_usage.calls + 1
  returning calls into n;
  return n <= p_cap;
end;
$$;

-- the one-argument version keeps counting lesson generations (the deployed generate-lesson function uses it)
create or replace function ai_take_call(p_cap int)
returns boolean
language sql
security definer
set search_path = public
as $$
  select ai_take_call(p_cap, 'lesson');
$$;

revoke execute on function ai_take_call(int, text) from public, anon, authenticated;
grant execute on function ai_take_call(int, text) to service_role;
revoke execute on function ai_take_call(int) from public, anon, authenticated;
grant execute on function ai_take_call(int) to service_role;
