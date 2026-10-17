package codequest.data;

import codequest.content.CTopic;
import codequest.content.Challenge;
import codequest.content.Difficulty;
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

    public boolean validateLogin(String username, String password) {
        // Not cached — always check the real credentials.
        String query = "profiles?username=eq." + SupabaseClient.encode(username)
                + "&password=eq." + SupabaseClient.encode(password)
                + "&select=id&limit=1";
        JsonValue result = SupabaseClient.get(query);
        return result != null && result.size > 0;
    }

    public boolean registerUser(String username, String password) {
        return registerUser(username, password, "student");
    }

    /** role must be "student" or "professor"; the caller is responsible for checking the professor invite code. */
    public boolean registerUser(String username, String password, String role) {
        String body = "{\"username\":\"" + escape(username) + "\",\"password\":\"" + escape(password)
                + "\",\"role\":\"" + escape(role) + "\"}";
        JsonValue inserted = SupabaseClient.post("profiles", body);
        return inserted != null;
    }

    public record Profile(long id, String username, String fullName, String section) {
    }

    public Optional<Profile> getProfile(String username) {
        JsonValue result = SupabaseClient.get("profiles?username=eq." + SupabaseClient.encode(username)
                + "&select=id,username,full_name,section&limit=1");
        if (result == null || result.size == 0) {
            return Optional.empty();
        }
        JsonValue row = result.get(0);
        return Optional.of(new Profile(row.getLong("id"), row.getString("username", username),
                row.getString("full_name", null), row.getString("section", null)));
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
        System.out.println("Saving progress: " + username + " stage:" + stageId + " score:" + score + " rank:" + rank);

        Long studentId = resolveProfileId(username);
        if (studentId == null) {
            System.err.println("saveLessonProgress: could not resolve the student, skipping save.");
            return;
        }

        JsonValue existing = SupabaseClient.get(
                "campaign_progress?student_id=eq." + studentId + "&stage_id=eq." + stageId
                        + "&select=id,score,attempts,rank");

        int bestScore = score;
        String bestRank = rank;
        int attempts = 1;
        Long rowId = null;
        if (existing != null && existing.size > 0) {
            JsonValue row = existing.get(0);
            rowId = row.getLong("id");
            int oldScore = row.getInt("score", 0);
            if (oldScore > score) {
                bestScore = oldScore;
                bestRank = row.getString("rank", rank);
            }
            attempts = row.getInt("attempts", 0) + 1;
        }

        String body = "{\"student_id\":" + studentId + ",\"stage_id\":" + stageId
                + ",\"status\":\"completed\",\"current_difficulty\":\"" + escape(difficulty) + "\""
                + ",\"score\":" + bestScore + ",\"attempts\":" + attempts
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
                + "&select=id,topic,difficulty,prompt,correct_token,distractors,hints(hint_text,hint_order)"
                + "&order=section_index,sequence_index");
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
                challenges.add(new Challenge("db-" + row.getLong("id"), topic, difficulty, prompt, correct,
                        distractors, hint));
            } catch (IllegalArgumentException | NullPointerException e) {
                System.err.println("Skipping malformed challenge row " + row.getString("id", "?") + ": " + e);
            }
        }
        return challenges;
    }

    // ── Certificates ─────────────────────────────────────

    public record CertificateRecord(long id, String rank, double scorePercentage, String issuedAt) {
    }

    /** One row of the student's certificate list. */
    public record CertificateListing(long id, long stageId, String lessonTitle, int lessonNumber, String rank,
            double scorePercentage, String issuedAt) {
    }

    /**
     * Writes the lesson certificate to the certifications table (D7) if the rank
     * is B or higher, or returns the existing one; a better score on a later run
     * updates it. Empty means not eligible, or the server couldn't be reached —
     * no certificate is ever handed out without its record being saved.
     */
    public Optional<CertificateRecord> issueCertificate(String username, long stageId, String rank,
            double scorePercentage) {
        if (!Rank.valueOf(rank).passes()) {
            return Optional.empty();
        }
        Long studentId = resolveProfileId(username);
        if (studentId == null) {
            return Optional.empty();
        }
        double pct = Math.round(scorePercentage * 100) / 100.0;

        JsonValue existing = SupabaseClient.get("certifications?student_id=eq." + studentId
                + "&stage_id=eq." + stageId + "&select=id,rank,score_percentage,issued_at");
        if (existing == null) {
            return Optional.empty();
        }
        if (existing.size > 0) {
            JsonValue row = existing.get(0);
            if (pct <= row.getDouble("score_percentage", 0)) {
                return Optional.of(certificateFrom(row));
            }
            String body = "{\"rank\":\"" + escape(rank) + "\",\"score_percentage\":" + pct
                    + ",\"issued_at\":\"" + Instant.now() + "\"}";
            JsonValue updated = SupabaseClient.patch("certifications?id=eq." + row.getLong("id"), body);
            return updated != null && updated.size > 0 ? Optional.of(certificateFrom(updated.get(0)))
                    : Optional.empty();
        }

        String body = "{\"student_id\":" + studentId + ",\"stage_id\":" + stageId + ",\"rank\":\""
                + escape(rank) + "\",\"score_percentage\":" + pct + "}";
        JsonValue inserted = SupabaseClient.post("certifications", body);
        return inserted != null && inserted.size > 0 ? Optional.of(certificateFrom(inserted.get(0)))
                : Optional.empty();
    }

    /** The student's certificates, newest first. */
    public List<CertificateListing> listCertificates(String username) {
        List<CertificateListing> out = new ArrayList<>();
        Long studentId = resolveProfileId(username);
        if (studentId == null) {
            return out;
        }
        JsonValue rows = SupabaseClient.get("certifications?student_id=eq." + studentId
                + "&select=id,stage_id,rank,score_percentage,issued_at,stage:stages(title,order_index)"
                + "&order=issued_at.desc");
        if (rows == null) {
            return out;
        }
        for (JsonValue r : rows) {
            JsonValue stage = r.get("stage");
            out.add(new CertificateListing(r.getLong("id"), r.getLong("stage_id", 0),
                    stage != null ? stage.getString("title", "Lesson") : "Lesson",
                    stage != null ? stage.getInt("order_index", 0) : 0,
                    r.getString("rank", ""), r.getDouble("score_percentage", 0), r.getString("issued_at", "")));
        }
        return out;
    }

    private static CertificateRecord certificateFrom(JsonValue row) {
        return new CertificateRecord(row.getLong("id"), row.getString("rank", ""),
                row.getDouble("score_percentage", 0), row.getString("issued_at", ""));
    }

    // ── Cosmetics ────────────────────────────────────────

    public record Cosmetic(long id, String name, String category, String description, String rarity,
            String unlockType, int lessonNumber) {
        public boolean isDefault() {
            return "default".equals(unlockType);
        }
    }

    /** The whole catalog, in a stable order. */
    public List<Cosmetic> getCatalog() {
        List<Cosmetic> out = new ArrayList<>();
        JsonValue rows = SupabaseClient.get(
                "cosmetics?select=id,name,category,description,rarity,unlock_type,unlock_criteria&order=id");
        if (rows == null) {
            return out;
        }
        for (JsonValue r : rows) {
            JsonValue criteria = r.get("unlock_criteria");
            out.add(new Cosmetic(r.getLong("id"), r.getString("name", "?"), r.getString("category", "badge"),
                    r.getString("description", ""), r.getString("rarity", "common"),
                    r.getString("unlock_type", "default"),
                    criteria != null ? criteria.getInt("lesson_number", 0) : 0));
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

    /** The cosmetics passing lesson number {@code lessonNumber} unlocks. */
    public List<Cosmetic> rewardsForLesson(int lessonNumber) {
        List<Cosmetic> out = new ArrayList<>();
        for (Cosmetic c : getCatalog()) {
            if ("stage_completion".equals(c.unlockType()) && c.lessonNumber() == lessonNumber) {
                out.add(c);
            }
        }
        return out;
    }

    /** Unlocks the lesson's rewards for the student; returns the ones that were new. */
    public List<Cosmetic> grantLessonRewards(String username, int lessonNumber) {
        List<Cosmetic> granted = new ArrayList<>();
        Long studentId = resolveProfileId(username);
        if (studentId == null) {
            return granted;
        }
        Set<Long> owned = new HashSet<>(getOwnedCosmetics(studentId));
        for (Cosmetic c : rewardsForLesson(lessonNumber)) {
            if (!owned.contains(c.id()) && unlockCosmetic(studentId, c.id())) {
                granted.add(c);
            }
        }
        return granted;
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
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
