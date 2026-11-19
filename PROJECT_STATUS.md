# CodeQuest: project status report (as of 18 November 2026)

Written to be pasted into a chat so a writing assistant can draft sections of the paper. Everything here is what the
build and the repository actually contain today. Items marked **[mismatch]** are places where the manuscript or diagrams
and the build may disagree and the paper should be reconciled.

## 1. What CodeQuest is
CodeQuest ("The Wolves", BSCS capstone, University of Batangas CICT) is an AI-assisted educational tower-defense-style
game that teaches introductory C to first-year BSCS students (CC102). A student's avatar walks a campus path. Towers
that stand beside the path launch a projectile and open a timed coding question. A right answer makes the hero's floating
weapon knock the projectile back and adds code to a growing "code block"; a wrong answer lets the projectile hit the
student and costs health. Two modes: **Campaign** (self-paced, difficulty adapts after every map section) and **Class
Test** (fixed difficulty, same questions for everyone, Kahoot-style speed scoring and live ranking). Professors author
lessons and questions and run class tests. AI is meant to work only before and after a session, never live in play.

## 2. How it got here (timeline)
- **Sep 10-12:** a Swing tower-defense prototype was ported to a LibGDX project (flat `codequest` package, later Java 25,
  Gradle multi-module `core` + `lwjgl3`). Persistence moved to Supabase over HTTPS REST. The old Mythology
  tower-defense mechanic was marked for retirement because it was not CodeQuest's real mechanic.
- **Oct 2-4:** content model designed (topics, difficulty, challenges). Beta-defense design diagrams reviewed (context
  diagram, ERD, DFD level 1, technology architecture).
- **Oct 16-17:** core gameplay rebuilt (`StageRun` state machine, adaptive difficulty, ranks, code block, built-in
  questions), database-backed questions, certificate PDF for rank B or higher. Fixed a long-hidden bug: the HTTP client
  could not send PATCH, so score updates had never been saved. Gameplay verified visually with a screenshot harness.
- **Oct 17-18:** classes, lessons, enrollment, professor dashboard and question editor, class tests with live scoring and
  podium, bcrypt-hashed passwords with server-side sign-in and sign-up, asynchronous loading, answer log.
- **Oct 31 - Nov 1:** gacha cosmetics (hats, floating weapons, trails, titles), duplicates, pity rule, one roll per
  lesson.
- **Nov 9-16:** real pixel art integrated (maps, towers, hero animation, hats, weapons, UI buttons, backgrounds, logo),
  strict tower-placement rules, a readable pixel font across the game.
- **Nov 17:** five ways to answer a question (see section 4).

## 3. Architecture and tech stack
- Java 25, LibGDX (Scene2D for menus, custom drawing for the play screen), Gradle (`core` = rules and screens,
  `lwjgl3` = desktop launcher). PDFBox for certificates. JUnit 5 for tests.
- Supabase (Postgres) over HTTPS REST/PostgREST via `java.net.http.HttpClient`. Not JDBC. **[mismatch]** the technology
  architecture diagram and manuscript Chapter III still say JDBC.
- Pure game rules live in `session/` and `content/` with no LibGDX dependency, so they are unit tested: adaptive
  difficulty, ranks (S 95%, A 85%, B 65%, C 50%, D 35%), scoring, gacha odds and pity, answer matching, question rules.
- Network calls run on background threads behind a "Loading..." cover; reads are cached where safe.
- Run on a laptop with JDK 25: copy `assets/supabase.properties.example` to `supabase.properties`, fill in the project
  URL and anon key, then `gradlew.bat lwjgl3:run`.

## 4. Features built
**Student**
- Login or register, role chosen on the screen but decided by the database. Menu: Campaign, Class Test, Cosmetics,
  Certificates. Lessons unlock in order. Passing = finishing without losing all health.
- Play screen: auto-walking animated hero, towers beside the path (never on it, never overlapping other sprites), timed
  questions, floating weapon that strikes or stumbles, hat on the head, code block on the side, health and score.
- **Five answer kinds**, all ending in one piece of text so scoring, saving and the answer log did not change:
  multiple choice (original), fill in the blank (typed, spaces ignored, case matters), true or false, arrange the code
  (tap tokens in order, optional decoys), find the bug (tap the buggy line). Typing and building get more of the
  projectile's flight time (1.7x, 2.2x, 1.6x; guesses to tune). **[mismatch]** the manuscript describes "token
  selection"; widen the wording. Nothing compiles or runs C.
- **Building-blocks lessons:** a lesson can hold one full C program; its questions are chunks of it in order, and every answered
  question adds its lines to the code block (green if right, red if wrong), so the program is built up block by block. Three
  built-in programs exist (the third is a small calculator). Professors write the program and check that their questions rebuild it.
- Campaign adaptive difficulty after each section; lessons can be resumed; rank and a PDF certificate for rank B or higher.
- Gacha rewards: 24 cosmetics, odds 60/28/9/3 percent (common/rare/epic/legendary), pity after 4 dry rolls, one roll per
  lesson per student, duplicates allowed, items follow the student across classes.
- Class Test: lobby, ready-up, same seeded questions for all, 500-1000 points by speed plus streak bonus, no health loss
  or hints, live rank and gap to the next player, podium reveal, resume after closing the game, tests end when everyone
  finishes or after 20 minutes.

**Professor**
- Sections, lessons, class management, overall performance, per-lesson analysis (worst topics), class-test control and
  history, question editor with a picker for the five kinds and a live preview, option to copy the 44 built-in questions
  into a lesson.
- Stage creation can ask Gemini (through a Supabase Edge Function that holds the key) to draft a lesson: one C program and the
  questions that build it. The draft is validated by the same rules as hand-written questions before the professor sees it, and
  nothing is saved until the professor confirms. Needs the GEMINI_API_KEY secret to be set; not yet tried against the real Gemini.

## 5. Data
- ERD from the manuscript (15 tables) plus later additions (`app_secrets`, `reward_rolls`) and changed columns:
  `classes.language`, `campaigns.class_id`, `stages.prompt`, `class_test_participants.ready`,
  `profiles.password_hash` (plaintext removed), `challenges.topic / section_index / correct_token / distractors /
  kind / accepted_answers`, and `submissions` extended (stage, session, topic, difficulty, key, points, time).
  **[mismatch]** the ERD must be updated to match; cardinality notation was also flagged earlier.
- Security so far: passwords are bcrypt hashes, sign-in and sign-up are server functions, the professor invite code is
  checked against a hash in a private table. **Row-level security is still open on every other table** (anyone holding
  the app key can read and write them). There is no wrong-password lockout. The planned fix is Supabase Auth + RLS.
- Migrations are versioned SQL files and have been applied to the live project.

## 6. Art and interface
- All art is pixel art built from the team's own AI-generated sheets, cleaned and exported by Python scripts (hero
  frames, towers, hats, weapons, three maps with reserved tower slots, backgrounds, logo, buttons). Maps look like a
  university campus with gates and fences.
- Placement rules are enforced by a generator check and unit tests: only the hero is on the path; towers use reserved
  slots beside it.
- Menus and labels use the Pixelify Sans pixel font (OFL). Questions, answers, hints, typed text and the code block use smooth
  DejaVu Sans / DejaVu Sans Mono so code is easy to read.

## 7. Verification
- Unit tests cover rules, matching, scoring, placement, fonts and the built-in question set; all pass.
- Screens were checked by a temporary harness that clicks through the real input path and saves screenshots (play
  screen for all five answer kinds, the professor editor, login, menu). Not yet done: a full playtest with real students,
  and a test on the school's laptops.

## 8. Not done / open
- **AI (deferred by decision):** Gemini for stage generation, dictionary and analysis; Claude for student-requested
  feedback. Not connected. Planned design: keys stay in a Supabase Edge Function secret, never in the game; the game
  falls back to hand-made questions when AI is unavailable. **[mismatch]** the diagrams show Gemini grading
  submissions, while the stated limitation is that AI never runs during play.
- Real security (Supabase Auth + RLS), lockout on wrong passwords.
- Question kinds now rotate (least-used kind first, never the same twice in a row) so a lesson gets a mix, as long as the lesson has more than one kind of question.
- Pixel UI kit applied only to menu buttons; other screens still use flat panels. Sound, "How to play" and Google sign-in
  are not built. Only C is playable (Python, C++, Java show as "coming soon").
- Manuscript fixes still pending: a stray "Java 17" sentence (target is Java 25), "thirteen tables" wording, typos.

## 9. API costs (prices checked 16-17 Nov 2026; verify before quoting)
With about 20 first-year students (assumed 40 AI calls each, about 2K tokens in and 1K out), usage is about 1.6M input
and 0.8M output tokens. Estimated cost: Gemini 2.5 Flash-Lite about $0.5, Claude Haiku 5.5 about $0.6, Gemini 3.1
Flash-Lite about $1.6, Gemini 3.5 Flash-Lite about $2.5, Claude Sonnet 5.5 about $11. Gemini's free tier covers the Flash
and Flash-Lite models at $0 (prompts may be used by Google, so no student names). Supabase Free (500 MB database, 5 GB
egress) is enough; Pro is $25 per month if the project must never pause. Realistic total: $0 to a few dollars.

## 10. Deployment plan
Students play on the team's laptops with JDK 25 installed; each laptop gets the repository plus a local
`supabase.properties`. A standalone packaged build was considered but needs the packaging JDK target changed from 21 to
25 first.

## 11. Dates
Alpha defense Sep 7-13 (done). Beta Oct 5-11 (design diagrams). Omega was planned for Nov 9-15 (Chapters IV-V, frontend and
backend at 90%) and has been moved to the week after 18 Nov. Final defense after the Nov 28 revisions. UAT plan: first-year CC102 students and CICT professors, ISO 25010 (six
characteristics), Google Forms, weighted mean.

## Addendum (18 Nov 2026): what changed after this report was first written
- Adaptive difficulty now changes how each chunk of the lesson's program is asked (choices, typing, fewer or more distractors), while the program itself stays the same. The timer is slower and every blank question carries a one-sentence goal so it has one right answer.
- Classes are now subjects in terms: a student sees "This semester" and "Past subjects" and keeps access to ended subjects; professors mark a class as ended.
- A student-facing dictionary of C terms (34 entries) is on the main menu.
- Gemini drafts a whole lesson (program plus questions) in stage creation through a Supabase Edge Function; it works with the real key but its drafts are validated and sometimes need a retry.
- After every answer the game now shows the right answer and a short explanation of why (and what the student answered, if wrong), and waits for the student to continue.
