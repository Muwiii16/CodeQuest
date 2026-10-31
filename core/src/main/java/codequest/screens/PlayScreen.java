package codequest.screens;

import codequest.Main;
import codequest.content.CTopic;
import codequest.content.Challenge;
import codequest.content.ChallengeProvider;
import codequest.content.DefaultStages;
import codequest.content.Difficulty;
import codequest.content.FallbackChallengeProvider;
import codequest.content.PooledChallengeProvider;
import codequest.content.Section;
import codequest.content.Stage;
import codequest.content.StaticChallengeProvider;
import codequest.data.ClassTestData;
import codequest.data.DatabaseManager;
import codequest.data.Lesson;
import codequest.data.Poller;
import codequest.data.RunCache;
import codequest.session.RunReplay;
import codequest.session.RunSummary;
import codequest.game.GameMap;
import codequest.session.CodeBlock;
import codequest.session.SectionResult;
import codequest.session.StageRun;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The CodeQuest gameplay screen: an avatar auto-walks the path; as it nears a
 * tower a projectile launches and a token-picking challenge opens; the player
 * must pick the right token before the projectile lands. All rules live in
 * StageRun — this class only walks, draws and relays clicks.
 *
 * The avatar pauses while a challenge is open (the placeholder path is too
 * short to keep walking and still give every tower its own challenge).
 * Avatar, towers and projectile are plain shapes until the art pass.
 * Coordinates are top-left origin, y down, like the rest of the game's layout.
 */
public class PlayScreen extends InputAdapter implements Screen {

    private enum Phase { WALKING, ENCOUNTER, FEEDBACK, RESULT }

    private record TowerSpot(float s, float x, float y, CTopic topic) {
    }

    private record Rect(float x, float y, float w, float h) {
        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    private static final float W = BaseScreen.VIRTUAL_WIDTH;
    private static final float H = BaseScreen.VIRTUAL_HEIGHT;
    private static final float MAP_WIDTH = 1400f;
    private static final float SIDEBAR_X = MAP_WIDTH;
    private static final float WALK_SPEED = 90f;
    private static final float TRIGGER_AHEAD = 140f;
    private static final float LEAD_IN = 220f;
    private static final float PANEL_H = 440f;

    private static final Rect QUIT_BTN = new Rect(SIDEBAR_X + 20, 980, 480, 60);
    private static final Rect CERT_BTN = new Rect(480, 880, 420, 80);
    private static final Rect CONTINUE_PAIRED = new Rect(1020, 880, 420, 80);
    private static final Color GOLD_TEXT = new Color(1f, 0.82f, 0.3f, 1f);
    private static final Rect CONTINUE_ALONE = new Rect(760, 880, 400, 80);

    private final Main game;
    private final String username;
    private final Lesson lesson;
    private final ClassTestData.Session testSession;
    private final boolean classTest;
    private final Difficulty startDifficulty;
    private final int towerCount;
    private final DatabaseManager db = new DatabaseManager();
    private CosmeticLook.Outfit outfit;
    private WeaponRig rig;
    private volatile DatabaseManager.RollOutcome rollOutcome;

    // what happens to the projectile after an answer: held in place until the weapon hits it, then knocked
    // back to its tower (right answer), or carried on into the student (wrong answer)
    private enum Fx { NONE, FROZEN, KNOCKBACK, HIT }

    private Fx fx = Fx.NONE;
    private float fxT;
    private float fxX;
    private float fxY;
    private float fxFromX;
    private float fxFromY;
    private float fxToX;
    private float fxToY;
    private float avatarFlash;

    // The answers so far and the seed the questions are drawn with are saved locally after every answer, so a
    // class test or a campaign lesson can be resumed after the game was closed.
    private final String cacheKey;
    private final long seed;
    private final List<RunReplay.Answer> answers = new ArrayList<>();
    private final long studentId;
    private final ClassTestData tests = new ClassTestData();
    private final ExecutorService uploader;
    private volatile boolean saveDone = true;

    // class test only: live standings
    private record Live(List<ClassTestData.Participant> participants, String status) {
    }

    private final Poller<Live> standings;
    private int liveRank;
    private int liveTotal;
    private int pointsBehind;
    private boolean timesUp;

    private final StageRun run;
    private final GameMap map;
    private final List<Point> pts;
    private final float[] cum;
    private final float pathLength;
    private final List<TowerSpot> towers = new ArrayList<>();

    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport = new FitViewport(W, H, camera);
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer sr = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final Map<String, Texture> sprites = new HashMap<>();
    private final GlyphLayout layout = new GlyphLayout();
    private final Random rng;

    private Phase phase = Phase.WALKING;
    private float distance = 0f;
    private int towerIdx = 0;

    private Challenge active;
    private List<String> options = List.of();
    private float timeLeft;
    private float flightTime;
    private boolean hintShown;
    private float panelTop;

    private String feedback = "";
    private Color feedbackColor = Color.WHITE;
    private float feedbackTimer;
    private boolean saved;

    /** Everything the screen needs from the database, fetched beforehand (see {@link #open}) so starting never freezes. */
    public record PlayData(List<Challenge> authored, long studentId, Map<String, Long> equipped,
            List<DatabaseManager.Cosmetic> catalog) {

        static PlayData fetch(DatabaseManager db, String username, Lesson lesson) {
            CompletableFuture<List<Challenge>> questions = CompletableFuture
                    .supplyAsync(() -> db.getStageChallenges(lesson.stageId()));
            CompletableFuture<Object[]> student = CompletableFuture.supplyAsync(() -> {
                long id = db.profileId(username);
                return new Object[] { id, id < 0 ? Map.<String, Long>of() : db.getEquippedCosmetics(id) };
            });
            CompletableFuture<List<DatabaseManager.Cosmetic>> catalog = CompletableFuture.supplyAsync(db::getCatalog);
            Object[] s = student.join();
            @SuppressWarnings("unchecked")
            Map<String, Long> equipped = (Map<String, Long>) s[1];
            return new PlayData(questions.join(), (Long) s[0], equipped, catalog.join());
        }
    }

    /**
     * Starts a lesson: a loading screen fetches the questions and the student's look in the background, then the
     * game opens. testSession != null plays it as a class test (fixed difficulty, same questions for everyone);
     * resume continues from the answers saved on this computer. A null lesson is looked up from the session.
     */
    public static void open(Main game, String username, Lesson lesson, ClassTestData.Session testSession,
            boolean resume, Runnable onFail) {
        DatabaseManager db = new DatabaseManager();
        LoadingScreen.open(game, "Loading the lesson...", () -> {
            Lesson l = lesson != null ? lesson
                    : new codequest.data.CampaignData().lesson(testSession.stageId()).orElseThrow();
            return Map.entry(l, PlayData.fetch(db, username, l));
        }, loaded -> game.setScreen(new PlayScreen(game, username, loaded.getKey(), testSession, resume,
                loaded.getValue())), onFail);
    }

    private PlayScreen(Main game, String username, Lesson lesson, ClassTestData.Session testSession, boolean resume,
            PlayData data) {
        this.game = game;
        this.username = username;
        this.lesson = lesson;
        this.testSession = testSession;
        this.classTest = testSession != null;
        this.startDifficulty = lesson.difficulty();
        this.studentId = data.studentId();
        this.cacheKey = classTest ? RunCache.classTestKey(testSession.id()) : RunCache.campaignKey(lesson.stageId());

        Optional<RunCache.Saved> savedRun = resume ? RunCache.load(cacheKey, username) : Optional.empty();
        this.seed = classTest ? testSession.id() : savedRun.map(RunCache.Saved::seed).orElseGet(() -> new Random().nextLong());
        this.rng = new Random(seed);

        Stage stage = DefaultStages.forNumber(DefaultStages.layoutFor(lesson.number()));
        this.towerCount = stage.towerCount();
        // D4 (campaign content) first; the built-in questions only cover what D4 does not have yet.
        ChallengeProvider provider = new FallbackChallengeProvider(new PooledChallengeProvider(data.authored(), seed),
                new StaticChallengeProvider());
        this.run = new StageRun(stage, startDifficulty, classTest ? StageRun.Mode.CLASS_TEST : StageRun.Mode.CAMPAIGN,
                provider, lesson.adaptive());
        applyLook(data);

        this.uploader = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "codequest-play-upload");
            t.setDaemon(true);
            return t;
        });
        if (classTest) {
            this.standings = new Poller<>(() -> {
                List<ClassTestData.Participant> ps = tests.participants(testSession.id());
                String status = tests.session(testSession.id()).map(s -> tests.endIfOver(s, ps)).orElse("active");
                return new Live(ps, status);
            }, 2000);
        } else {
            this.standings = null;
        }

        this.map = new GameMap(DefaultStages.layoutFor(lesson.number()), (int) MAP_WIDTH);
        this.pts = map.getWaypoints();
        this.cum = new float[pts.size()];
        for (int i = 1; i < pts.size(); i++) {
            cum[i] = cum[i - 1] + (float) pts.get(i).distance(pts.get(i - 1));
        }
        this.pathLength = cum[cum.length - 1];

        float secLen = (pathLength - LEAD_IN) / stage.sections().size();
        for (int k = 0; k < stage.sections().size(); k++) {
            Section section = stage.sections().get(k);
            int n = section.towerTopics().size();
            for (int j = 0; j < n; j++) {
                float s = LEAD_IN + k * secLen + (j + 1) * secLen / (n + 1);
                Vector2 on = pointAt(s);
                Vector2 dir = pointAt(s + 5).sub(pointAt(s - 5)).nor();
                float side = towers.size() % 2 == 0 ? 1f : -1f;
                towers.add(new TowerSpot(s, on.x - dir.y * 70f * side, on.y + dir.x * 70f * side,
                        section.towerTopics().get(j)));
            }
        }

        if (resume && savedRun.isPresent()) {
            resumeFrom(savedRun.get());
        } else {
            RunCache.clear(cacheKey, username);
        }
    }

    /** Replays the saved answers so the run is exactly where the student left it. */
    private void resumeFrom(RunCache.Saved savedRun) {
        RunReplay.replay(run, savedRun.answers());
        answers.addAll(savedRun.answers());
        towerIdx = answers.size();
        if (run.isFailed() || run.nextChallenge().isEmpty() || towerIdx >= towers.size()) {
            towerIdx = towers.size();
            finishRun();
        } else {
            distance = Math.max(0f, towers.get(towerIdx).s() - TRIGGER_AHEAD - 160f);
        }
    }

    /** Rank among the classmates using the newest standings plus this student's own up-to-date points. */
    private void updateStanding() {
        Live live = standings == null ? null : standings.latest();
        if (live == null) {
            return;
        }
        int mine = run.score();
        int ahead = 0;
        int gap = Integer.MAX_VALUE;
        int total = 1;
        for (ClassTestData.Participant p : live.participants()) {
            if (p.studentId() == studentId) {
                continue;
            }
            total++;
            if (p.score() > mine) {
                ahead++;
                gap = Math.min(gap, p.score() - mine);
            }
        }
        liveRank = ahead + 1;
        liveTotal = total;
        pointsBehind = ahead == 0 ? 0 : gap;
    }

    private String placeLine() {
        updateStanding();
        return liveTotal > 1 ? "You are #" + liveRank + " of " + liveTotal : "";
    }

    private Vector2 pointAt(float s) {
        s = Math.max(0f, Math.min(pathLength, s));
        int i = 1;
        while (i < cum.length - 1 && cum[i] < s) {
            i++;
        }
        float seg = cum[i] - cum[i - 1];
        float t = seg == 0f ? 0f : (s - cum[i - 1]) / seg;
        Point a = pts.get(i - 1);
        Point b = pts.get(i);
        return new Vector2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);
    }

    // ── Update ───────────────────────────────────────────

    @Override
    public void render(float delta) {
        update(Math.min(delta, 0.1f));
        draw();
    }

    private void update(float dt) {
        Vector2 avatarNow = pointAt(distance);
        rig.update(dt, avatarNow.x, avatarNow.y, phase == Phase.WALKING ? -10f : 0f);
        updateFx(dt, avatarNow);
        if (classTest && phase != Phase.RESULT && standings != null) {
            Live live = standings.latest();
            if (live != null && "completed".equals(live.status())) {
                timesUp = true; // the professor ended the test, or the time limit passed
                finishRun();
                return;
            }
        }
        switch (phase) {
            case WALKING -> {
                distance = Math.min(pathLength, distance + WALK_SPEED * dt);
                if (towerIdx < towers.size() && distance >= towers.get(towerIdx).s() - TRIGGER_AHEAD) {
                    startEncounter();
                } else if (towerIdx >= towers.size() && distance >= pathLength) {
                    finishRun();
                }
            }
            case ENCOUNTER -> {
                timeLeft -= dt;
                Vector2 pp = projectilePos();
                rig.guard(pp.x, pp.y, 1f - Math.max(0f, timeLeft) / flightTime);
                if (timeLeft <= 0f) {
                    resolve(null);
                }
            }
            case FEEDBACK -> {
                feedbackTimer -= dt;
                if (feedbackTimer <= 0f) {
                    if (run.isFailed()) {
                        finishRun();
                    } else {
                        phase = Phase.WALKING;
                    }
                }
            }
            case RESULT -> {
            }
        }
    }

    private void startEncounter() {
        Optional<Challenge> next = run.nextChallenge();
        if (next.isEmpty()) {
            towerIdx = towers.size();
            return;
        }
        active = next.get();
        List<String> shuffled = new ArrayList<>();
        shuffled.add(active.correctToken());
        shuffled.addAll(active.distractors());
        Collections.shuffle(shuffled, rng);
        options = shuffled;
        flightTime = StageRun.flightSeconds(run.difficulty());
        timeLeft = flightTime;
        hintShown = false;

        TowerSpot tower = towers.get(towerIdx);
        Vector2 avatar = pointAt(distance);
        panelTop = (tower.y() + avatar.y) / 2f < H / 2f ? 600f : 30f;
        phase = Phase.ENCOUNTER;
    }

    /** token == null means the player ran out of time. */
    private void resolve(String token) {
        Challenge answered = active;
        boolean hinted = hintShown;
        float fraction = flightTime > 0f ? Math.max(0f, timeLeft / flightTime) : 0f;
        int takenMs = Math.round((flightTime - Math.max(0f, timeLeft)) * 1000f);
        Vector2 projectileAt = projectilePos();
        TowerSpot sourceTower = towers.get(towerIdx);
        StageRun.Outcome o = run.answer(token, fraction);
        towerIdx++;
        active = null;
        startAnswerFx(o.correct(), projectileAt, sourceTower);

        answers.add(new RunReplay.Answer(token, fraction));
        RunCache.save(cacheKey, username, seed, answers);
        recordAnswer(answered, token, o, takenMs, hinted);

        StringBuilder sb = new StringBuilder();
        if (classTest) {
            if (o.correct()) {
                sb.append("Correct!  +").append(o.points());
                if (o.streak() >= 2) {
                    sb.append("     streak x").append(o.streak());
                }
                feedbackColor = new Color(0.55f, 0.95f, 0.55f, 1f);
            } else {
                sb.append(token == null ? "Too slow!" : "Wrong").append("  Answer: ").append(o.correctToken());
                feedbackColor = new Color(1f, 0.55f, 0.5f, 1f);
            }
        } else if (o.correct()) {
            sb.append("Deflected!  +").append(StageRun.POINTS_PER_CORRECT);
            feedbackColor = new Color(0.55f, 0.95f, 0.55f, 1f);
        } else {
            sb.append(token == null ? "Too slow!" : "Hit!")
                    .append("  Answer: ").append(o.correctToken())
                    .append("   -").append(o.damage()).append(" HP");
            feedbackColor = new Color(1f, 0.55f, 0.5f, 1f);
        }
        if (classTest) {
            String place = placeLine();
            if (!place.isEmpty()) {
                sb.append("\n").append(place);
                if (pointsBehind > 0) {
                    sb.append("   -   ").append(pointsBehind).append(" pts behind the next player");
                }
            }
        }
        SectionResult closed = o.sectionClosed();
        if (closed != null) {
            sb.append("\nSection ").append(closed.sectionIndex()).append(" done: ")
                    .append(Math.round(closed.accuracy() * 100)).append("%");
            if (!classTest && closed.nextDifficulty() != closed.playedAt()) {
                sb.append("   difficulty ").append(closed.playedAt()).append(" -> ").append(closed.nextDifficulty());
            }
        }
        feedback = sb.toString();
        feedbackTimer = closed != null ? 2.8f : 1.5f;
        phase = Phase.FEEDBACK;
    }

    /** Writes the answer to the answer log and, in a class test, the running score; in the background. */
    private void recordAnswer(Challenge answered, String token, StageRun.Outcome o, int takenMs, boolean hinted) {
        if (studentId < 0 || answered == null) {
            return;
        }
        int score = run.score();
        DatabaseManager.AnswerLog entry = new DatabaseManager.AnswerLog(studentId, lesson.stageId(),
                classTest ? testSession.id() : null, classTest ? "class_test" : "campaign", answered.id(),
                answered.topic(), answered.difficulty(), token, o.correct(), o.points(), takenMs, hinted ? 1 : 0);
        uploader.submit(() -> {
            db.logAnswer(entry);
            if (classTest) {
                tests.submitProgress(testSession.id(), studentId, score);
            }
        });
    }

    private void finishRun() {
        phase = Phase.RESULT;
        if (saved) {
            return;
        }
        saved = true;
        boolean pass = !classTest && !run.isFailed();
        saveDone = false;
        int score = run.score();
        String rank = run.rank().name();
        uploader.submit(() -> {
            try {
                if (classTest) {
                    if (studentId >= 0) {
                        tests.submitResult(testSession.id(), studentId, score);
                    }
                } else if (pass) {
                    db.saveLessonProgress(username, lesson.stageId(), startDifficulty.name().toLowerCase(), score, rank);
                    rollOutcome = db.rollReward(username, lesson.stageId());
                }
            } finally {
                saveDone = true;
            }
        });
        RunCache.clear(cacheKey, username); // the run is over: nothing left to resume
    }

    private void leave() {
        if (classTest && phase == Phase.RESULT) {
            game.setScreen(new ClassTestResultScreen(game, username, testSession, RunSummary.of(run)));
        } else if (classTest) {
            game.setScreen(new MainMenuScreen(game, username));
        } else {
            game.setScreen(afterRun());
        }
    }

    /** After a passed lesson the reward reveal comes next (it was rolled while the result was saved); otherwise the lesson list. */
    private com.badlogic.gdx.Screen afterRun() {
        DatabaseManager.RollOutcome r = rollOutcome;
        if (!classTest && phase == Phase.RESULT && r != null && r.status() != DatabaseManager.RollStatus.ALREADY_CLAIMED) {
            return new RewardRollScreen(game, username, r, lesson.title(), lesson.stageId());
        }
        return new CampaignScreen(game, username);
    }

    /** Dresses the student in the equipped hat, floating weapon and trail (placeholder colors until the art exists). */
    private void applyLook(PlayData data) {
        outfit = CosmeticLook.outfit(data.equipped(), data.catalog());
        DatabaseManager.Cosmetic w = outfit.weapon();
        rig = new WeaponRig(w == null ? 0 : w.rarityRank(), w == null ? new Color(0.82f, 0.86f, 1f, 1f) : CosmeticLook.colorOf(w),
                CosmeticLook.rarityColor(w == null ? "common" : w.rarity()));
    }

    private Vector2 projectilePos() {
        TowerSpot t = towers.get(Math.min(towerIdx, towers.size() - 1));
        Vector2 a = pointAt(distance);
        float p = flightTime > 0f ? Math.max(0f, Math.min(1f, 1f - timeLeft / flightTime)) : 1f;
        return new Vector2(t.x() + (a.x - t.x()) * p, t.y() + (a.y - t.y()) * p);
    }

    /** Starts the weapon's strike (right answer) or stumble (wrong answer) and the matching fate for the projectile. */
    private void startAnswerFx(boolean correct, Vector2 pp, TowerSpot source) {
        fxFromX = pp.x;
        fxFromY = pp.y;
        fxX = pp.x;
        fxY = pp.y;
        fxT = 0f;
        if (correct) {
            fx = Fx.FROZEN;
            fxToX = source.x();
            fxToY = source.y();
            int rank = rig == null ? 0 : outfit.weapon() == null ? 0 : outfit.weapon().rarityRank();
            Color color = rank >= 1 ? CosmeticLook.rarityColor(outfit.weapon().rarity()) : new Color(0.6f, 0.82f, 1f, 1f);
            rig.strike(pp.x, pp.y, () -> {
                rig.burst(pp.x, pp.y, color, 14 + 8 * rank, 260f + 60f * rank);
                fx = Fx.KNOCKBACK;
                fxT = 0f;
            });
        } else {
            fx = Fx.HIT;
            rig.stumble();
        }
    }

    private void updateFx(float dt, Vector2 avatar) {
        avatarFlash = Math.max(0f, avatarFlash - dt);
        switch (fx) {
            case KNOCKBACK -> {
                fxT += dt;
                float t = Math.min(1f, fxT / 0.4f);
                float e = com.badlogic.gdx.math.Interpolation.pow2Out.apply(t);
                fxX = fxFromX + (fxToX - fxFromX) * e;
                fxY = fxFromY + (fxToY - fxFromY) * e;
                if (t >= 1f) {
                    fx = Fx.NONE;
                }
            }
            case HIT -> {
                fxT += dt;
                float t = Math.min(1f, fxT / 0.25f);
                fxX = fxFromX + (avatar.x - fxFromX) * t;
                fxY = fxFromY + (avatar.y - fxFromY) * t;
                if (t >= 1f) {
                    rig.burst(avatar.x, avatar.y, new Color(1f, 0.35f, 0.3f, 1f), 12, 220f);
                    avatarFlash = 0.45f;
                    fx = Fx.NONE;
                }
            }
            default -> {
            }
        }
    }

    // ── Input ────────────────────────────────────────────

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        Vector2 world = viewport.unproject(new Vector2(screenX, screenY));
        float mx = world.x;
        float my = H - world.y;

        if (phase == Phase.RESULT) {
            if (!saveDone) {
                return true; // still saving the result: wait for it so the next screen shows it
            }
            if (continueButton().contains(mx, my)) {
                leave();
            } else if (certificateEligible() && CERT_BTN.contains(mx, my)) {
                game.setScreen(new CertificateScreen(game, username, lesson,
                        run.rank().name(), run.overallAccuracy(), () -> game.setScreen(afterRun())));
            }
            return true;
        }
        if (QUIT_BTN.contains(mx, my)) {
            leave();
            return true;
        }
        if (phase == Phase.ENCOUNTER) {
            if (!classTest && !hintShown && hintButton().contains(mx, my)) {
                hintShown = true;
                run.useHint();
                return true;
            }
            for (int i = 0; i < options.size(); i++) {
                if (tokenButton(i).contains(mx, my)) {
                    resolve(options.get(i));
                    return true;
                }
            }
        }
        return true;
    }

    private boolean certificateEligible() {
        return !classTest && !run.isFailed() && run.rank().passes();
    }

    private Rect continueButton() {
        return certificateEligible() ? CONTINUE_PAIRED : CONTINUE_ALONE;
    }

    private Rect hintButton() {
        return new Rect(MAP_WIDTH - 220, panelTop + 20, 140, 50);
    }

    private Rect tokenButton(int i) {
        float bw = 290f;
        float gap = 20f;
        float total = options.size() * bw + (options.size() - 1) * gap;
        float startX = (MAP_WIDTH - total) / 2f;
        return new Rect(startX + i * (bw + gap), panelTop + 335, bw, 80);
    }

    // ── Drawing ──────────────────────────────────────────

    private void draw() {
        ScreenUtils.clear(0.04f, 0.06f, 0.15f, 1f);
        viewport.apply();
        batch.setProjectionMatrix(camera.combined);
        sr.setProjectionMatrix(camera.combined);

        batch.begin();
        map.draw(batch);
        drawWorldSprites();
        batch.end();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        drawWorldShapes();
        drawSidebarShapes();
        if (phase == Phase.ENCOUNTER) {
            drawEncounterShapes();
        } else if (phase == Phase.FEEDBACK) {
            fill(60, 30, 1280, 150, new Color(0.04f, 0.06f, 0.15f, 0.92f));
        } else if (phase == Phase.RESULT) {
            fill(0, 0, W, H, new Color(0f, 0f, 0f, 0.82f));
            Rect cont = continueButton();
            fill(cont.x, cont.y, cont.w, cont.h, new Color(0.3f, 0.1f, 0.1f, 1f));
            if (certificateEligible()) {
                fill(CERT_BTN.x, CERT_BTN.y, CERT_BTN.w, CERT_BTN.h, new Color(0.12f, 0.3f, 0.12f, 1f));
            }
        }
        sr.end();

        batch.begin();
        drawSidebarText();
        if (phase == Phase.WALKING) {
            text("Section " + run.currentSectionNumber() + " / " + run.sectionCount(), 30, 20, 1.5f, Color.WHITE);
        } else if (phase == Phase.ENCOUNTER) {
            drawEncounterText();
        } else if (phase == Phase.FEEDBACK) {
            drawCentered(feedback, MAP_WIDTH / 2f, 50, 2.2f, feedbackColor);
        } else if (phase == Phase.RESULT) {
            drawResultText();
        }
        batch.end();
    }

    private void fill(float x, float yTop, float w, float h, Color c) {
        sr.setColor(c);
        sr.rect(x, H - yTop - h, w, h);
    }

    private void circle(float x, float yTop, float r, Color c) {
        sr.setColor(c);
        sr.circle(x, H - yTop, r, 32);
    }

    private static Color topicColor(CTopic t) {
        return switch (t) {
            case SYNTAX -> new Color(0.95f, 0.75f, 0.2f, 1f);
            case VARIABLES -> new Color(0.35f, 0.65f, 0.95f, 1f);
            case CONTROL_STRUCTURES -> new Color(0.7f, 0.5f, 0.95f, 1f);
            case FUNCTIONS -> new Color(0.3f, 0.8f, 0.7f, 1f);
            case ARRAYS -> new Color(0.5f, 0.85f, 0.4f, 1f);
            case POINTERS -> new Color(0.95f, 0.5f, 0.4f, 1f);
        };
    }

    private Texture sprite(String path) {
        return sprites.computeIfAbsent(path, p -> new Texture(Gdx.files.internal(p)));
    }

    private void drawProjectile(float x, float y, float size, float alpha, float rotation) {
        Texture tex = sprite("game/projectile.png");
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(tex, x - size / 2f, H - y - size / 2f, size / 2f, size / 2f, size, size, 1f, 1f, rotation, 0, 0,
                tex.getWidth(), tex.getHeight(), false, false);
        batch.setColor(Color.WHITE);
    }

    /** Towers, student (hat, trail), the projectile and the floating weapon (placeholder sprites; y is flipped from the top-left layout). */
    private void drawWorldSprites() {
        for (int i = 0; i < towers.size(); i++) {
            TowerSpot t = towers.get(i);
            Texture tex = sprite("game/towers/tower_" + t.topic().name().toLowerCase() + ".png");
            batch.setColor(i < towerIdx ? new Color(0.5f, 0.5f, 0.55f, 0.9f) : Color.WHITE);
            batch.draw(tex, t.x() - 42, H - t.y() - 60, 84, 105);
        }
        batch.setColor(Color.WHITE);

        Vector2 a = pointAt(distance);
        float bob = phase == Phase.WALKING ? (float) Math.sin(distance * 0.18f) * 2.5f : 0f;

        DatabaseManager.Cosmetic trail = outfit.trail();
        if (trail != null && !trail.isDefault()) {
            Color tc = CosmeticLook.colorOf(trail);
            int rank = trail.rarityRank();
            int dots = 5 + rank * 2;
            if (rank >= 2) {
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            }
            for (int i = 1; i <= dots; i++) {
                Vector2 back = pointAt(distance - i * 18f);
                batch.setColor(tc.r, tc.g, tc.b, Math.max(0.05f, 0.75f - i * (0.75f / (dots + 1))));
                float size = (26 + rank * 4) - i * 2.4f;
                batch.draw(sprite("game/glow.png"), back.x - size / 2, H - back.y - size / 2, size * 1.4f, size * 1.4f);
            }
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }

        if (avatarFlash > 0f) {
            float f = avatarFlash / 0.45f;
            batch.setColor(1f, 1f - 0.6f * f, 1f - 0.6f * f, 1f);
        }
        batch.draw(sprite("game/avatar.png"), a.x - 36, H - (a.y + bob) - 40, 72, 72);
        batch.setColor(Color.WHITE);

        DatabaseManager.Cosmetic hat = outfit.hat();
        if (hat != null && !hat.isDefault()) {
            Color hc = CosmeticLook.colorOf(hat);
            int rank = hat.rarityRank();
            float hatY = H - (a.y + bob - 24f);
            if (rank >= 1) {
                Color aura = CosmeticLook.rarityColor(hat.rarity());
                float size = 80f + 28f * rank;
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                batch.setColor(aura.r, aura.g, aura.b, 0.28f + 0.1f * rank);
                batch.draw(sprite("game/glow.png"), a.x - size / 2f, hatY + 20f - size / 2f, size, size);
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            }
            batch.setColor(hc);
            batch.draw(sprite("game/hat.png"), a.x - 29f, hatY, 58f, 44f);
            batch.setColor(Color.WHITE);
        }

        if (phase == Phase.ENCOUNTER) {
            Vector2 pp = projectilePos();
            drawProjectile(pp.x, pp.y, 60f, 1f, 0f);
        } else if (fx == Fx.FROZEN) {
            drawProjectile(fxX, fxY, 60f, 1f, 0f);
        } else if (fx == Fx.KNOCKBACK) {
            float t = Math.min(1f, fxT / 0.4f);
            drawProjectile(fxX, fxY, 60f * (1f - 0.5f * t), 1f - t, 720f * t);
        } else if (fx == Fx.HIT) {
            drawProjectile(fxX, fxY, 60f, 1f, 0f);
        }

        rig.draw(batch, H, sprite("game/weapon.png"), sprite("game/glow.png"), sprite("game/spark.png"),
                sprite("game/ring.png"));
    }

    private void drawWorldShapes() {
    }

    private void drawSidebarShapes() {
        fill(SIDEBAR_X, 0, W - SIDEBAR_X, H, new Color(0.05f, 0.07f, 0.17f, 0.96f));
        float ratio = classTest ? 0f : run.hp() / (float) StageRun.MAX_HP;
        if (!classTest) {
            fill(SIDEBAR_X + 20, 150, 480, 26, new Color(0.25f, 0.05f, 0.05f, 1f));
        }
        Color hpColor = ratio > 0.5f ? new Color(0.2f, 0.75f, 0.2f, 1f)
                : ratio > 0.25f ? new Color(0.85f, 0.7f, 0.1f, 1f) : new Color(0.8f, 0.15f, 0.15f, 1f);
        if (!classTest) {
            fill(SIDEBAR_X + 20, 150, 480 * ratio, 26, hpColor);
        }
        if (phase != Phase.RESULT) {
            fill(QUIT_BTN.x, QUIT_BTN.y, QUIT_BTN.w, QUIT_BTN.h, new Color(0.3f, 0.1f, 0.1f, 1f));
        }
    }

    private void drawEncounterShapes() {
        fill(60, panelTop, 1280, PANEL_H, new Color(0.05f, 0.07f, 0.17f, 0.95f));
        float ratio = Math.max(0f, timeLeft / flightTime);
        fill(60, panelTop, 1280 * ratio, 10, ratio > 0.4f ? new Color(0.3f, 0.8f, 0.3f, 1f)
                : new Color(0.9f, 0.25f, 0.2f, 1f));
        if (!hintShown && !classTest) {
            Rect h = hintButton();
            fill(h.x, h.y, h.w, h.h, new Color(0.35f, 0.28f, 0.08f, 1f));
        }
        for (int i = 0; i < options.size(); i++) {
            Rect b = tokenButton(i);
            fill(b.x, b.y, b.w, b.h, new Color(0.14f, 0.18f, 0.28f, 1f));
        }
    }

    private void drawSidebarText() {
        float x = SIDEBAR_X + 20;
        text(classTest ? "CLASS TEST" : "LESSON " + lesson.number(), x, 30, 2f, new Color(0.9f, 0.75f, 0.3f, 1f));
        text(lesson.title(), x, 62, 1.2f, new Color(0.75f, 0.8f, 0.95f, 1f));
        text("Section " + run.currentSectionNumber() + "/" + run.sectionCount()
                + "    " + run.difficulty(), x, 100, 1.4f, Color.LIGHT_GRAY);
        if (classTest) {
            updateStanding();
            text(liveTotal > 0 ? "Rank #" + liveRank + " of " + liveTotal : "Rank --", x, 140, 2.2f, GOLD_TEXT);
            text(pointsBehind > 0 ? pointsBehind + " pts behind the next player"
                    : liveRank == 1 && liveTotal > 1 ? "You are in the lead!" : "", x, 188, 1.3f, Color.LIGHT_GRAY);
            text("Points " + run.score(), x, 222, 1.7f, new Color(0.7f, 0.9f, 0.55f, 1f));
            text(run.streak() >= 2 ? "Streak x" + run.streak() : "", x, 256, 1.4f, new Color(1f, 0.6f, 0.2f, 1f));
        } else {
            text("HP " + run.hp() + "/" + StageRun.MAX_HP, x, 188, 1.3f, Color.WHITE);
            text("Score " + run.score(), x, 230, 1.5f, new Color(0.7f, 0.9f, 0.55f, 1f));
        }

        text("CODE BLOCK", x, 290, 1.4f, Color.LIGHT_GRAY);
        List<CodeBlock.Token> tokens = run.codeBlock().tokens();
        if (tokens.isEmpty()) {
            text("(empty)", x, 335, 1.3f, Color.GRAY);
        }
        int first = Math.max(0, tokens.size() - 16);
        for (int i = first; i < tokens.size(); i++) {
            CodeBlock.Token t = tokens.get(i);
            text((t.correct() ? "+ " : "x ") + t.text(), x, 335 + (i - first) * 36f, 1.5f,
                    t.correct() ? new Color(0.55f, 0.95f, 0.55f, 1f) : new Color(1f, 0.45f, 0.4f, 1f));
        }
        if (phase != Phase.RESULT) {
            drawCentered(classTest ? "Leave (you can resume)" : "Quit (progress is saved)", QUIT_BTN.x + QUIT_BTN.w / 2f,
                    QUIT_BTN.y + 18, 1.4f, Color.WHITE);
        }
    }

    private void drawEncounterText() {
        text("Tower: " + active.topic().name().replace('_', ' '), 90, panelTop + 25, 1.4f,
                topicColor(active.topic()));
        int lines = active.prompt().split("\n", -1).length;
        text(active.prompt(), 90, panelTop + 80, lines <= 5 ? 2.0f : 1.55f, Color.WHITE);
        if (classTest) {
            // no hints in a class test
        } else if (!hintShown) {
            Rect h = hintButton();
            drawCentered("Hint", h.x + h.w / 2f, h.y + 13, 1.7f, Color.WHITE);
        } else if (active.hint() != null) {
            text("Hint: " + active.hint(), 90, panelTop + 285, 1.5f, new Color(0.95f, 0.85f, 0.45f, 1f));
        }
        for (int i = 0; i < options.size(); i++) {
            Rect b = tokenButton(i);
            String tok = options.get(i);
            boolean tiny = tok.length() <= 2;
            drawCentered(tok, b.x + b.w / 2f, b.y + (tiny ? 10 : 20), tiny ? 3.6f : 2.6f, Color.WHITE);
        }
    }

    private void drawResultText() {
        boolean cleared = !run.isFailed();
        drawCentered(classTest ? (timesUp ? "TIME IS UP" : "TEST FINISHED") : cleared ? "LESSON COMPLETE" : "RUN FAILED", W / 2f, 130, 3f,
                cleared ? new Color(0.6f, 0.95f, 0.6f, 1f) : new Color(1f, 0.5f, 0.45f, 1f));
        drawCentered(classTest ? run.score() + " pts" : run.rank().name(), W / 2f, 230, classTest ? 6f : 8f,
                new Color(1f, 0.85f, 0.35f, 1f));
        drawCentered(classTest
                ? "Accuracy " + Math.round(run.overallAccuracy() * 100) + "%     Best streak " + run.bestStreak()
                : "Accuracy " + Math.round(run.overallAccuracy() * 100) + "%     Score " + run.score()
                        + "     HP " + run.hp(), W / 2f, 440, 1.8f, Color.WHITE);

        float y = 500;
        for (SectionResult r : run.sectionResults()) {
            drawCentered("Section " + r.sectionIndex() + ":  " + r.correct() + "/" + r.total() + "  ("
                    + Math.round(r.accuracy() * 100) + "%)   " + r.playedAt() + " -> " + r.nextDifficulty(),
                    W / 2f, y, 1.5f, Color.LIGHT_GRAY);
            y += 40;
        }
        if (classTest) {
            drawCentered(placeLine(), W / 2f, 780, 1.8f, new Color(0.6f, 0.95f, 0.6f, 1f));
        } else if (cleared && rollOutcome != null) {
            if (rollOutcome.status() == DatabaseManager.RollStatus.ALREADY_CLAIMED) {
                drawCentered("You already claimed this lesson's reward. Replays do not give another roll.", W / 2f, 735,
                        1.5f, new Color(0.7f, 0.75f, 0.9f, 1f));
            } else {
                drawCentered("You earned a reward roll!  Open it on the next screen.", W / 2f, 735, 1.6f,
                        new Color(1f, 0.85f, 0.4f, 1f));
            }
        }
        if (certificateEligible()) {
            drawCentered("Rank B or higher: you earned a lesson certificate", W / 2f, 780, 1.5f,
                    new Color(0.6f, 0.95f, 0.6f, 1f));
            drawCentered("Get certificate", CERT_BTN.x + CERT_BTN.w / 2f, CERT_BTN.y + 28, 1.8f, Color.WHITE);
        }
        Rect cont = continueButton();
        drawCentered(saveDone ? "Continue" : "Saving...", cont.x + cont.w / 2f, cont.y + 28, 1.8f, Color.WHITE);
    }

    private void text(String s, float x, float yTop, float scale, Color c) {
        font.setColor(c);
        font.getData().setScale(scale);
        font.draw(batch, s, x, H - yTop);
        font.getData().setScale(1f);
    }

    private void drawCentered(String s, float centerX, float yTop, float scale, Color c) {
        font.setColor(c);
        font.getData().setScale(scale);
        layout.setText(font, s);
        font.draw(batch, s, centerX - layout.width / 2f, H - yTop);
        font.getData().setScale(1f);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        batch.dispose();
        sr.dispose();
        font.dispose();
        sprites.values().forEach(Texture::dispose);
        map.dispose();
        if (standings != null) {
            standings.close();
        }
        if (uploader != null) {
            uploader.shutdown();
        }
    }
}
