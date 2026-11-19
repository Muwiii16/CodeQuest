package codequest.screens;

import codequest.Main;
import codequest.content.AnswerKind;
import codequest.content.BuiltInPrograms;
import codequest.content.CTopic;
import codequest.content.Challenge;
import codequest.content.ChallengeProvider;
import codequest.content.DefaultStages;
import codequest.content.Difficulty;
import codequest.content.FallbackChallengeProvider;
import codequest.content.KindRotation;
import codequest.content.PooledChallengeProvider;
import codequest.content.ProgramChallengeProvider;
import codequest.content.ProgramLayout;
import codequest.content.QuestionDraft;
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
import codequest.game.TowerPlacement;
import codequest.game.TowerSlots;
import codequest.screens.PixelFonts.Face;
import codequest.session.CodeBlock;
import codequest.session.SectionResult;
import codequest.session.Terminal;
import codequest.session.StageRun;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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
    /** Pixel-art sizes on the 1920 x 1080 stage: hero pictures are drawn at this fraction, towers and hats at these sizes. */
    private static final float HERO_SCALE = 0.8f;      // run frames
    private static final float HERO_IDLE_SCALE = 0.62f; // the standing pictures are taller, so they are drawn smaller
    private static final float TOWER_W = 78f;
    private static final float TOWER_H = 121f;
    /** How far a tower's picture must stay from the path's centre line: half a cell of path plus a little air. */
    private static final float TOWER_CLEARANCE = 43f;
    private static final float HAT_W = 74f;
    private static final float HAT_H = 62f;
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
    private final Map<String, Texture> sprites = new HashMap<>();
    private final GlyphLayout layout = new GlyphLayout();
    private final Random rng;

    private Phase phase = Phase.WALKING;
    private float distance = 0f;
    /** Seconds on screen, drives the hero's run and idle frames. */
    private float animTime;
    private String weaponPath = "game/weapons/wooden.png";
    private int towerIdx = 0;

    private Challenge active;
    private List<String> options = List.of();
    /** Arrange questions: which options (by position in {@link #options}) are in the student's line, in order. */
    private final List<Integer> placed = new ArrayList<>();
    /** Fill-in-the-blank questions: what the student has typed so far. */
    private final StringBuilder typed = new StringBuilder();
    /** Find the bug: the line the student has picked (0-based), or -1. Nothing counts until Check is pressed. */
    private int pickedLine = -1;
    private float mouseX;
    private float mouseY;
    private float timeLeft;
    private float flightTime;
    private boolean hintShown;
    private float panelTop;

    private String feedback = "";
    private Color feedbackColor = Color.WHITE;
    private float feedbackTimer;
    // after an answer: the explanation panel (what was asked, the right answer and why)
    private Challenge explainChallenge;
    private String explainToken;
    private boolean explainCorrect;
    private float explainTop;
    private float explainAge;
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

        // A lesson with a program (or with no questions at all, which plays a built-in program) is built block by block:
        // one tower per chunk, in program order. Any other lesson draws its questions at random as before.
        int layout = DefaultStages.layoutFor(lesson.number());
        List<Challenge> programSlots = programSlots(lesson, data.authored(), layout);
        Stage stage;
        ChallengeProvider provider;
        ProgramLayout programLayout = null;
        if (programSlots != null) {
            ProgramChallengeProvider byProgram = new ProgramChallengeProvider(programSlots);
            provider = byProgram;
            // the lesson's program text (several small programs are separated by ---) and what each prints
            String programText;
            String outputsText;
            if (data.authored().isEmpty()) {
                BuiltInPrograms.Program builtIn = BuiltInPrograms.forLayout(layout);
                programText = builtIn.text();
                outputsText = builtIn.outputs();
            } else {
                programText = lesson.program();
                outputsText = lesson.outputs();
            }
            List<String> chunks = new ArrayList<>();
            byProgram.slots().forEach(c -> chunks.add(c.codeChunk()));
            programLayout = ProgramLayout.of(programText, outputsText, chunks);
            stage = ProgramChallengeProvider.stageFor(lesson.title(), "map" + layout, byProgram.slots());
        } else {
            stage = DefaultStages.forNumber(layout);
            // D4 (campaign content) first; the built-in questions only cover what D4 does not have yet.
            // One rotation feeds both sources, so the lesson gets a mix of answer kinds; both are seeded, so a class test
            // gives everyone the same questions and a resumed lesson replays exactly.
            KindRotation rotation = new KindRotation();
            provider = new FallbackChallengeProvider(
                    new PooledChallengeProvider(data.authored(), seed, rotation), new StaticChallengeProvider(seed + 1, rotation));
        }
        this.towerCount = stage.towerCount();
        this.run = new StageRun(stage, startDifficulty, classTest ? StageRun.Mode.CLASS_TEST : StageRun.Mode.CAMPAIGN,
                provider, lesson.adaptive());
        if (programLayout != null) {
            run.useLayout(programLayout);
        }
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

        // Towers stand BESIDE the path, never on it, and never on top of another picture. The map picture reserves
        // empty slots for them (map<N>_slots.txt); each tower takes the slot that keeps the towers in path order and
        // spread along the route. Without a slot file (or with too few slots) the tower is placed by TowerPlacement,
        // which at least keeps it off the path.
        List<float[]> pathSamples = TowerPlacement.sample(pts, 8f);
        TowerPlacement.Box playArea = new TowerPlacement.Box(60f, map.getOffsetY() + 72f, map.getMapWidth() - 60f,
                map.getOffsetY() + map.getMapHeight() - 72f);
        TowerPlacement.Box wholeMap = new TowerPlacement.Box(20f, map.getOffsetY() + 10f, map.getMapWidth() - 20f,
                map.getOffsetY() + map.getMapHeight() - 10f);
        List<Float> wantedS = new ArrayList<>();
        List<CTopic> wantedTopics = new ArrayList<>();
        float secLen = (pathLength - LEAD_IN) / stage.sections().size();
        for (int k = 0; k < stage.sections().size(); k++) {
            Section section = stage.sections().get(k);
            int n = section.towerTopics().size();
            for (int j = 0; j < n; j++) {
                wantedS.add(LEAD_IN + k * secLen + (j + 1) * secLen / (n + 1));
                wantedTopics.add(section.towerTopics().get(j));
            }
        }
        List<TowerSlots.Slot> given = TowerSlots.assign(map.getTowerSlots(), wantedS);
        List<TowerPlacement.Box> takenSpots = new ArrayList<>();
        for (TowerSlots.Slot slot : given) {
            if (slot != null) {
                takenSpots.add(towerBox(slot.x(), slot.y()));
            }
        }
        for (int i = 0; i < wantedS.size(); i++) {
            float s = wantedS.get(i);
            TowerSlots.Slot slot = given.get(i);
            float[] spot = slot == null ? null : new float[] { slot.x(), slot.y() };
            if (spot == null) {
                Vector2 on = pointAt(s);
                Vector2 dir = pointAt(s + 5).sub(pointAt(s - 5)).nor();
                int side = i % 2 == 0 ? 1 : -1;
                spot = TowerPlacement.choose(pathSamples, on.x, on.y, dir.x, dir.y, side, TOWER_W / 2f, TOWER_H / 2f + 1f, 60f,
                        TOWER_CLEARANCE, playArea, takenSpots);
                if (spot == null) {
                    spot = TowerPlacement.choose(pathSamples, on.x, on.y, dir.x, dir.y, side, TOWER_W / 2f, TOWER_H / 2f + 1f, 60f,
                            TOWER_CLEARANCE, wholeMap, takenSpots);
                }
                if (spot == null) {
                    spot = new float[] { on.x - dir.y * 110f * side, on.y + dir.x * 110f * side };
                }
                takenSpots.add(towerBox(spot[0], spot[1]));
            }
            towers.add(new TowerSpot(s, spot[0], spot[1], wantedTopics.get(i)));
        }

        if (resume && savedRun.isPresent()) {
            resumeFrom(savedRun.get());
        } else {
            RunCache.clear(cacheKey, username);
        }
    }

    /**
     * The questions of a lesson that is built block by block, in program order, or null when the lesson draws its
     * questions at random. A lesson with a program and questions that all say which lines they add is built from its own
     * questions; a lesson with no questions at all plays the built-in program of its map.
     */
    private static List<Challenge> programSlots(Lesson lesson, List<Challenge> authored, int layout) {
        if (authored.isEmpty()) {
            return BuiltInPrograms.forLayout(layout).slots();
        }
        if (lesson.hasProgram() && authored.stream().allMatch(c -> c.codeChunk() != null)) {
            return authored;
        }
        return null;
    }

    /** The area a tower's picture covers when its spot is (x, y): the picture is drawn 60 px below the spot and reaches up from there. */
    private static TowerPlacement.Box towerBox(float x, float y) {
        return new TowerPlacement.Box(x - TOWER_W / 2f, y - TOWER_H / 2f - 1f, x + TOWER_W / 2f, y + 60f);
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
        animTime += dt;
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
                explainAge += dt;
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
        options = active.bank(rng);
        placed.clear();
        typed.setLength(0);
        pickedLine = -1;
        // typing and building take longer than tapping, so those kinds get more of the projectile's flight time
        flightTime = StageRun.flightSeconds(active, run.difficulty());
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
        explainChallenge = answered != null && !answered.explanationText().isEmpty() ? answered : null;
        if (explainChallenge != null) {
            explainToken = token;
            explainCorrect = o.correct();
            explainAge = 0f;
            // the explanation sits on the other side of the map from where the question was; a campaign lesson waits for
            // the student to press Continue, a class test moves on by itself after a while
            explainTop = panelTop < 300f ? 620f : 200f;
            feedbackTimer = classTest ? 9f : 3600f;
        }
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

    /** Dresses the student in the equipped hat, floating weapon and trail. The weapon and hat pictures keep their own colors. */
    private void applyLook(PlayData data) {
        outfit = CosmeticLook.outfit(data.equipped(), data.catalog());
        DatabaseManager.Cosmetic w = outfit.weapon();
        weaponPath = CosmeticLook.weaponSprite(w);
        rig = new WeaponRig(w == null ? 0 : w.rarityRank(), Color.WHITE,
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
        if (phase == Phase.FEEDBACK && explainChallenge != null && explainAge > 0.4f
                && explainContinueButton().contains(mx, my)) {
            feedbackTimer = 0f;
            return true;
        }
        if (phase == Phase.ENCOUNTER) {
            if (!classTest && !hintShown && hintButton().contains(mx, my)) {
                hintShown = true;
                run.useHint();
                return true;
            }
            tapEncounter(mx, my);
        }
        return true;
    }

    /** A tap inside the question panel, handled by the way this question is answered. */
    private void tapEncounter(float mx, float my) {
        switch (active.kind()) {
            case CHOICE, TRUE_FALSE -> {
                for (int i = 0; i < options.size(); i++) {
                    if (tokenButton(i).contains(mx, my)) {
                        resolve(options.get(i));
                        return;
                    }
                }
            }
            case FIND_BUG -> {
                if (bugCheckButton().contains(mx, my)) {
                    submitBugLine();
                    return;
                }
                for (int i = 0; i < options.size(); i++) {
                    if (lineRect(i).contains(mx, my)) {
                        pickedLine = i; // only picks it: a slip of the mouse cannot cost an answer
                        return;
                    }
                }
            }
            case FILL_BLANK -> {
                if (submitButton().contains(mx, my)) {
                    submitTyped();
                }
            }
            case ARRANGE -> {
                if (checkButton().contains(mx, my)) {
                    submitArranged();
                    return;
                }
                if (clearButton().contains(mx, my)) {
                    placed.clear();
                    return;
                }
                float scale = chipScale();
                List<Rect> built = chipRects(labels(placed), builtY(), scale);
                for (int i = 0; i < built.size(); i++) {
                    if (built.get(i).contains(mx, my)) {
                        placed.remove(i); // back to the bank
                        return;
                    }
                }
                List<Integer> left = remaining();
                List<Rect> bank = chipRects(labels(left), bankY(), scale);
                for (int i = 0; i < bank.size(); i++) {
                    if (bank.get(i).contains(mx, my)) {
                        placed.add(left.get(i));
                        return;
                    }
                }
            }
        }
    }

    private void submitBugLine() {
        if (pickedLine >= 0 && pickedLine < options.size()) {
            resolve(options.get(pickedLine));
        }
    }

    private void submitTyped() {
        String t = typed.toString().trim();
        if (!t.isEmpty()) {
            resolve(t);
        }
    }

    private void submitArranged() {
        if (!placed.isEmpty()) {
            resolve(String.join(" ", labels(placed)));
        }
    }

    @Override
    public boolean keyTyped(char c) {
        if (phase == Phase.ENCOUNTER && active != null && active.kind() == AnswerKind.FILL_BLANK
                && c >= 32 && c != 127 && typed.length() < QuestionDraft.MAX_TOKEN) {
            typed.append(c);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (phase == Phase.FEEDBACK && explainChallenge != null && explainAge > 0.4f
                && (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER || keycode == Input.Keys.SPACE)) {
            feedbackTimer = 0f;
            return true;
        }
        if (phase != Phase.ENCOUNTER || active == null) {
            return false;
        }
        if (active.kind() == AnswerKind.FIND_BUG) {
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                submitBugLine();
                return true;
            }
            if (keycode == Input.Keys.UP && !options.isEmpty()) {
                pickedLine = pickedLine <= 0 ? options.size() - 1 : pickedLine - 1;
                return true;
            }
            if (keycode == Input.Keys.DOWN && !options.isEmpty()) {
                pickedLine = (pickedLine + 1) % options.size();
                return true;
            }
            if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_8 && keycode - Input.Keys.NUM_1 < options.size()) {
                pickedLine = keycode - Input.Keys.NUM_1;
                return true;
            }
        }
        if (active.kind() == AnswerKind.FILL_BLANK) {
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                submitTyped();
                return true;
            }
            if (keycode == Input.Keys.BACKSPACE && typed.length() > 0) {
                typed.setLength(typed.length() - 1);
                return true;
            }
        } else if (active.kind() == AnswerKind.ARRANGE) {
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                submitArranged();
                return true;
            }
            if (keycode == Input.Keys.BACKSPACE && !placed.isEmpty()) {
                placed.remove(placed.size() - 1);
                return true;
            }
        }
        return false;
    }

    private boolean certificateEligible() {
        return !classTest && !run.isFailed() && run.rank().passes();
    }

    private Rect continueButton() {
        return certificateEligible() ? CONTINUE_PAIRED : CONTINUE_ALONE;
    }

    private Rect explainContinueButton() {
        return new Rect(1040, explainTop + 336, 260, 56);
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

    // find the bug: one tappable row per line of code
    private Rect lineRect(int i) {
        return new Rect(90, panelTop + 80 + i * 42f, 1040, 38);
    }

    private Rect bugCheckButton() {
        return new Rect(1150, panelTop + 80, 180, 84);
    }

    // fill in the blank: the box the answer is typed into, and its button
    private Rect answerBox() {
        return new Rect(90, panelTop + 335, 800, 80);
    }

    private Rect submitButton() {
        return new Rect(910, panelTop + 335, 300, 80);
    }

    // arrange: the student's line, the tokens still to place, and the two buttons under them
    private float builtY() {
        return panelTop + 215;
    }

    private float bankY() {
        return panelTop + 318;
    }

    private Rect checkButton() {
        return new Rect(1010, panelTop + 384, 250, 48);
    }

    private Rect clearButton() {
        return new Rect(90, panelTop + 384, 160, 48);
    }

    /** Options that are not in the student's line yet, in their original order. */
    private List<Integer> remaining() {
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            if (!placed.contains(i)) {
                left.add(i);
            }
        }
        return left;
    }

    private List<String> labels(List<Integer> indexes) {
        List<String> out = new ArrayList<>();
        for (int i : indexes) {
            out.add(options.get(i));
        }
        return out;
    }

    private static final float CHIP_GAP = 12f;
    private static final float CHIP_H = 60f;
    private static final float ROW_WIDTH = 1170f;

    private float chipWidth(String label, float scale) {
        BitmapFont font = PixelFonts.code(scale);
        layout.setText(font, label);
        return Math.max(70f, layout.width + 40f);
    }

    /** One size for every chip of the question, small enough that the whole bank fits on one row. */
    private float chipScale() {
        for (float scale : new float[] { 2.2f, 1.8f, 1.4f }) {
            float total = 0f;
            for (String o : options) {
                total += chipWidth(o, scale) + CHIP_GAP;
            }
            if (total - CHIP_GAP <= ROW_WIDTH) {
                return scale;
            }
        }
        return 1.1f;
    }

    /** Chips laid out left to right on a row starting at the panel's left edge. */
    private List<Rect> chipRects(List<String> chipLabels, float y, float scale) {
        List<Rect> out = new ArrayList<>();
        float x = 90f;
        for (String label : chipLabels) {
            float w = chipWidth(label, scale);
            out.add(new Rect(x, y, w, CHIP_H));
            x += w + CHIP_GAP;
        }
        return out;
    }

    private static Color shade(Color base, boolean lit) {
        return lit ? new Color(Math.min(1f, base.r + 0.1f), Math.min(1f, base.g + 0.1f), Math.min(1f, base.b + 0.1f), 1f)
                : base;
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
            if (explainChallenge != null) {
                fill(60, explainTop, 1280, 410, new Color(0.05f, 0.07f, 0.17f, 0.96f));
                fill(60, explainTop, 1280, 8, explainCorrect ? new Color(0.3f, 0.8f, 0.3f, 1f) : new Color(0.9f, 0.3f, 0.25f, 1f));
                Rect go = explainContinueButton();
                Color base = new Color(0.12f, 0.3f, 0.12f, 1f);
                fill(go.x, go.y, go.w, go.h, explainAge > 0.4f ? shade(base, go.contains(mouseX, mouseY)) : new Color(0.14f, 0.16f, 0.22f, 1f));
            }
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
            drawCentered(Face.SANS, feedback, MAP_WIDTH / 2f, 46, 2.0f, feedbackColor);
            if (explainChallenge != null) {
                drawExplanation();
            }
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

    /**
     * A sprite from assets/. Pixel art is drawn with the Nearest filter; the hero and the towers are shown smaller than
     * their pictures and the glow textures are soft, so those use Linear.
     */
    private Texture sprite(String path) {
        return sprites.computeIfAbsent(path, p -> {
            Texture t = new Texture(Gdx.files.internal(p));
            boolean smooth = p.startsWith("game/hero/") || p.startsWith("game/towers/") || p.equals("game/glow.png")
                    || p.equals("game/ring.png");
            Texture.TextureFilter filter = smooth ? Texture.TextureFilter.Linear : Texture.TextureFilter.Nearest;
            t.setFilter(filter, filter);
            return t;
        });
    }

    private void drawProjectile(float x, float y, float size, float alpha, float rotation) {
        drawProjectile(x, y, size, alpha, rotation, false);
    }

    private void drawProjectile(float x, float y, float size, float alpha, float rotation, boolean frozen) {
        Texture tex = sprite(frozen ? "game/projectile_frozen.png" : "game/projectile.png");
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
            batch.draw(tex, t.x() - TOWER_W / 2f, H - t.y() - 60, TOWER_W, TOWER_H);
        }
        batch.setColor(Color.WHITE);

        Vector2 a = pointAt(distance);
        float bob = phase == Phase.WALKING ? (float) Math.sin(distance * 0.18f) * 1.5f : 0f;

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
        // the hero: a run cycle facing the way he walks, or the idle loop while he stands
        String set = "idle";
        int frame = (int) (animTime * 4f) % 5 + 1;
        if (phase == Phase.WALKING) {
            Vector2 dir = pointAt(distance + 6f).sub(pointAt(Math.max(0f, distance - 6f)));
            if (dir.len2() < 0.01f) {
                dir.set(1f, 0f);
            }
            set = Math.abs(dir.x) >= Math.abs(dir.y) ? (dir.x >= 0f ? "run_right" : "run_left") : (dir.y < 0f ? "run_up" : "run_down");
            frame = (int) (animTime * 10f) % 5 + 1;
        }
        Texture hero = sprite(Art.heroFrame(set, frame));
        float heroScale = set.equals("idle") ? HERO_IDLE_SCALE : HERO_SCALE;
        float heroW = hero.getWidth() * heroScale;
        float heroH = hero.getHeight() * heroScale;
        float feetY = a.y + bob + 38f;
        batch.draw(hero, a.x - heroW / 2f, H - feetY, heroW, heroH);
        batch.setColor(Color.WHITE);

        DatabaseManager.Cosmetic hat = outfit.hat();
        if (hat != null && !hat.isDefault()) {
            int rank = hat.rarityRank();
            float headX = a.x + (set.equals("run_right") ? heroW * 0.06f : set.equals("run_left") ? -heroW * 0.06f : 0f);
            float seatY = feetY - heroH + 0.30f * heroW; // the hairline, where the hat's brim sits
            if (rank >= 1) {
                Color aura = CosmeticLook.rarityColor(hat.rarity());
                float size = 80f + 28f * rank;
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                batch.setColor(aura.r, aura.g, aura.b, 0.28f + 0.1f * rank);
                batch.draw(sprite("game/glow.png"), headX - size / 2f, H - (seatY - 18f) - size / 2f, size, size);
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            }
            batch.setColor(Color.WHITE);
            batch.draw(sprite(CosmeticLook.hatSprite(hat)), headX - HAT_W / 2f, H - (seatY - 0.88f * HAT_H + HAT_H), HAT_W, HAT_H);
        }

        if (phase == Phase.ENCOUNTER) {
            Vector2 pp = projectilePos();
            drawProjectile(pp.x, pp.y, 60f, 1f, 0f);
        } else if (fx == Fx.FROZEN) {
            drawProjectile(fxX, fxY, 60f, 1f, 0f, true);
        } else if (fx == Fx.KNOCKBACK) {
            float t = Math.min(1f, fxT / 0.4f);
            drawProjectile(fxX, fxY, 60f * (1f - 0.5f * t), 1f - t, 720f * t, true);
        } else if (fx == Fx.HIT) {
            drawProjectile(fxX, fxY, 60f, 1f, 0f);
        }

        rig.draw(batch, H, sprite(weaponPath), sprite("game/glow.png"), sprite("game/spark.png"),
                sprite("game/ring.png"));
    }

    private void drawWorldShapes() {
    }

    private void drawSidebarShapes() {
        fill(SIDEBAR_X, 0, W - SIDEBAR_X, H, new Color(0.05f, 0.07f, 0.17f, 0.96f));
        if (run.buildsProgram()) {
            fill(SIDEBAR_X + 10, 680, W - SIDEBAR_X - 20, 290, new Color(0.01f, 0.02f, 0.06f, 1f)); // the terminal
        }
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
        Vector2 m = viewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        mouseX = m.x;
        mouseY = H - m.y;
        Color idle = new Color(0.14f, 0.18f, 0.28f, 1f);
        switch (active.kind()) {
            case CHOICE, TRUE_FALSE -> {
                for (int i = 0; i < options.size(); i++) {
                    Rect b = tokenButton(i);
                    fill(b.x, b.y, b.w, b.h, shade(idle, b.contains(mouseX, mouseY)));
                }
            }
            case FIND_BUG -> {
                for (int i = 0; i < options.size(); i++) {
                    Rect r = lineRect(i);
                    boolean picked = i == pickedLine;
                    fill(r.x, r.y, r.w, r.h, picked ? new Color(0.5f, 0.31f, 0.08f, 1f)
                            : shade(new Color(0.09f, 0.12f, 0.21f, 1f), r.contains(mouseX, mouseY)));
                    fill(r.x, r.y, 50, r.h, picked ? new Color(0.8f, 0.5f, 0.12f, 1f) : new Color(0.16f, 0.2f, 0.32f, 1f));
                }
                Rect check = bugCheckButton();
                fill(check.x, check.y, check.w, check.h, pickedLine < 0 ? new Color(0.14f, 0.16f, 0.22f, 1f)
                        : shade(new Color(0.12f, 0.3f, 0.12f, 1f), check.contains(mouseX, mouseY)));
            }
            case FILL_BLANK -> {
                Rect box = answerBox();
                fill(box.x, box.y, box.w, box.h, new Color(0.02f, 0.03f, 0.09f, 1f));
                fill(box.x, box.y + box.h - 4, box.w, 4, new Color(0.45f, 0.6f, 0.9f, 1f));
                Rect go = submitButton();
                fill(go.x, go.y, go.w, go.h, shade(new Color(0.12f, 0.3f, 0.12f, 1f), go.contains(mouseX, mouseY)));
            }
            case ARRANGE -> {
                fill(90, builtY() - 8, ROW_WIDTH, CHIP_H + 16, new Color(0.02f, 0.03f, 0.09f, 1f));
                float scale = chipScale();
                for (Rect r : chipRects(labels(placed), builtY(), scale)) {
                    fill(r.x, r.y, r.w, r.h, shade(new Color(0.16f, 0.32f, 0.2f, 1f), r.contains(mouseX, mouseY)));
                }
                for (Rect r : chipRects(labels(remaining()), bankY(), scale)) {
                    fill(r.x, r.y, r.w, r.h, shade(idle, r.contains(mouseX, mouseY)));
                }
                Rect check = checkButton();
                fill(check.x, check.y, check.w, check.h,
                        shade(new Color(0.12f, 0.3f, 0.12f, 1f), check.contains(mouseX, mouseY)));
                Rect clear = clearButton();
                fill(clear.x, clear.y, clear.w, clear.h,
                        shade(new Color(0.3f, 0.1f, 0.1f, 1f), clear.contains(mouseX, mouseY)));
            }
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

        int blocks = run.layout() == null ? 1 : run.layout().blocks();
        text("CODE BLOCK", x, 290, 1.4f, Color.LIGHT_GRAY);
        if (run.buildsProgram() && blocks > 1) {
            text("Program " + (run.displayBlock() + 1) + " of " + blocks, x + 250, 292, 1.2f, GOLD_TEXT);
        }
        if (run.buildsProgram()) {
            // the program being built: every answered question added its lines, green if right and red if wrong
            List<CodeBlock.Line> lines = run.codeBlock().lines(run.displayBlock());
            if (lines.isEmpty()) {
                text("(empty)", x, 335, 1.3f, Color.GRAY);
            }
            int firstLine = Math.max(0, lines.size() - 14);
            for (int i = firstLine; i < lines.size(); i++) {
                CodeBlock.Line l = lines.get(i);
                float ly = 332 + (i - firstLine) * 24f;
                text(Face.MONO, String.valueOf(l.number()), x, ly, 1.0f, new Color(0.45f, 0.5f, 0.65f, 1f));
                text(Face.MONO, l.text(), x + 34, ly, 1.1f,
                        l.correct() ? new Color(0.55f, 0.95f, 0.55f, 1f) : new Color(1f, 0.45f, 0.4f, 1f));
            }
            drawTerminal(x, 690, 965);
        } else {
            List<CodeBlock.Token> tokens = run.codeBlock().tokens();
            if (tokens.isEmpty()) {
                text("(empty)", x, 335, 1.3f, Color.GRAY);
            }
            int first = Math.max(0, tokens.size() - 16);
            for (int i = first; i < tokens.size(); i++) {
                CodeBlock.Token t = tokens.get(i);
                text(Face.MONO, (t.correct() ? "+ " : "x ") + t.text(), x, 335 + (i - first) * 36f, 1.45f,
                        t.correct() ? new Color(0.55f, 0.95f, 0.55f, 1f) : new Color(1f, 0.45f, 0.4f, 1f));
            }
        }
        if (phase != Phase.RESULT) {
            drawCentered(classTest ? "Leave (you can resume)" : "Quit (progress is saved)", QUIT_BTN.x + QUIT_BTN.w / 2f,
                    QUIT_BTN.y + 18, 1.4f, Color.WHITE);
        }
    }

    /** The terminal under the code block: what the finished program prints, or the compiler's messages for the mistakes in it. */
    private void drawTerminal(float x, float top, float bottom) {
        text("TERMINAL", x, top, 1.4f, Color.LIGHT_GRAY);
        List<Terminal.Row> rows = run.terminal();
        float width = 470f;
        if (rows.isEmpty()) {
            drawWrapped(Face.SANS, "Finish a program to run it. You will see what it prints here, or the compiler's error "
                    + "if a wrong answer broke the code.", x, top + 40, 1.1f, new Color(0.55f, 0.6f, 0.78f, 1f), width);
            return;
        }
        // keep the end of the terminal (the output) when there is more than fits
        List<Float> heights = new ArrayList<>();
        List<GlyphLayout> layouts = new ArrayList<>();
        for (Terminal.Row r : rows) {
            BitmapFont f = font(r.kind() == Terminal.Kind.NOTE ? Face.SANS : Face.MONO, r.kind() == Terminal.Kind.NOTE ? 1.0f : 1.0f);
            GlyphLayout gl = new GlyphLayout(f, r.text(), Color.WHITE, width, com.badlogic.gdx.utils.Align.left, true);
            layouts.add(gl);
            heights.add(gl.height + 6f);
        }
        float total = 0f;
        for (float h : heights) {
            total += h;
        }
        int from = 0;
        float avail = bottom - (top + 36);
        while (total > avail && from < rows.size() - 1) {
            total -= heights.get(from++);
        }
        float y = top + 36;
        for (int i = from; i < rows.size(); i++) {
            Terminal.Row r = rows.get(i);
            Color c = switch (r.kind()) {
                case COMMAND -> new Color(0.6f, 0.8f, 1f, 1f);
                case ERROR -> new Color(1f, 0.45f, 0.4f, 1f);
                case WARNING -> new Color(1f, 0.85f, 0.4f, 1f);
                case CODE -> new Color(0.8f, 0.82f, 0.9f, 1f);
                case OUTPUT -> Color.WHITE;
                case NOTE -> new Color(0.6f, 0.66f, 0.85f, 1f);
            };
            BitmapFont f = font(r.kind() == Terminal.Kind.NOTE ? Face.SANS : Face.MONO, 1.0f);
            f.setColor(c);
            f.draw(batch, r.text(), x, H - y, width, com.badlogic.gdx.utils.Align.left, true);
            y += heights.get(i);
        }
    }

    private void drawEncounterText() {
        text("Tower: " + active.topic().name().replace('_', ' '), 90, panelTop + 25, 1.4f,
                topicColor(active.topic()));
        AnswerKind kind = active.kind();
        Color muted = new Color(0.75f, 0.8f, 0.95f, 1f);
        if (kind == AnswerKind.FIND_BUG) {
            // unmistakable: this is not a multiple choice, one of the lines has a mistake
            text("FIND THE BUG", 520, panelTop + 18, 1.7f, new Color(1f, 0.6f, 0.2f, 1f));
            text(Face.SANS, "One line has a mistake. Tap it, then Check.", 735, panelTop + 22, 1.2f,
                    new Color(1f, 0.82f, 0.55f, 1f));
        } else {
            text(Face.SANS, kind.label() + "  -  " + kind.instruction(), 520, panelTop + 22, 1.2f, muted);
        }
        int lines = active.prompt().split("\n", -1).length;
        if (active.context() != null) {
            // what the code is for, so a blank like  if (age ___ 18)  has exactly one right answer
            text(Face.SANS, "Goal: " + shorten(active.context(), 105), 90, panelTop + 46, 1.2f, new Color(0.65f, 0.9f, 1f, 1f));
        }
        if (kind != AnswerKind.FIND_BUG) {
            drawWrapped(faceFor(active), active.prompt(), 90, panelTop + 76, lines <= 5 ? 1.9f : 1.5f, Color.WHITE, 1060f);
        }
        Color gold = new Color(0.95f, 0.85f, 0.45f, 1f);
        if (classTest) {
            // no hints in a class test
        } else if (!hintShown) {
            Rect h = hintButton();
            drawCentered("Hint", h.x + h.w / 2f, h.y + 13, 1.7f, Color.WHITE);
        } else if (active.hint() != null) {
            if (kind == AnswerKind.FIND_BUG || kind == AnswerKind.ARRANGE) {
                // their rows fill the lower panel: arrange has room under its sentence, find the bug under its code lines
                float hintY = kind == AnswerKind.ARRANGE ? panelTop + 146 : panelTop + 80 + lines * 42f + 10f;
                text(Face.SANS, "Hint: " + shorten(active.hint(), 100), 90, hintY, 1.15f, gold);
            } else {
                drawWrapped(Face.SANS, "Hint: " + active.hint(), 90, panelTop + 285, 1.4f, gold, 1160f);
            }
        }
        switch (kind) {
            case CHOICE -> {
                for (int i = 0; i < options.size(); i++) {
                    Rect b = tokenButton(i);
                    String tok = options.get(i);
                    boolean tiny = tok.length() <= 2;
                    drawCentered(Face.MONO, tok, b.x + b.w / 2f, b.y + (tiny ? 14 : 24), tiny ? 3.0f : 2.2f, Color.WHITE);
                }
            }
            case TRUE_FALSE -> {
                for (int i = 0; i < options.size(); i++) {
                    Rect b = tokenButton(i);
                    drawCentered(Face.SANS, options.get(i), b.x + b.w / 2f, b.y + 22, 2.4f, Color.WHITE);
                }
            }
            case FIND_BUG -> {
                for (int i = 0; i < options.size(); i++) {
                    Rect r = lineRect(i);
                    drawCentered(Face.MONO, String.valueOf(i + 1), r.x + 25, r.y + 6, 1.45f, new Color(0.65f, 0.72f, 0.9f, 1f));
                    text(Face.MONO, active.codeLines().get(i), r.x + 66, r.y + 6, 1.55f, Color.WHITE);
                }
                Rect check = bugCheckButton();
                drawCentered(Face.SANS, pickedLine < 0 ? "Pick a line" : "Check line " + (pickedLine + 1),
                        check.x + check.w / 2f, check.y + 18, 1.5f, pickedLine < 0 ? Color.GRAY : Color.WHITE);
                drawCentered(Face.SANS, pickedLine < 0 ? "tap the buggy one" : "or press Enter", check.x + check.w / 2f,
                        check.y + 50, 1.05f, pickedLine < 0 ? Color.DARK_GRAY : new Color(0.75f, 0.95f, 0.75f, 1f));
            }
            case FILL_BLANK -> {
                Rect box = answerBox();
                boolean caret = ((int) (animTime * 2f)) % 2 == 0;
                if (typed.length() == 0) {
                    text(Face.MONO, caret ? "|" : " ", box.x + 24, box.y + 22, 2.2f, Color.WHITE);
                    text(Face.SANS, "   type your answer", box.x + 24, box.y + 26, 1.7f, new Color(0.5f, 0.55f, 0.7f, 1f));
                } else {
                    text(Face.MONO, typed + (caret ? "|" : ""), box.x + 24, box.y + 22, 2.2f, Color.WHITE);
                }
                Rect go = submitButton();
                drawCentered(Face.SANS, "Answer  (Enter)", go.x + go.w / 2f, go.y + 26, 1.8f, Color.WHITE);
                if (run.difficulty() == Difficulty.EASY && !active.correctToken().isEmpty()) {
                    // an easy typed answer shows its shape: how it starts and how long it is
                    String shape = "Starts with \"" + active.correctToken().charAt(0) + "\"  -  " + active.correctToken().length()
                            + (active.correctToken().length() == 1 ? " character" : " characters");
                    text(Face.SANS, shape, box.x, box.y - 26, 1.15f, new Color(0.65f, 0.9f, 1f, 1f));
                }
            }
            case ARRANGE -> {
                float scale = chipScale();
                text(Face.SANS, "Your line  (tap a token to take it back)", 90, panelTop + 178, 1.1f,
                        new Color(0.65f, 0.72f, 0.9f, 1f));
                text(Face.SANS, "Tokens", 90, panelTop + 290, 1.1f, new Color(0.65f, 0.72f, 0.9f, 1f));
                drawChips(chipRects(labels(placed), builtY(), scale), labels(placed), scale);
                List<String> left = labels(remaining());
                drawChips(chipRects(left, bankY(), scale), left, scale);
                Rect check = checkButton();
                drawCentered(Face.SANS, "Check  (Enter)", check.x + check.w / 2f, check.y + 11, 1.5f, Color.WHITE);
                Rect clear = clearButton();
                drawCentered(Face.SANS, "Clear", clear.x + clear.w / 2f, clear.y + 11, 1.5f, Color.WHITE);
            }
        }
    }

    private void drawChips(List<Rect> rects, List<String> chipLabels, float scale) {
        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);
            drawCentered(Face.MONO, chipLabels.get(i), r.x + r.w / 2f, r.y + (CHIP_H - PixelFonts.BASE * scale) / 2f - 2f,
                    scale, Color.WHITE);
        }
    }

    /** After an answer: the verdict, the right answer, what the student answered, and why. */
    private void drawExplanation() {
        Challenge c = explainChallenge;
        float y = explainTop;
        Vector2 m = viewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        mouseX = m.x;
        mouseY = H - m.y;
        boolean timedOut = explainToken == null;
        Color verdict = explainCorrect ? new Color(0.55f, 0.95f, 0.55f, 1f) : new Color(1f, 0.55f, 0.5f, 1f);
        text(Face.SANS, explainCorrect ? "Correct!" : timedOut ? "Time ran out" : "Not quite", 90, y + 22, 2.1f, verdict);

        String right = switch (c.kind()) {
            case FIND_BUG -> {
                int n = 0;
                try {
                    n = Integer.parseInt(c.correctToken().trim());
                } catch (NumberFormatException ignored) {
                    // keep 0
                }
                yield n >= 1 && n <= c.codeLines().size() ? "line " + n + ":   " + c.codeLines().get(n - 1).trim() : "line " + c.correctToken();
            }
            case ARRANGE -> c.codeChunk() != null ? c.codeChunk() : c.correctToken();
            default -> c.correctToken();
        };
        text(Face.SANS, "Right answer", 90, y + 76, 1.15f, new Color(0.65f, 0.72f, 0.9f, 1f));
        text(c.kind() == AnswerKind.TRUE_FALSE ? Face.SANS : Face.MONO, right, 90, y + 98, 1.8f, Color.WHITE);

        float next = y + 150;
        if (!explainCorrect && !timedOut) {
            String yours = c.kind() == AnswerKind.FIND_BUG ? "line " + explainToken : explainToken;
            text(Face.SANS, "You answered", 90, next, 1.15f, new Color(0.65f, 0.72f, 0.9f, 1f));
            text(c.kind() == AnswerKind.TRUE_FALSE ? Face.SANS : Face.MONO, shorten(yours, 70), 90, next + 22, 1.6f, verdict);
            next += 62;
        }
        text(Face.SANS, "Why", 90, next, 1.15f, GOLD_TEXT);
        drawWrapped(Face.SANS, c.explanationText(), 90, next + 24, 1.5f, Color.WHITE, 1160f);

        Rect go = explainContinueButton();
        drawCentered(Face.SANS, explainAge > 0.4f ? "Continue  (Enter)" : "...", go.x + go.w / 2f, go.y + 15, 1.6f,
                explainAge > 0.4f ? Color.WHITE : Color.GRAY);
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    /** Text that wraps at the given width (explicit new lines still break). */
    private void drawWrapped(Face face, String s, float x, float yTop, float scale, Color c, float width) {
        BitmapFont font = font(face, scale);
        font.setColor(c);
        font.draw(batch, s, x, H - yTop, width, com.badlogic.gdx.utils.Align.left, true);
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

    private static BitmapFont font(Face face, float scale) {
        return PixelFonts.font(face, scale);
    }

    private void text(String s, float x, float yTop, float scale, Color c) {
        text(Face.PIXEL, s, x, yTop, scale, c);
    }

    private void text(Face face, String s, float x, float yTop, float scale, Color c) {
        BitmapFont font = font(face, scale);
        font.setColor(c);
        font.draw(batch, s, x, H - yTop);
    }

    private void drawCentered(String s, float centerX, float yTop, float scale, Color c) {
        drawCentered(Face.PIXEL, s, centerX, yTop, scale, c);
    }

    private void drawCentered(Face face, String s, float centerX, float yTop, float scale, Color c) {
        BitmapFont font = font(face, scale);
        font.setColor(c);
        layout.setText(font, s);
        font.draw(batch, s, centerX - layout.width / 2f, H - yTop);
    }

    /** Code reads best in the monospace face; sentences in the sans face. */
    private static Face faceFor(Challenge c) {
        return PixelFonts.faceForPrompt(c.kind(), c.prompt());
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
