-- The most points a finished lesson run could have earned (its questions x 10). The game used to guess 80 for every lesson.
alter table public.campaign_progress add column if not exists max_score integer;

update public.campaign_progress cp
set max_score = q.n * 10
from (select c.stage_id, count(*) as n from public.challenges c group by c.stage_id) q
join public.stages s on s.id = q.stage_id
where cp.stage_id = q.stage_id and cp.max_score is null and s.program is not null and btrim(s.program) <> '';
