package codequest.data;

import codequest.session.RunReplay.Answer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Local save of a run in progress (the seed its questions were drawn with, and the answers so far),
 * kept in the user's home folder under CodeQuest/cache so a student who closes the game by accident
 * can resume a class test or a campaign lesson. Written after every answer; removed when the run
 * is over. Replaying the answers against the same seed lands on the same question.
 */
public final class RunCache {

    public static class Entry {
        public String token;
        public float timeLeft;
    }

    public static class Snapshot {
        public String key;
        public String username;
        public long seed;
        public List<Entry> answers = new ArrayList<>();
    }

    /** What was saved: the question seed and the answers given with it. */
    public record Saved(long seed, List<Answer> answers) {
    }

    private static final String DIR = "CodeQuest/cache/";

    private RunCache() {
    }

    public static String classTestKey(long sessionId) {
        return "classtest-" + sessionId;
    }

    public static String campaignKey(long stageId) {
        return "campaign-" + stageId;
    }

    private static String safe(String username) {
        return username.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static FileHandle file(String key, String username) {
        return Gdx.files.external(DIR + key + "-" + safe(username) + ".json");
    }

    private static Json json() {
        Json json = new Json(JsonWriter.OutputType.json);
        json.setElementType(Snapshot.class, "answers", Entry.class);
        json.setIgnoreUnknownFields(true);
        return json;
    }

    public static void save(String key, String username, long seed, List<Answer> answers) {
        try {
            Snapshot snapshot = new Snapshot();
            snapshot.key = key;
            snapshot.username = username;
            snapshot.seed = seed;
            for (Answer a : answers) {
                Entry e = new Entry();
                e.token = a.token();
                e.timeLeft = a.timeLeft();
                snapshot.answers.add(e);
            }
            file(key, username).writeString(json().toJson(snapshot), false, "UTF-8");
        } catch (RuntimeException e) {
            System.err.println("RunCache: could not save progress: " + e);
        }
    }

    public static boolean exists(String key, String username) {
        try {
            return file(key, username).exists();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The saved run, or empty if there is no usable save. */
    public static Optional<Saved> load(String key, String username) {
        try {
            FileHandle f = file(key, username);
            if (!f.exists()) {
                return Optional.empty();
            }
            Snapshot snapshot = json().fromJson(Snapshot.class, f.readString("UTF-8"));
            if (snapshot == null || !key.equals(snapshot.key) || !username.equals(snapshot.username)) {
                return Optional.empty();
            }
            List<Answer> out = new ArrayList<>();
            for (Entry e : snapshot.answers) {
                out.add(new Answer(e.token, e.timeLeft));
            }
            return Optional.of(new Saved(snapshot.seed, out));
        } catch (RuntimeException e) {
            System.err.println("RunCache: could not read saved progress: " + e);
            return Optional.empty();
        }
    }

    public static void clear(String key, String username) {
        try {
            file(key, username).delete();
        } catch (RuntimeException e) {
            System.err.println("RunCache: could not delete saved progress: " + e);
        }
    }

    /** Removes this student's saves whose key starts with the prefix, except the ones to keep. */
    public static void clearExcept(String keyPrefix, String username, Set<String> keepKeys) {
        try {
            FileHandle dir = Gdx.files.external(DIR);
            if (!dir.exists()) {
                return;
            }
            String suffix = "-" + safe(username) + ".json";
            for (FileHandle f : dir.list()) {
                String name = f.name();
                if (name.startsWith(keyPrefix) && name.endsWith(suffix)) {
                    String key = name.substring(0, name.length() - suffix.length());
                    if (!keepKeys.contains(key)) {
                        f.delete();
                    }
                }
            }
        } catch (RuntimeException e) {
            System.err.println("RunCache: cleanup failed: " + e);
        }
    }
}
