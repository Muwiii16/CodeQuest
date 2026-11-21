package codequest.data;

import codequest.content.AnswerKind;
import codequest.content.CTopic;
import codequest.content.Challenge;
import codequest.content.Difficulty;
import codequest.session.GachaRoll;
import codequest.session.Rank;
import com.badlogic.gdx.utils.JsonValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Talks to Supabase via PostgREST for accounts, student progress, challenges,
 * certificates and cosmetics (the CodeQuest schema — see supabase/migrations).
 * Lessons and classes live in {@link CampaignData} / {@link ProfessorData};
 * class tests in {@link ClassTestData}.
 *
 * Every call blocks the calling thread. Profile ids are cached at class level
 * because every screen constructs a fresh DatabaseManager and they never change.
 */
public class DatabaseManager {

    private static final Map<String, Long> profileIdCache = new ConcurrentHashMap<>();

    public DatabaseManager() {
        if (!SupabaseConfig.isConfigured()) {
            System.err.println("DatabaseManager: Supabase not configured (assets/supabase.properties missing or incomplete).");
        }
    }

    // ── Accounts ─────────────────────────────────────────

    /** A signed-in account: its profile id and role ("student" or "professor"). */
    public record Account(long id, String role) {
        public boolean isProfessor() {
            return "professor".equals(role);
        }
    }

    public enum SignUpResult {
        OK, TAKEN, INVALID, BAD_INVITE, PROFESSOR_DISABLED, TOO_MANY, ERROR
    }

    public enum JoinStatus {
        JOINED, ALREADY_IN, REQUESTED, PENDING, ENDED, INVALID_CODE, NOT_A_STUDENT, TOO_MANY, NOT_SIGNED_IN, ERROR
    }

    /** The answer to a sign-in: the account when the password was right, or {@code locked} after too many wrong tries. */
    public record LoginResult(Optional<Account> account, boolean locked) {
    }

    /** What happened when a student used a class's join code. */
    public record JoinResult(JoinStatus status, long classId, String sectionName, String subject) {
    }

    /**
     * A student enrolls in the class that has this code. The server checks everything: a code that matches no class, a class
     * that has ended, an account that is not a student, and a student who is already in the class.
     */
    public JoinResult joinClass(String typedCode) {
        // the student is whoever is signed in: the server reads it from the session token, not from anything typed here
        JsonValue result = SupabaseClient.post("rpc/join_class", "{\"p_code\":\"" + escape(JoinCode.normalize(typedCode)) + "\"}");
        if (result == null || result.isNull() || !result.isObject()) {
            return new JoinResult(JoinStatus.ERROR, -1L, "", null);
        }
        JoinStatus status = switch (result.getString("status", "")) {
            case "ok" -> JoinStatus.JOINED;
            case "already" -> JoinStatus.ALREADY_IN;
            case "requested" -> JoinStatus.REQUESTED; // the code was right: the professor still has to approve
            case "pending" -> JoinStatus.PENDING;     // asked before, still waiting
            case "ended" -> JoinStatus.ENDED;
            case "invalid" -> JoinStatus.INVALID_CODE;
            case "not_student" -> JoinStatus.NOT_A_STUDENT;
            case "too_many" -> JoinStatus.TOO_MANY;
            case "not_signed_in" -> JoinStatus.NOT_SIGNED_IN;
            default -> JoinStatus.ERROR;
        };
        return new JoinResult(status, result.getLong("class_id", -1L), result.getString("section_name", ""),
                result.getString("subject", null));
    }

    /**
     * Checks the username and password on the server (passwords are stored hashed and compared there,
     * so nothing readable is ever fetched). No account means wrong credentials or the server could not be reached;
     * {@code locked} means too many wrong tries for now. On success the server also hands over a session token, which is
     * kept in memory and sent with every later request (see {@link #logout()}).
     */
    public LoginResult login(String username, String password) {
        SupabaseClient.setSession(null); // a failed attempt must not leave an earlier person's session behind
        JsonValue result = SupabaseClient.post("rpc/login_user",
                "{\"p_username\":\"" + escape(username) + "\",\"p_password\":\"" + escape(password) + "\"}");
        if (result == null || result.isNull() || !result.isObject()) {
            return new LoginResult(Optional.empty(), false);
        }
        if (result.getBoolean("locked", false)) {
            return new LoginResult(Optional.empty(), true);
        }
        String token = result.getString("token", null);
        if (token == null) {
            return new LoginResult(Optional.empty(), false);
        }
        SupabaseClient.setSession(token);
        long id = result.getLong("id");
        profileIdCache.put(username, id);
        return new LoginResult(Optional.of(new Account(id, result.getString("role", "student"))), false);
    }

    /** The answer to "Continue with Google": who signed in (username is the in-game name), or a message to show. */
    public record GoogleResult(Account account, String username, String error) {
    }

    /**
     * Signs in with the school Google account. The browser does the Google part; the server checks the address (it must end
     * with @ub.edu.ph; digits before the @ mean a student, a name means a professor), creates the profile the first time and
     * returns a session token like a password login does. Blocks while the person is in the browser: call it off the render thread.
     */
    public GoogleResult googleLogin() {
        return googleLogin(new GoogleAttempt());
    }

    /** One try at signing in with Google, which the person can cancel while the browser is open. */
    public static final class GoogleAttempt {
        private final GoogleSignIn.Cancel cancel = new GoogleSignIn.Cancel();

        /** Stops waiting for the browser; the sign-in then ends with no account and the attempt reads as cancelled. */
        public void cancel() {
            cancel.cancel();
        }

        public boolean cancelled() {
            return cancel.isCancelled();
        }

        GoogleSignIn.Cancel token() {
            return cancel;
        }
    }

    /** Same, for an attempt that can be cancelled (see {@link GoogleAttempt#cancel}). */
    public GoogleResult googleLogin(GoogleAttempt attempt) {
        SupabaseClient.setSession(null);
        GoogleSignIn.Code code = GoogleSignIn.obtainCode(attempt.token());
        if (code.error() != null) {
            return new GoogleResult(null, null, code.error());
        }
        SupabaseClient.FunctionResult done = SupabaseClient.callFunction("google-login", "{\"action\":\"exchange\",\"code\":\""
                + escape(code.code()) + "\",\"code_verifier\":\"" + escape(code.verifier()) + "\",\"redirect_uri\":\""
                + escape(code.redirectUri()) + "\"}", 30);
        if (attempt.cancelled()) {
            // the person changed their mind: a late answer must not replace the account they sign in with next
            return new GoogleResult(null, null, GoogleSignIn.CANCELLED);
        }
        JsonValue answer = GoogleSignIn.parse(done.body());
        String token = answer == null ? null : answer.getString("token", null);
        if (!done.ok() || token == null) {
            return new GoogleResult(null, null, GoogleSignIn.messageOf(answer, "Could not sign in with Google. Try again."));
        }
        SupabaseClient.setSession(token);
        String username = answer.getString("username", "?");
        long id = answer.getLong("id");
        profileIdCache.put(username, id);
        return new GoogleResult(new Account(id, answer.getString("role", "student")), username, null);
    }

    /**
     * Real names, for the people the caller may see them for: the caller's own, and (for a professor) the students in their
     * own classes. Everyone else is simply missing from the answer. Classmates only ever see game names.
     */
    public static Map<Long, String> fullNames(java.util.Collection<Long> profileIds) {
        Map<Long, String> out = new java.util.HashMap<>();
        if (profileIds.isEmpty()) {
            return out;
        }
        StringBuilder ids = new StringBuilder();
        for (Long id : profileIds) {
            ids.append(ids.length() == 0 ? "" : ",").append(id);
        }
        JsonValue rows = SupabaseClient.post("rpc/app_full_names", "{\"p_ids\":[" + ids + "]}");
        if (rows != null && rows.isArray()) {
            for (JsonValue r : rows) {
                String name = r.getString("full_name", null);
                if (name != null && !name.isBlank()) {
                    out.put(r.getLong("id"), name);
                }
            }
        }
        return out;
    }

    /** Signs out: the session token is dropped here and ended on the server. */
    public void logout() {
        profileIdCache.clear();
        SupabaseClient.endSession();
    }

    /**
     * Creates an account on the server. A professor account also needs the invite code, which is checked
     * there against a stored hash, so the code is not kept in the game files.
     */
    public SignUpResult register(String username, String password, String role, String inviteCode) {
        JsonValue result = SupabaseClient.post("rpc/register_user", "{\"p_username\":\"" + escape(username)
                + "\",\"p_password\":\"" + escape(password) + "\",\"p_role\":\"" + escape(role)
                + "\",\"p_invite\":" + (inviteCode == null ? "null" : "\"" + escape(inviteCode) + "\"") + "}");
        if (result == null || result.isNull()) {
            return SignUpResult.ERROR;
        }
        return switch (result.asString()) {
            case "ok" -> SignUpResult.OK;
            case "taken" -> SignUpResult.TAKEN;
            case "invalid" -> SignUpResult.INVALID;
            case "bad_invite" -> SignUpResult.BAD_INVITE;
            case "professor_disabled" -> SignUpResult.PROFESSOR_DISABLED;
            case "too_many" -> SignUpResult.TOO_MANY;
            default -> SignUpResult.ERROR;
        };
    }

    public record Profile(long id, String username, String fullName, String section) {
    }

    public Optional<Profile> getProfile(String username) {
        JsonValue result = SupabaseClient.get("profiles?username=eq." + SupabaseClient.encode(username)
                + "&select=id,username,section&limit=1");
        if (result == null || result.size == 0) {
            return Optional.empty();
        }
        JsonValue row = result.get(0);
        long id = row.getLong("id");
        return Optional.of(new Profile(id, row.getString("username", username),
                fullNames(java.util.List.of(id)).get(id), row.getString("section", null)));
    }

    /** The profile id for a username, or -1 if it can't be resolved. */
    public long profileId(String username) {
        Long id = resolveProfileId(username);
        return id == null ? -1L : id;
    }

    // ── Progress ─────────────────────────────────────────

    /**
     * Records a finished lesson run: marks it completed and keeps the best score.
     * rank is the S-F letter earned on this run (nullable).
     */
    public void saveLessonProgress(String username, long stageId, String difficulty, int score, String rank) {
        saveLessonProgress(username, stageId, difficulty, score, rank, 0);
    }

    /** As above; maxScore is the most this run could have earned (its questions x 10), kept with the best score. */
    public void saveLessonProgress(String username, long stageId, String difficulty, int score, String rank, int maxScore) {
        System.out.println("Saving progress: " + username + " stage:" + stageId + " score:" + score + " rank:" + rank);

        Long studentId = resolveProfileId(username);
        if (studentId == null) {
            System.err.println("saveLessonProgress: could not resolve the student, skipping save.");
            return;
        }

        JsonValue existing = SupabaseClient.get(
                "campaign_progress?student_id=eq." + studentId + "&stage_id=eq." + stageId
                        + "&select=id,score,attempts,rank,max_score");

        int bestScore = score;
        String bestRank = rank;
        int bestMax = maxScore;
        int attempts = 1;
        Long rowId = null;
        if (existing != null && existing.size > 0) {
            JsonValue row = existing.get(0);
            rowId = row.getLong("id");
            int oldScore = row.getInt("score", 0);
            if (oldScore > score) {
                bestScore = oldScore;
                bestRank = row.getString("rank", rank);
                bestMax = row.getInt("max_score", 0); // the best score has its own maximum
            }
            attempts = row.getInt("attempts", 0) + 1;
        }

        String body = "{\"student_id\":" + studentId + ",\"stage_id\":" + stageId
                + ",\"status\":\"completed\",\"current_difficulty\":\"" + escape(difficulty) + "\""
                + ",\"score\":" + bestScore + ",\"attempts\":" + attempts
                + (bestMax > 0 ? ",\"max_score\":" + bestMax : "")
                + (bestRank != null ? ",\"rank\":\"" + escape(bestRank) + "\"" : "")
                + ",\"last_played_at\":\"" + Instant.now() + "\"}";

        if (rowId != null) {
            SupabaseClient.patch("campaign_progress?id=eq." + rowId, body);
        } else {
            SupabaseClient.post("campaign_progress", body);
        }
    }

    /**
     * Reads a lesson's token challenges from D4 (campaign content). Rows missing a
     * valid topic, difficulty, prompt or correct token are skipped rather than
     * breaking the run. Empty if the lesson has no content or the server is unreachable.
     */
    public List<Challenge> getStageChallenges(long stageId) {
        List<Challenge> challenges = new ArrayList<>();
        JsonValue rows = SupabaseClient.get("challenges?stage_id=eq." + stageId
                + "&select=id,topic,difficulty,prompt,correct_token,distractors,kind,accepted_answers,code_chunk,context,sequence_index,explanation,error_text,"
                + "hints(hint_text,hint_order)&order=section_index,sequence_index");
        if (rows == null) {
            return challenges;
        }
        for (JsonValue row : rows) {
            try {
                String prompt = row.getString("prompt", null);
                String correct = row.getString("correct_token", null);
                if (prompt == null || correct == null) {
                    continue;
                }
                CTopic topic = CTopic.valueOf(row.getString("topic", "").toUpperCase());
                Difficulty difficulty = Difficulty.valueOf(row.getString("difficulty", "").toUpperCase());

                List<String> distractors = new ArrayList<>();
                JsonValue wrong = row.get("distractors");
                if (wrong != null) {
                    for (JsonValue d : wrong) {
                        distractors.add(d.asString());
                    }
                }

                String hint = null;
                int bestOrder = Integer.MAX_VALUE;
                JsonValue hints = row.get("hints");
                if (hints != null) {
                    for (JsonValue h : hints) {
                        int order = h.getInt("hint_order", Integer.MAX_VALUE);
                        if (order < bestOrder) {
                            bestOrder = order;
                            hint = h.getString("hint_text", null);
                        }
                    }
                }
                List<String> accepted = new ArrayList<>();
                JsonValue other = row.get("accepted_answers");
                if (other != null) {
                    for (JsonValue a : other) {
                        accepted.add(a.asString());
                    }
                }
                challenges.add(new Challenge("db-" + row.getLong("id"), topic, difficulty, prompt, correct,
                        distractors, hint, AnswerKind.fromDb(row.getString("kind", null)), accepted,
                        row.getString("code_chunk", null), row.getString("context", null), row.getInt("sequence_index", 0),
                        row.getString("explanation", null), row.getString("error_text", null)));
            } catch (IllegalArgumentException | NullPointerException e) {
                System.err.println("Skipping malformed challenge row " + row.getString("id", "?") + ": " + e);
            }
        }
        return challenges;
    }

    // ── Answer log (D5 submissions) ──────────────────────

    /** One answer a student gave, as the log stores it. */
    public record AnswerLog(long studentId, long stageId, Long sessionId, String mode, String challengeId, CTopic topic,
            Difficulty difficulty, String token, boolean correct, int points, int timeTakenMs, int hintsUsed) {
    }

    /**
     * Writes one answer to the log. Built-in questions (not rows in the challenges table) are recorded by
     * their key instead of a challenge id. Returns false if it could not be saved; callers do not block on it.
     */
    public boolean logAnswer(AnswerLog a) {
        String challengeRef;
        if (a.challengeId() != null && a.challengeId().startsWith("db-")) {
            challengeRef = ",\"challenge_id\":" + a.challengeId().substring(3);
        } else {
            challengeRef = a.challengeId() == null ? "" : ",\"challenge_key\":\"" + escape(a.challengeId()) + "\"";
        }
        String body = "{\"student_id\":" + a.studentId() + ",\"stage_id\":" + a.stageId()
                + (a.sessionId() == null ? "" : ",\"session_id\":" + a.sessionId())
                + ",\"mode\":\"" + escape(a.mode()) + "\"" + challengeRef
                + ",\"topic\":\"" + a.topic().name() + "\",\"difficulty\":\"" + a.difficulty().name().toLowerCase()
                + "\",\"submitted_code\":" + (a.token() == null ? "null" : "\"" + escape(a.token()) + "\"")
                + ",\"is_correct\":" + a.correct() + ",\"points\":" + a.points()
                + ",\"time_taken_ms\":" + a.timeTakenMs() + ",\"time_taken_seconds\":" + Math.round(a.timeTakenMs() / 1000f)
                + ",\"hints_used\":" + a.hintsUsed() + "}";
        return SupabaseClient.post("submissions", body) != null;
    }

    // ── Cosmetics ────────────────────────────────────────

    public record Cosmetic(long id, String name, String category, String description, String rarity,
            String unlockType) {
        public boolean isDefault() {
            return "default".equals(unlockType);
        }

        /** 0 (common) to 3 (legendary): how flashy the item looks. */
        public int rarityRank() {
            return GachaRoll.rank(rarity);
        }
    }

    private static volatile List<Cosmetic> catalogCache;
    private static volatile long catalogCachedAt;
    private static final long CATALOG_TTL_MS = 5 * 60 * 1000L;

    /** The whole catalog, in a stable order. It changes rarely, so it is kept for a few minutes. */
    public List<Cosmetic> getCatalog() {
        List<Cosmetic> cached = catalogCache;
        if (cached != null && System.currentTimeMillis() - catalogCachedAt < CATALOG_TTL_MS) {
            return cached;
        }
        List<Cosmetic> fresh = loadCatalog();
        if (!fresh.isEmpty()) {
            catalogCache = fresh;
            catalogCachedAt = System.currentTimeMillis();
        }
        return fresh;
    }

    private List<Cosmetic> loadCatalog() {
        List<Cosmetic> out = new ArrayList<>();
        JsonValue rows = SupabaseClient.get(
                "cosmetics?select=id,name,category,description,rarity,unlock_type&order=id");
        if (rows == null) {
            return out;
        }
        for (JsonValue r : rows) {
            out.add(new Cosmetic(r.getLong("id"), r.getString("name", "?"), r.getString("category", "weapon"),
                    r.getString("description", ""), r.getString("rarity", "common"),
                    r.getString("unlock_type", "default")));
        }
        return out;
    }

    /** Ids the student can use: everything unlocked, plus the default cosmetics every student starts with. */
    public List<Long> getOwnedCosmetics(long studentId) {
        Set<Long> owned = new HashSet<>();
        JsonValue rows = SupabaseClient.get("student_cosmetics?student_id=eq." + studentId + "&select=cosmetic_id");
        if (rows != null) {
            for (JsonValue r : rows) {
                owned.add(r.getLong("cosmetic_id"));
            }
        }
        JsonValue defaults = SupabaseClient.get("cosmetics?unlock_type=eq.default&select=id");
        if (defaults != null) {
            for (JsonValue r : defaults) {
                owned.add(r.getLong("id"));
            }
        }
        return new ArrayList<>(owned);
    }

    public boolean unlockCosmetic(long studentId, long cosmeticId) {
        JsonValue existing = SupabaseClient.get("student_cosmetics?student_id=eq." + studentId
                + "&cosmetic_id=eq." + cosmeticId + "&select=cosmetic_id");
        if (existing == null) {
            return false;
        }
        if (existing.size > 0) {
            return true;
        }
        return SupabaseClient.post("student_cosmetics",
                "{\"student_id\":" + studentId + ",\"cosmetic_id\":" + cosmeticId + "}") != null;
    }

    public boolean equipCosmetic(long studentId, String category, long cosmeticId) {
        JsonValue existing = SupabaseClient.get("equipped_cosmetics?student_id=eq." + studentId
                + "&category=eq." + SupabaseClient.encode(category) + "&select=cosmetic_id");
        if (existing == null) {
            return false;
        }
        String body = "{\"cosmetic_id\":" + cosmeticId + ",\"equipped_at\":\"" + Instant.now() + "\"}";
        if (existing.size > 0) {
            return SupabaseClient.patch("equipped_cosmetics?student_id=eq." + studentId + "&category=eq."
                    + SupabaseClient.encode(category), body) != null;
        }
        return SupabaseClient.post("equipped_cosmetics", "{\"student_id\":" + studentId + ",\"category\":\""
                + escape(category) + "\",\"cosmetic_id\":" + cosmeticId + "}") != null;
    }

    /** Equipped cosmetic id per category (a category with nothing equipped is simply absent). */
    public Map<String, Long> getEquippedCosmetics(long studentId) {
        Map<String, Long> out = new HashMap<>();
        JsonValue rows = SupabaseClient.get("equipped_cosmetics?student_id=eq." + studentId
                + "&select=category,cosmetic_id");
        if (rows != null) {
            for (JsonValue r : rows) {
                out.put(r.getString("category", ""), r.getLong("cosmetic_id"));
            }
        }
        return out;
    }

    public enum RollStatus {
        /** A cosmetic was won. */
        WON,
        /** This lesson already gave its roll: replaying a lesson gives nothing. */
        ALREADY_CLAIMED,
        ERROR
    }

    /**
     * The result of a reward roll. {@code duplicate}: the student already owned the item. {@code pity}: the roll was
     * forced to be rare or better. {@code rollsUntilGuarantee}: how many rolls until the next forced one (1 = the next roll).
     */
    public record RollOutcome(RollStatus status, Cosmetic cosmetic, boolean duplicate, boolean pity,
            int rollsUntilGuarantee) {
        public static RollOutcome of(RollStatus status) {
            return new RollOutcome(status, null, false, false, GachaRoll.PITY_ROLLS);
        }
    }

    /** How many rolls the student has made, and how many in a row since the last rare or better. */
    public record RollStats(int rolls, int sinceRare) {
        public int untilGuarantee() {
            return Math.max(1, GachaRoll.PITY_ROLLS - sinceRare);
        }
    }

    /** Rarities of the student's rolls, newest first (rolls recorded for lessons finished before the gacha existed are ignored). */
    private List<String> rollHistory(long studentId) {
        List<String> out = new ArrayList<>();
        JsonValue rows = SupabaseClient.get("reward_rolls?student_id=eq." + studentId
                + "&rarity=neq.legacy&select=rarity&order=rolled_at.desc,id.desc&limit=500");
        if (rows != null) {
            for (JsonValue r : rows) {
                out.add(r.getString("rarity", "common"));
            }
        }
        return out;
    }

    public RollStats getRollStats(long studentId) {
        List<String> history = rollHistory(studentId);
        return new RollStats(history.size(), GachaRoll.rollsSinceRare(history));
    }

    /** Ids of the lessons (stages) that already gave this student their roll. */
    public Set<Long> rolledStages(long studentId) {
        Set<Long> out = new HashSet<>();
        JsonValue rows = SupabaseClient.get("reward_rolls?student_id=eq." + studentId + "&stage_id=not.is.null&select=stage_id");
        if (rows != null) {
            for (JsonValue r : rows) {
                out.add(r.getLong("stage_id"));
            }
        }
        return out;
    }

    /**
     * The reward for passing a lesson for the first time: one random cosmetic by the rarity odds. Duplicates can
     * happen; after {@link GachaRoll#PITY_ROLLS} - 1 rolls without anything rare or better the next roll is forced to be
     * rare or better. A lesson gives one roll per student, ever, so replaying it gives nothing. The roll is recorded
     * in the reward_rolls ledger (unique per student and lesson) before the item is unlocked.
     */
    public RollOutcome rollReward(String username, long stageId) {
        Long studentId = resolveProfileId(username);
        if (studentId == null) {
            return RollOutcome.of(RollStatus.ERROR);
        }
        JsonValue claimed = SupabaseClient.get("reward_rolls?student_id=eq." + studentId + "&stage_id=eq." + stageId
                + "&select=id&limit=1");
        if (claimed == null) {
            return RollOutcome.of(RollStatus.ERROR);
        }
        if (claimed.size > 0) {
            return RollOutcome.of(RollStatus.ALREADY_CLAIMED);
        }

        List<Cosmetic> pool = new ArrayList<>();
        for (Cosmetic c : getCatalog()) {
            if ("gacha".equals(c.unlockType())) {
                pool.add(c);
            }
        }
        if (pool.isEmpty()) {
            return RollOutcome.of(RollStatus.ERROR);
        }
        List<String> history = rollHistory(studentId);
        int since = GachaRoll.rollsSinceRare(history);
        boolean pity = GachaRoll.pityDue(since);
        Optional<Cosmetic> won = GachaRoll.roll(pool, Cosmetic::rarity, new java.util.Random(), pity);
        if (won.isEmpty()) {
            return RollOutcome.of(RollStatus.ERROR);
        }
        Cosmetic item = won.get();
        boolean duplicate = getOwnedCosmetics(studentId).contains(item.id());
        boolean forcedByPity = pity && GachaRoll.rank(item.rarity()) >= 1 && since >= GachaRoll.PITY_ROLLS - 1;

        // claim the lesson's roll first: the unique ledger entry is what stops a second roll for the same lesson
        JsonValue entry = SupabaseClient.post("reward_rolls", "{\"student_id\":" + studentId + ",\"stage_id\":" + stageId
                + ",\"cosmetic_id\":" + item.id() + ",\"rarity\":\"" + escape(item.rarity()) + "\",\"was_pity\":"
                + forcedByPity + ",\"was_duplicate\":" + duplicate + "}");
        if (entry == null) {
            JsonValue again = SupabaseClient.get("reward_rolls?student_id=eq." + studentId + "&stage_id=eq." + stageId
                    + "&select=id&limit=1");
            return RollOutcome.of(again != null && again.size > 0 ? RollStatus.ALREADY_CLAIMED : RollStatus.ERROR);
        }
        boolean unlocked = unlockCosmetic(studentId, item.id()) || unlockCosmetic(studentId, item.id());
        if (!unlocked) {
            System.err.println("rollReward: could not unlock " + item.name() + " for " + username);
        }
        return new RollOutcome(RollStatus.WON, item, duplicate, forcedByPity,
                GachaRoll.rollsUntilGuarantee(since, item.rarity()));
    }

    // ── helpers ──────────────────────────────────────────

    private Long resolveProfileId(String username) {
        Long cached = profileIdCache.get(username);
        if (cached != null) {
            return cached;
        }
        JsonValue result = SupabaseClient.get(
                "profiles?username=eq." + SupabaseClient.encode(username) + "&select=id&limit=1");
        if (result != null && result.size > 0) {
            long id = result.get(0).getLong("id");
            profileIdCache.put(username, id);
            return id;
        }
        return null;
    }

    private static String escape(String value) {
        return SupabaseClient.jsonEscape(value);
    }
}
