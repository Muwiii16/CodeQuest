package codequest.data;

import com.badlogic.gdx.utils.JsonValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Talks to Supabase via PostgREST, against the CodeQuest schema (profiles /
 * campaigns / stages / campaign_progress / leaderboard_totals — see
 * supabase_schema.sql), not the old Tower-of-Engkanto users/leaderboard/
 * game_saves tables.
 *
 * The current gameplay only has 3 fixed stages (the old tower-defense reskin),
 * seeded once as a "Default Campaign" with stages at order_index 1-3. This
 * class resolves those by order_index rather than hardcoding IDs, so it stays
 * correct once real Campaign Mode content replaces this placeholder.
 *
 * Every call still blocks the calling thread — caching below cuts down how
 * *often* that happens (every screen constructs a fresh DatabaseManager, so
 * without a class-level cache every navigation re-fetched things that never
 * changed), but the first fetch of anything still pays for a real network
 * round trip. See TODO.md for the follow-up: a loading visual for that.
 */
public class DatabaseManager {

    // Static: these need to survive across the many short-lived DatabaseManager
    // instances each screen creates, not just one instance's lifetime.
    private static final Map<String, Long> profileIdCache = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> stageIdCache = new ConcurrentHashMap<>();
    private static final Map<String, Integer> unlockedStageCache = new ConcurrentHashMap<>();
    private static volatile List<String[]> leaderboardCache = null;

    public DatabaseManager() {
        if (!SupabaseConfig.isConfigured()) {
            System.err.println("DatabaseManager: Supabase not configured (assets/supabase.properties missing or incomplete).");
        }
    }

    public boolean validateLogin(String username, String password) {
        // Not cached — always check the real credentials.
        String query = "profiles?username=eq." + SupabaseClient.encode(username)
                + "&password=eq." + SupabaseClient.encode(password)
                + "&select=id&limit=1";
        JsonValue result = SupabaseClient.get(query);
        return result != null && result.size > 0;
    }

    public boolean registerUser(String username, String password) {
        String body = "{\"username\":\"" + escape(username) + "\",\"password\":\"" + escape(password)
                + "\",\"role\":\"student\"}";
        JsonValue inserted = SupabaseClient.post("profiles", body);
        return inserted != null;
    }

    public int getUnlockedStage(String username) {
        Integer cached = unlockedStageCache.get(username);
        if (cached != null) {
            return cached;
        }

        Long studentId = resolveProfileId(username);
        if (studentId == null) {
            return 1;
        }

        JsonValue result = SupabaseClient.get(
                "campaign_progress?student_id=eq." + studentId + "&status=eq.completed"
                        + "&select=stage:stages(order_index)");
        int maxCompleted = 0;
        if (result != null) {
            for (JsonValue row : result) {
                JsonValue stage = row.get("stage");
                if (stage != null) {
                    maxCompleted = Math.max(maxCompleted, stage.getInt("order_index", 0));
                }
            }
        }

        int unlocked = Math.min(maxCompleted + 1, 3);
        unlockedStageCache.put(username, unlocked);
        return unlocked;
    }

    public List<String[]> getTopLeaderboard() {
        List<String[]> cached = leaderboardCache;
        if (cached != null) {
            return cached;
        }

        List<String[]> results = new ArrayList<>();
        JsonValue result = SupabaseClient.get(
                "leaderboard_totals?select=username,total_points,best_stage,best_difficulty,last_played&limit=10");
        if (result != null) {
            for (JsonValue row : result) {
                results.add(new String[] {
                        row.getString("username", ""),
                        String.valueOf(row.getInt("total_points", 0)),
                        String.valueOf(row.getInt("best_stage", 0)),
                        row.getString("best_difficulty", ""),
                        row.getString("last_played", "")
                });
            }
        }
        leaderboardCache = results;
        return results;
    }

    public void saveGameProgress(String username, int stageCompleted, String difficulty, int score) {
        System.out.println("Saving progress: " + username + " stage:" + stageCompleted + " score:" + score);

        Long studentId = resolveProfileId(username);
        Long stageId = resolveStageId(stageCompleted);
        if (studentId == null || stageId == null) {
            System.err.println("saveGameProgress: could not resolve student/stage, skipping save.");
            return;
        }

        JsonValue existing = SupabaseClient.get(
                "campaign_progress?student_id=eq." + studentId + "&stage_id=eq." + stageId
                        + "&select=id,score,attempts");

        int bestScore = score;
        int attempts = 1;
        Long rowId = null;
        if (existing != null && existing.size > 0) {
            JsonValue row = existing.get(0);
            rowId = row.getLong("id");
            bestScore = Math.max(row.getInt("score", 0), score);
            attempts = row.getInt("attempts", 0) + 1;
        }

        String body = "{\"student_id\":" + studentId + ",\"stage_id\":" + stageId
                + ",\"status\":\"completed\",\"current_difficulty\":\"" + escape(difficulty) + "\""
                + ",\"score\":" + bestScore + ",\"attempts\":" + attempts
                + ",\"last_played_at\":\"" + Instant.now() + "\"}";

        if (rowId != null) {
            SupabaseClient.patch("campaign_progress?id=eq." + rowId, body);
            System.out.println("Updated campaign_progress (best score " + bestScore + ").");
        } else {
            SupabaseClient.post("campaign_progress", body);
            System.out.println("New campaign_progress row added.");
        }

        // This save is the only thing that can change either of these, so only
        // invalidate here rather than giving every cache a time-based expiry.
        unlockedStageCache.remove(username);
        leaderboardCache = null;
    }

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

    private Long resolveStageId(int orderIndex) {
        Long cached = stageIdCache.get(orderIndex);
        if (cached != null) {
            return cached;
        }
        JsonValue result = SupabaseClient.get(
                "stages?order_index=eq." + orderIndex + "&select=id&limit=1");
        if (result != null && result.size > 0) {
            long id = result.get(0).getLong("id");
            stageIdCache.put(orderIndex, id);
            return id;
        }
        return null;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
