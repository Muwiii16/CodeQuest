-- ============================================================
-- A daily cap on AI calls, so a leaked app key cannot run up a bill. The generate-lesson Edge Function calls
-- ai_take_call() (with the service role) before it asks Gemini; nobody else can reach the table or the function.
-- ============================================================

create table if not exists ai_usage (
  day   date primary key,
  calls int not null default 0
);
alter table ai_usage enable row level security;
revoke all on ai_usage from anon, authenticated;

create or replace function ai_take_call(p_cap int)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  n int;
begin
  insert into ai_usage(day, calls) values (current_date, 1)
  on conflict (day) do update set calls = ai_usage.calls + 1
  returning calls into n;
  return n <= p_cap;
end;
$$;

revoke execute on function ai_take_call(int) from public, anon, authenticated;
grant execute on function ai_take_call(int) to service_role;
