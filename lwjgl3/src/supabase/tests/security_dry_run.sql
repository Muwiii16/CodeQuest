-- Security dry run: simulates phase B (lockdown) and checks who can see and change what, then ROLLS BACK (it always ends
-- with an error on purpose, which undoes everything, including the zz_ test accounts). Run it in the Supabase SQL editor
-- (or through the SQL tool): the last line of the error message says "ALL PASSED" or lists what failed.
-- Each check runs as the `anon` role with the `x-session` header set, exactly like the game's requests.

create temp table r(label text, got text, expect text) on commit drop;

create function pg_temp.run(p_token text, p_sql text) returns text language plpgsql as $f$
declare n int;
begin
  perform set_config('request.headers', case when p_token is null then '{}' else json_build_object('x-session', p_token)::text end, true);
  set local role anon;
  begin
    execute p_sql;
    get diagnostics n = row_count;
    reset role;
    return 'ok:' || n;
  exception when others then
    reset role;
    return 'err:' || sqlerrm;
  end;
end $f$;

create function pg_temp.val(p_token text, p_sql text) returns text language plpgsql as $f$
declare v text;
begin
  perform set_config('request.headers', case when p_token is null then '{}' else json_build_object('x-session', p_token)::text end, true);
  set local role anon;
  begin
    execute p_sql into v;
    reset role;
    return v;
  exception when others then
    reset role;
    return 'err:' || sqlerrm;
  end;
end $f$;

create function pg_temp.chk(p_label text, p_token text, p_sql text, p_expect text) returns void language plpgsql as $f$
begin
  insert into r values (p_label, pg_temp.run(p_token, p_sql), p_expect);
end $f$;

create function pg_temp.chkv(p_label text, p_token text, p_sql text, p_expect text) returns void language plpgsql as $f$
begin
  insert into r values (p_label, pg_temp.val(p_token, p_sql), p_expect);
end $f$;

do $$
declare
  pa bigint; pb bigint; s1 bigint; s2 bigint; s3 bigint;
  ca bigint; cb bigint; camA bigint; camB bigint; stA bigint; stB bigint; chA bigint; sess bigint;
  tpa text; tpb text; ts1 text; ts2 text; ts3 text; code_a text; code_b text; i int;
  bad text;
begin
  insert into profiles(username,password_hash,role) values ('zz_pa', crypt('pw1234', gen_salt('bf',4)),'professor') returning id into pa;
  insert into profiles(username,password_hash,role) values ('zz_pb', crypt('pw1234', gen_salt('bf',4)),'professor') returning id into pb;
  insert into profiles(username,password_hash,role) values ('zz_s1', crypt('pw1234', gen_salt('bf',4)),'student') returning id into s1;
  insert into profiles(username,password_hash,role) values ('zz_s2', crypt('pw1234', gen_salt('bf',4)),'student') returning id into s2;
  insert into profiles(username,password_hash,role) values ('zz_s3', crypt('pw1234', gen_salt('bf',4)),'student') returning id into s3;
  insert into classes(professor_id, section_name, course_code, language) values (pa,'zzA','ZZ','C') returning id, join_code into ca, code_a;
  insert into classes(professor_id, section_name, course_code, language) values (pb,'zzB','ZZ','C') returning id, join_code into cb, code_b;
  insert into campaigns(class_id, title) values (ca,'zz campaign A') returning id into camA;
  insert into campaigns(class_id, title) values (cb,'zz campaign B') returning id into camB;
  insert into stages(campaign_id, order_index, title) values (camA,1,'zz stage A') returning id into stA;
  insert into stages(campaign_id, order_index, title) values (camB,1,'zz stage B') returning id into stB;
  insert into challenges(stage_id, sequence_index, language, prompt, difficulty, points) values (stA,1,'C','zz q','easy',10) returning id into chA;
  insert into class_enrollments(class_id, student_id) values (ca, s1);
  insert into campaign_progress(student_id, stage_id) values (s1, stA);
  insert into class_test_sessions(class_id, stage_id, session_mode, status) values (ca, stA, 'competitive', 'scheduled') returning id into sess;

  -- real sign-in
  tpa := (login_user('zz_pa','pw1234'))->>'token';
  tpb := (login_user('zz_pb','pw1234'))->>'token';
  ts1 := (login_user('zz_s1','pw1234'))->>'token';
  ts2 := (login_user('zz_s2','pw1234'))->>'token';
  ts3 := (login_user('zz_s3','pw1234'))->>'token';
  insert into r values ('tokens issued', (tpa is not null and ts1 is not null)::text, 'true');
  insert into r values ('token is not stored in clear', (select count(*)::text from sessions where token_hash = tpa), '0');

  -- PHASE B (the same statements as pending/20261121130000_security_lockdown.sql)
  drop policy "public access" on campaign_progress; drop policy "public access" on campaigns; drop policy "public access" on certifications;
  drop policy "public access" on challenges; drop policy "public access" on class_enrollments; drop policy "public access" on class_test_participants;
  drop policy "public access" on class_test_sessions; drop policy "public access" on classes; drop policy "public access" on cosmetics;
  drop policy "public access" on equipped_cosmetics; drop policy "public access" on hints; drop policy "public access" on profiles;
  drop policy "public access" on reward_rolls; drop policy "public access" on stages; drop policy "public access" on student_cosmetics;
  drop policy "public access" on submissions;
  drop function if exists join_class(text, text);

  -- no token at all
  perform pg_temp.chk('anon: classes hidden', null, 'select * from classes', 'ok:0');
  perform pg_temp.chk('anon: profiles hidden', null, 'select id from profiles', 'ok:0');
  perform pg_temp.chk('anon: stages hidden', null, 'select * from stages', 'ok:0');
  perform pg_temp.chk('anon: submissions hidden', null, 'select * from submissions', 'ok:0');
  perform pg_temp.chk('anon: cannot create class', null, format('insert into classes(professor_id, section_name, course_code, language) values (%s,''x'',''x'',''C'')', pa), 'err%');
  perform pg_temp.chk('anon: cannot delete class', null, format('delete from classes where id=%s', ca), 'ok:0');
  perform pg_temp.chk('anon: cosmetics list still readable', null, 'select * from cosmetics', 'ok:%');
  perform pg_temp.chk('anon: dictionary still readable', null, 'select * from dictionary_entries', 'ok:%');
  perform pg_temp.chk('anon: cannot read password hashes', null, 'select password_hash from profiles', 'err%');
  perform pg_temp.chk('forged token is no token', repeat('a',64), 'select * from classes', 'ok:0');
  perform pg_temp.chkv('anon: cannot call ai_take_call', null, 'select ai_take_call(5)::text', 'err%');
  perform pg_temp.chkv('anon: cannot call set_professor_invite_code', null, $q$select set_professor_invite_code('hackhack')$q$, 'err%');
  perform pg_temp.chkv('anon: join_class needs sign-in', null, format('select join_class(%L)::jsonb->>''status''', code_a), 'not_signed_in');

  -- student s1 (enrolled in A)
  perform pg_temp.chk('s1: sees own class only', ts1, 'select * from classes', 'ok:1');
  perform pg_temp.chk('s1: sees its campaign', ts1, 'select * from campaigns', 'ok:1');
  perform pg_temp.chk('s1: sees its stage only', ts1, 'select * from stages', 'ok:1');
  perform pg_temp.chk('s1: sees its question', ts1, 'select * from challenges', 'ok:1');
  perform pg_temp.chk('s1: sees the class test session', ts1, 'select * from class_test_sessions', 'ok:1');
  perform pg_temp.chk('s1: sees own progress', ts1, 'select * from campaign_progress', 'ok:1');
  perform pg_temp.chk('s1: can read profiles (names)', ts1, 'select id, username from profiles', 'ok:%');
  perform pg_temp.chk('s1: cannot create class', ts1, format('insert into classes(professor_id, section_name, course_code, language) values (%s,''x'',''x'',''C'')', s1), 'err%');
  perform pg_temp.chk('s1: cannot edit a stage', ts1, format('update stages set title=''hacked'' where id=%s', stA), 'ok:0');
  perform pg_temp.chk('s1: cannot delete a question', ts1, format('delete from challenges where id=%s', chA), 'ok:0');
  perform pg_temp.chk('s1: cannot add a question', ts1, format('insert into challenges(stage_id, sequence_index, language, prompt, difficulty, points) values (%s,2,''C'',''x'',''easy'',1)', stA), 'err%');
  perform pg_temp.chk('s1: cannot enroll self by insert', ts1, format('insert into class_enrollments(class_id, student_id) values (%s,%s)', cb, s1), 'err%');
  perform pg_temp.chk('s1: cannot read the other class roster', ts1, format('select * from class_enrollments where class_id=%s', cb), 'ok:0');
  perform pg_temp.chk('s1: can save own submission', ts1, format('insert into submissions(student_id, stage_id, mode, is_correct) values (%s,%s,''campaign'',true)', s1, stA), 'ok:1');
  perform pg_temp.chk('s1: cannot save a submission for s2', ts1, format('insert into submissions(student_id, stage_id, mode, is_correct) values (%s,%s,''campaign'',true)', s2, stA), 'err%');
  perform pg_temp.chk('s1: cannot change s2 score', ts1, format('update campaign_progress set score=999 where student_id=%s', s2), 'ok:0');
  perform pg_temp.chk('s1: can update own progress', ts1, format('update campaign_progress set score=50 where student_id=%s', s1), 'ok:1');
  perform pg_temp.chk('s1: cannot write cosmetics catalogue', ts1, 'insert into cosmetics(name, category, rarity, unlock_type) values (''x'',''badge'',''common'',''default'')', 'err%');
  perform pg_temp.chk('s1: cannot grant a cosmetic to s2', ts1, format('insert into student_cosmetics(student_id, cosmetic_id) select %s, id from cosmetics limit 1', s2), 'err%');
  perform pg_temp.chk('s1: can join the class test lobby', ts1, format('insert into class_test_participants(session_id, student_id) values (%s,%s)', sess, s1), 'ok:1');
  perform pg_temp.chk('s1: cannot put s3 in the lobby', ts1, format('insert into class_test_participants(session_id, student_id) values (%s,%s)', sess, s3), 'err%');
  perform pg_temp.chk('s1: cannot start the test', ts1, format('update class_test_sessions set status=''active'' where id=%s', sess), 'ok:0');
  perform pg_temp.chkv('s1: cannot regenerate the code', ts1, format('select regenerate_join_code(%s)', ca), '');

  -- student s3 (not enrolled anywhere)
  perform pg_temp.chk('s3: sees no class', ts3, 'select * from classes', 'ok:0');
  perform pg_temp.chk('s3: sees no stage', ts3, 'select * from stages', 'ok:0');
  perform pg_temp.chk('s3: sees no question', ts3, 'select * from challenges', 'ok:0');
  perform pg_temp.chk('s3: cannot enter the lobby', ts3, format('insert into class_test_participants(session_id, student_id) values (%s,%s)', sess, s3), 'err%');

  -- joining by code
  perform pg_temp.chkv('s2: wrong format', ts2, $q$select join_class('ABC')::jsonb->>'status'$q$, 'invalid');
  perform pg_temp.chkv('s2: joins A with its code', ts2, format('select join_class(%L)::jsonb->>''status''', code_a), 'ok');
  perform pg_temp.chkv('s2: joining again says already', ts2, format('select join_class(%L)::jsonb->>''status''', lower(substr(code_a,1,3)||'-'||substr(code_a,4))), 'already');
  perform pg_temp.chk('s2: now sees class A', ts2, 'select * from classes', 'ok:1');
  perform pg_temp.chkv('professor cannot join a class', tpa, format('select join_class(%L)::jsonb->>''status''', code_b), 'not_student');
  for i in 1..10 loop
    perform pg_temp.chkv('s3: wrong guess '||i, ts3, $q$select join_class('ZZZZZZ')::jsonb->>'status'$q$, 'invalid');
  end loop;
  perform pg_temp.chkv('s3: 11th guess is blocked even with the right code', ts3, format('select join_class(%L)::jsonb->>''status''', code_b), 'too_many');

  -- professor A
  perform pg_temp.chk('pa: sees own class only', tpa, 'select * from classes', 'ok:1');
  perform pg_temp.chk('pa: sees own roster (s1,s2)', tpa, 'select * from class_enrollments', 'ok:2');
  perform pg_temp.chk('pa: sees own stage only', tpa, 'select * from stages', 'ok:1');
  perform pg_temp.chk('pa: sees s1 progress', tpa, 'select * from campaign_progress', 'ok:1');
  perform pg_temp.chk('pa: sees student answers', tpa, 'select * from submissions', 'ok:1');
  perform pg_temp.chk('pa: sees lobby', tpa, 'select * from class_test_participants', 'ok:1');
  perform pg_temp.chk('pa: creates class', tpa, format('insert into classes(professor_id, section_name, course_code, language) values (%s,''zzA2'',''ZZ'',''C'')', pa), 'ok:1');
  perform pg_temp.chk('pa: cannot create a class for pb', tpa, format('insert into classes(professor_id, section_name, course_code, language) values (%s,''zzx'',''ZZ'',''C'')', pb), 'err%');
  perform pg_temp.chk('pa: adds a campaign (no professor_id, like the game)', tpa, format('insert into campaigns(class_id, kind, title) values (%s,''test'',''zz tests'')', ca), 'ok:1');
  perform pg_temp.chk('pa: adds a stage to own campaign', tpa, format('insert into stages(campaign_id, order_index, title) values (%s,2,''zz s2'')', camA), 'ok:1');
  perform pg_temp.chk('pa: cannot add a stage to pb campaign', tpa, format('insert into stages(campaign_id, order_index, title) values (%s,2,''zz hack'')', camB), 'err%');
  perform pg_temp.chk('pa: edits own question', tpa, format('update challenges set prompt=''zz new'' where id=%s', chA), 'ok:1');
  perform pg_temp.chk('pa: adds hint to own question', tpa, format('insert into hints(challenge_id, hint_order, hint_text) values (%s,1,''h'')', chA), 'ok:1');
  perform pg_temp.chk('pa: enrolls a student by hand', tpa, format('insert into class_enrollments(class_id, student_id) values (%s,%s)', ca, s3), 'ok:1');
  perform pg_temp.chk('pa: cannot enroll into pb class', tpa, format('insert into class_enrollments(class_id, student_id) values (%s,%s)', cb, s3), 'err%');
  perform pg_temp.chk('pa: cannot delete pb stage', tpa, format('delete from stages where id=%s', stB), 'ok:0');
  perform pg_temp.chk('pa: starts the test', tpa, format('update class_test_sessions set status=''active'' where id=%s', sess), 'ok:1');
  perform pg_temp.chk('pa: cannot edit pb class', tpa, format('update classes set course_code=''x'' where id=%s', cb), 'ok:0');
  perform pg_temp.chkv('pa: regenerates own code', tpa, format('select (regenerate_join_code(%s) is not null)::text', ca), 'true');
  perform pg_temp.chkv('pa: cannot regenerate pb code', tpa, format('select coalesce(regenerate_join_code(%s), ''null'')', cb), 'null');
  perform pg_temp.chk('pa: drops a student', tpa, format('delete from class_enrollments where class_id=%s and student_id=%s', ca, s3), 'ok:1');
  perform pg_temp.chk('pa cannot read pb class', tpa, format('select * from classes where id=%s', cb), 'ok:0');

  -- sign-in limits and log out
  for i in 1..8 loop perform login_user('zz_s2','wrong'); end loop;
  insert into r values ('9th try is locked even with the right password', coalesce((login_user('zz_s2','pw1234'))->>'locked','no'), 'true');
  insert into r values ('another user is unaffected', (login_user('zz_pb','pw1234') is not null)::text, 'true');
  perform pg_temp.chkv('s1 logs out', ts1, 'select logout_user()::text', '');
  perform pg_temp.chk('after logout the token is dead', ts1, 'select * from classes', 'ok:0');
  for i in 1..20 loop perform register_user('zz_x'||i, 'pw1234', 'professor', 'wrong-invite'); end loop;
  insert into r values ('invite brute force is cut off', register_user('zz_y', 'pw1234', 'professor', 'wrong-invite'), 'professor_disabled|too_many');

  select string_agg(label || ' => ' || got || ' (wanted ' || expect || ')', E'\n') into bad from r
   where not (got like expect or got ~ ('^(' || expect || ')$') or expect = '');
  raise exception E'DRY RUN DONE: % checks, % failed\n%', (select count(*) from r), (select count(*) from r where not (got like expect or got ~ ('^(' || expect || ')$') or expect = '')), coalesce(bad, 'ALL PASSED');
end $$;
