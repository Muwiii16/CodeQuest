-- ============================================================
-- A class can now have stages that are only for class tests. They live in their own campaign (kind = 'test') so
-- they never show in the students' campaign, never take a lesson number and never block unlocking. Every campaign
-- that exists is a lesson campaign.
-- ============================================================

alter table campaigns add column if not exists kind text not null default 'lesson';
alter table campaigns drop constraint if exists campaigns_kind_check;
alter table campaigns add constraint campaigns_kind_check check (kind in ('lesson', 'test'));
create index if not exists campaigns_class_kind on campaigns (class_id, kind);
