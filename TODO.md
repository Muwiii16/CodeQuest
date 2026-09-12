# CodeQuest 0.0.1 — TODO

## UI/UX
- [ ] **Loading visual during Supabase calls.** Screens that hit the database
  (Login, StageSelect, Leaderboard, GameScreen's victory save) block the
  render thread while the network request is in flight. Caching (added in
  DatabaseManager) cuts down how often this happens, but the *first* fetch
  of anything still freezes the frame for the round trip. Needs an actual
  loading spinner/progress indicator shown while waiting, ideally backed by
  moving the network call off the render thread (e.g. a background thread
  + a "loading" screen state) rather than just tolerating a blocking call.
- [ ] Swap the placeholder default `BitmapFont` for the bundled
  `MedievalSharp-Regular.ttf` (needs the `gdx-freetype` extension).
- [ ] Restore emoji in the Tower/Enemy Codex stat labels once a font with
  emoji glyphs is in place (stripped when porting from Swing — see
  TowerCodexScreen/EnemyCodexScreen).
- [ ] Leaderboard rows are flat-tinted rectangles now, not the original's
  gradient/rounded-rect/hover-highlight styling.

## Backend / Data
- [ ] Passwords in `profiles` are stored in plain text (carried over from
  the original prototype, not introduced by the Supabase migration).
  Worth replacing the custom `profiles` auth with real Supabase Auth.
- [ ] RLS policies on every table are currently wide open (`using (true)`)
  since there's no per-user auth/JWT wired in yet. Tighten once Supabase
  Auth (or equivalent) is in place — see `supabase_schema.sql`.
- [ ] `campaign_progress.rank` (S/A/B/C) is never set — no rank-computation
  formula exists yet; needed before certifications can check the B-rank
  (65%+) threshold.

## Gameplay (the big one)
- [ ] The actual game is still the ported *Swing tower-defense prototype*
  reskinned in LibGDX — it doesn't yet implement CodeQuest's real mechanic
  (coding challenges at towers, AI-generated stages/hints/grading/feedback,
  Campaign vs. Class Test modes, certification). The new Supabase schema
  (`challenges`, `hints`, `submissions`, `class_test_sessions`,
  `class_test_participants`, `certifications`) is ready for this but
  currently unused by the app.
- [ ] Class Test Mode multiplayer (competitive/cooperative, real-time) isn't
  built — this is *why* Supabase's REST API was chosen over direct
  Postgres/JDBC, so Realtime can be added later without another migration.
