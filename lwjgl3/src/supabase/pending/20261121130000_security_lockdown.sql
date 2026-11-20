-- ============================================================
-- SECURITY, PHASE B: LOCKDOWN  (NOT applied yet)
--
-- Apply this ONLY after the game that sends the session token (branch security/session-tokens) is the game people run:
-- from the moment it is applied, a game without the token can no longer read or write anything but the public lists
-- (cosmetics, dictionary) and the sign-in / sign-up calls.
-- After applying, move this file into ../migrations/ so the folder shows what the database really has.
--
-- To undo (emergency only):  create policy "public access" on <table> for all using (true) with check (true);  for each table,
-- and  delete from app_secrets where key = 'enforce_sessions';
-- ============================================================

-- 1. Remove the open policy everywhere: from now on only the policies of phase A decide (they check the session token).
drop policy if exists "public access" on campaign_progress;
drop policy if exists "public access" on campaigns;
drop policy if exists "public access" on certifications;
drop policy if exists "public access" on challenges;
drop policy if exists "public access" on class_enrollments;
drop policy if exists "public access" on class_test_participants;
drop policy if exists "public access" on class_test_sessions;
drop policy if exists "public access" on classes;
drop policy if exists "public access" on cosmetics;
drop policy if exists "public access" on equipped_cosmetics;
drop policy if exists "public access" on hints;
drop policy if exists "public access" on profiles;
drop policy if exists "public access" on reward_rolls;
drop policy if exists "public access" on stages;
drop policy if exists "public access" on student_cosmetics;
drop policy if exists "public access" on submissions;

-- 2. The old join function trusted a typed username (anyone could enroll anyone). Only join_class(code) remains.
drop function if exists join_class(text, text);

-- 3. The public lists are read-only for the app; nothing else is writable without a policy that says so.
revoke insert, update, delete, truncate, references, trigger on cosmetics, dictionary_entries from anon, authenticated;
revoke truncate, references, trigger on all tables in schema public from anon, authenticated;

-- 4. The Edge Functions (generate-lesson, ai-feedback) start requiring a valid session, and professors only for lessons.
insert into app_secrets (key, value) values ('enforce_sessions', 'on')
on conflict (key) do update set value = 'on';
