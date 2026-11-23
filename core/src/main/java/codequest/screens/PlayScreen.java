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
import codequest.data.AiFeedback;
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
    /** The question panel is laid out at full size (PANEL_H high, 1280 wide) and drawn smaller, so the map stays in view. */
    private static final float PANEL_SCALE = 0.78f;

    private static final Rect QUIT_BTN = new Rect(SIDEBAR_X + 20, 980, 480, 60);
    private static final Color GOLD_TEXT = new Color(1f, 0.82f, 0.3f, 1f);
    private static final float RESULT_BTN_W = 400f;
    private static final float RESULT_BTN_GAP = 40f;

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
    /** The subject this lesson belongs to once the run was saved: it holds the certificate when all its lessons are completed. */
    private volatile codequest.data.AwardsData.Subject subjectDone;
    /** The language this lesson is taught in (its class): the AI feedback follows it. */
    private codequest.content.Language language = codequest.content.Language.C;

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
    // what the student answered this run, for the written feedback they can ask for on the result screen (no names)
    private final List<AiFeedback.Item> feedbackItems = new ArrayList<>();
    private volatile String aiText;
    private volatile String aiError;
    private volatile boolean aiLoading;
    private boolean aiOpen;

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
    /** Looks at the map only (the panels and the sidebar keep {@link #camera}): it zooms in and shakes during a question. */
    private final OrthographicCamera worldCamera = new OrthographicCamera(W, H);
    private float camZoom = 1f;
    private float camX = W / 2f;
    /** The middle of the world view, measured from the top of the map. */
    private float camTop = H / 2f;
    private float shakeAmp;
    private float shakeKick;
    private float shakeClock;
    /** The anime speed lines that rush toward the middle of the action: 0 = none, they fade in slowly as the timer runs down. */
    private float lineFade;
    /** 0 = the sidebar is in place, 1 = it has slid off to the right (during a question). */
    private float sidebarSlide;
    private float lineClock;
    /** The middle between the projectile and the hero, in map coordinates measured from the top. */
    private float focusMapX = MAP_WIDTH / 2f;
    private float focusMapTop = H / 2f;
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer sr = new ShapeRenderer();
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
    /** The verdict as a pop-up over the hero: it rises and fades about two seconds after an answer. */
    private float popAge = 99f;
    private String popText = "";
    private Color feedbackColor = Color.WHITE;
    private float feedbackTimer;
    // after an answer: the explanation panel (what was asked, the right answer and why)
    private Challenge explainChallenge;
    private String explainToken;
    private boolean explainCorrect;
    private float explainTop;
    /** The explanation panel sits at the top of the map when the hero is low on the screen, else at the bottom. */
    private boolean explainOnTop;
    private static final float EXPLAIN_SCALE = 0.72f;
    private float explainAge;
    private boolean saved;

    /** Everything the screen needs from the database, fetched beforehand (see {@link #open}) so starting never freezes. */
    public record PlayData(List<Challenge> authored, long studentId, Map<String, Long> equipped,
            List<DatabaseManager.Cosmetic> catalog, codequest.content.Language language) {

        static PlayData fetch(DatabaseManager db, String username, Lesson lesson) {
            CompletableFuture<List<Challenge>> questions = CompletableFuture
                    .supplyAsync(() -> db.getStageChallenges(lesson.stageId()));
            CompletableFuture<Object[]> student = CompletableFuture.supplyAsync(() -> {
                long id = db.profileId(username);
                return new Object[] { id, id < 0 ? Map.<String, Long>of() : db.getEquippedCosmetics(id) };
            });
            CompletableFuture<List<DatabaseManager.Cosmetic>> catalog = CompletableFuture.supplyAsync(db::getCatalog);
            // the language of the lesson's class: the built-in lessons and starter questions follow it
            CompletableFuture<codequest.content.Language> language = CompletableFuture
                    .supplyAsync(() -> new codequest.data.CampaignData().languageOfCampaign(lesson.campaignId()));
            Object[] s = student.join();
            @SuppressWarnings("unchecked")
            Map<String, Long> equipped = (Map<String, Long>) s[1];
            return new PlayData(questions.join(), (Long) s[0], equipped, catalog.join(), language.join());
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
                    : new codequest.data.CampaignData().lesson(testSession.stageId()).orElseThrow(() -> new LoadingScreen.Failure(
                            "The lesson for this test could not be found. Ask your professor to check that it still exists."));
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
        this.language = data.language();
        int layout = DefaultStages.layoutFor(lesson.number());
        List<Challenge> programSlots = programSlots(lesson, data.authored(), layout, data.language());
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
                BuiltInPrograms.Program builtIn = BuiltInPrograms.forLayout(layout, data.language());
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
                    new PooledChallengeProvider(data.authored(), seed, rotation),
                    new StaticChallengeProvider(data.language(), seed + 1, rotation));
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
            }, Poller.CLASS_TEST_PERIOD_MS);
        } else {
            this.standings = null;
        }

        this.map = new GameMap(DefaultStages.layoutFor(lesson.number()), (int) MAP_WIDTH, lesson.theme());
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
    private static List<Challenge> programSlots(Lesson lesson, List<Challenge> authored, int layout,
            codequest.content.Language language) {
        if (authored.isEmpty()) {
            return BuiltInPrograms.forLayout(layout, language).slots();
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
        updateCamera(Math.min(delta, 0.1f));
        draw();
    }

    /**
     * While a question is open the map zooms in on the projectile and the hero together and shakes a little, more and more as
     * the projectile gets closer, so the timer feels like it is running out. It eases back out when the question ends.
     */
    private void updateCamera(float dt) {
        boolean tense = phase == Phase.ENCOUNTER && active != null && flightTime > 0f;
        float targetZoom = 1f;
        float targetX = W / 2f;
        float targetTop = H / 2f;
        float targetAmp = 0f;
        float progress = 0f;
        if (tense) {
            progress = Math.max(0f, Math.min(1f, 1f - timeLeft / flightTime));
            Vector2 shot = projectilePos();
            Vector2 hero = pointAt(distance);
            // the hero sits in the middle of the free part of the screen (the question panel takes the rest)
            boolean panelBelow = panelTop > H / 2f;
            float bandTop = panelBelow ? 0f : 96f + PANEL_H * PANEL_SCALE + 20f;
            float bandBottom = panelBelow ? H - 30f - PANEL_H * PANEL_SCALE - 20f : H;
            // zoom just enough to keep the projectile in view; it gets closer, so the zoom tightens
            float needX = (Math.abs(shot.x - hero.x) + 170f) / (W / 2f);
            float needY = (Math.abs(shot.y - hero.y) + 170f) / ((bandBottom - bandTop) / 2f);
            targetZoom = Math.max(0.4f, Math.min(0.62f, Math.max(needX, needY)));
            targetX = hero.x;
            targetTop = hero.y - ((bandTop + bandBottom) / 2f - H / 2f) * targetZoom;
            targetAmp = 1.2f + 11f * progress * progress;
            focusMapX = hero.x;
            focusMapTop = hero.y;
        }
        // the speed lines: nothing at first, then slowly stronger; they leave faster than they came
        float lineTarget = tense ? (float) Math.pow(Math.max(0f, Math.min(1f, (progress - 0.02f) / 0.6f)), 1.2) * 0.85f : 0f;
        lineFade += (lineTarget - lineFade) * (1f - (float) Math.exp(-(lineTarget > lineFade ? 2.2f : 8f) * dt));
        lineClock += dt;
        float k = 1f - (float) Math.exp(-(tense ? 5f : 9f) * dt);
        // the sidebar slides out to the right while a question is open and back in after the answer
        sidebarSlide += ((tense ? 1f : 0f) - sidebarSlide) * (1f - (float) Math.exp(-(tense ? 6f : 7f) * dt));
        camZoom += (targetZoom - camZoom) * k;
        camX += (targetX - camX) * k;
        camTop += (targetTop - camTop) * k;
        if (targetAmp > shakeAmp) {
            shakeAmp += (targetAmp - shakeAmp) * k;
        } else {
            shakeAmp *= (float) Math.exp(-8f * dt);
        }
        shakeKick *= (float) Math.exp(-7f * dt);
        shakeClock += dt * (28f + 22f * progress);
        float power = shakeAmp + shakeKick;
        float ox = (float) (Math.sin(shakeClock) * 0.6 + Math.sin(shakeClock * 2.3 + 1.7) * 0.4) * power;
        float oy = (float) (Math.cos(shakeClock * 1.3 + 0.4) * 0.6 + Math.sin(shakeClock * 2.9) * 0.4) * power * 0.8f;
        worldCamera.zoom = camZoom;
        worldCamera.position.set(camX + ox, H - camTop - oy, 0f);
        worldCamera.update();
    }

    private void update(float dt) {
        animTime += dt;
        popAge += dt;
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
        if (answered != null && !classTest) {
            String question = answered.prompt();
            String given = token == null ? "" : token;
            if (answered.kind() == AnswerKind.FIND_BUG) {
                // the AI must see the same line numbers the student saw
                List<String> lines = answered.codeLines();
                StringBuilder numbered = new StringBuilder("Find the line with the bug:");
                for (int i = 0; i < lines.size(); i++) {
                    numbered.append('\n').append(i + 1).append(": ").append(lines.get(i));
                }
                question = numbered.toString();
                given = token == null ? "" : "line " + token;
            }
            feedbackItems.add(new AiFeedback.Item(answered.topic().name(), question, given, answered.answerLabel(), o.correct()));
        }
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
        // the pop-up over the hero: a right answer keeps its text; a wrong one shows only the damage
        popText = o.correct() ? feedback
                : classTest ? (token == null ? "Too slow!" : "Wrong") : "-" + o.damage() + " HP";
        popAge = 0f;
        feedbackTimer = closed != null ? 2.8f : 1.5f;
        explainChallenge = answered != null && !answered.explanationText().isEmpty() ? answered : null;
        if (explainChallenge != null) {
            explainToken = token;
            explainCorrect = o.correct();
            explainAge = 0f;
            // the explanation sits on the other side of the map from where the question was; a campaign lesson waits for
            // the student to press Continue, a class test moves on by itself after a while
            explainTop = 200f; // its own layout; it is drawn smaller and away from the hero, see explainMatrix()
            explainOnTop = pointAt(distance).y > H / 2f;
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
                    db.saveLessonProgress(username, lesson.stageId(), startDifficulty.name().toLowerCase(), score, rank,
                            codequest.session.Grades.maxScore(towerCount));
                    rollOutcome = db.rollReward(username, lesson.stageId());
                    // the certificate comes only when every lesson of the subject is completed (the server checks it)
                    subjectDone = new codequest.data.AwardsData().subjectOfLesson(username, lesson).orElse(null);
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
        if (!correct) {
            shakeKick = 14f; // the hit lands: one hard shake that dies away
        }
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
            if (aiOpen) {
                if (aiCloseButton().contains(mx, my)) {
                    aiOpen = false;
                }
                return true;
            }
            if (continueButton().contains(mx, my)) {
                leave();
            } else if (feedbackAvailable() && feedbackButton().contains(mx, my)) {
                openFeedback();
            } else if (certificateEligible() && certButton().contains(mx, my)) {
                // finishing the whole subject gives the grand certificate; otherwise this lesson finished its period
                codequest.data.Period which = subjectDone.certificate() != null ? null : lesson.periodEnum();
                game.setScreen(new CertificateScreen(game, username, subjectDone, which, () -> game.setScreen(afterRun()), "Continue"));
            }
            return true;
        }
        if (sidebarSlide < 0.5f && QUIT_BTN.contains(mx, my)) {
            leave();
            return true;
        }
        float[] inExplain = toExplain(mx, my);
        if (phase == Phase.FEEDBACK && explainChallenge != null && explainAge > 0.4f
                && explainContinueButton().contains(inExplain[0], inExplain[1])) {
            feedbackTimer = 0f;
            return true;
        }
        if (phase == Phase.ENCOUNTER) {
            float[] inPanel = toPanel(mx, my); // the panel is drawn smaller than it is laid out
            if (!classTest && !hintShown && hintButton().contains(inPanel[0], inPanel[1])) {
                hintShown = true;
                run.useHint();
                return true;
            }
            tapEncounter(inPanel[0], inPanel[1]);
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

    /** A certificate is only for finishing the whole subject: true when this run completed the last unfinished lesson of it. */
    private boolean certificateEligible() {
        return !classTest && !run.isFailed() && subjectDone != null
                && (subjectDone.certificate() != null || periodAwardOfLesson() != null);
    }

    /** The certificate of this lesson's grading period, when every lesson of that period is completed (else null). */
    private codequest.data.AwardsData.PeriodAward periodAwardOfLesson() {
        codequest.data.AwardsData.PeriodAward a = subjectDone == null ? null : subjectDone.award(lesson.periodEnum());
        return a != null && a.certificate() != null ? a : null;
    }

    private boolean feedbackAvailable() {
        return !classTest && !feedbackItems.isEmpty();
    }

    /** The result buttons from left to right: feedback (campaign), certificate (rank B or better), continue. */
    private Rect resultButton(int slot) {
        int count = 1 + (feedbackAvailable() ? 1 : 0) + (certificateEligible() ? 1 : 0);
        float total = count * RESULT_BTN_W + (count - 1) * RESULT_BTN_GAP;
        return new Rect(W / 2f - total / 2f + slot * (RESULT_BTN_W + RESULT_BTN_GAP), 880, RESULT_BTN_W, 80);
    }

    private Rect feedbackButton() {
        return resultButton(0);
    }

    private Rect certButton() {
        return resultButton(feedbackAvailable() ? 1 : 0);
    }

    private Rect continueButton() {
        return resultButton((feedbackAvailable() ? 1 : 0) + (certificateEligible() ? 1 : 0));
    }

    /** The pill button built into the feedback panel's picture (panel at {@link #AI_X}, {@link #AI_Y}). */
    private Rect aiCloseButton() {
        return new Rect(AI_X + 600f, AI_Y + 797f, 440f, 88f);
    }

    /** Asks the AI for feedback on this run (once; the answer is kept for the rest of the result screen). */
    private void openFeedback() {
        aiOpen = true;
        if (aiText != null || aiLoading) {
            return;
        }
        aiLoading = true;
        aiError = null;
        List<AiFeedback.Item> items = List.copyOf(feedbackItems);
        String title = lesson.title();
        String rank = run.rank().name();
        int accuracy = (int) Math.round(run.overallAccuracy() * 100);
        uploader.submit(() -> {
            AiFeedback.Result r = new AiFeedback(language).forStudent(title, rank, accuracy, items);
            aiText = r.text();
            aiError = r.error();
            aiLoading = false;
        });
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

    private static final float LIFT = 8f;
    private static final float ROW_LIFT = 5f;

    /** How far a button is raised right now: the mouse is over it and it can be pressed. The colors never change. */
    private float lift(Rect r, boolean enabled, float amount) {
        return enabled && r.contains(mouseX, mouseY) ? amount : 0f;
    }

    private float lift(Rect r) {
        return lift(r, true, LIFT);
    }

    /** A button's fill: raised over a soft shadow while the mouse is over it (its label must use the same {@link #lift}). */
    private void buttonFill(Rect r, Color color, boolean enabled, float amount) {
        float up = lift(r, enabled, amount);
        if (collectingPanelButtons) {
            panelButtons.add(new PanelButton(r, color, up, enabled));
            return;
        }
        if (up > 0f) {
            fill(r.x + 5, r.y + 8, r.w - 10, r.h, new Color(0f, 0f, 0f, 0.55f));
        }
        fill(r.x, r.y - up, r.w, r.h, color);
    }

    private void buttonFill(Rect r, Color color) {
        buttonFill(r, color, true, LIFT);
    }

    // ── Drawing ──────────────────────────────────────────

    private void draw() {
        ScreenUtils.clear(0.04f, 0.06f, 0.15f, 1f);
        viewport.apply();
        Vector2 pointer = viewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        mouseX = pointer.x;
        mouseY = H - pointer.y;
        sr.setProjectionMatrix(camera.combined);

        batch.setProjectionMatrix(worldCamera.combined);
        batch.begin();
        batch.setColor(Color.WHITE); // the last frame's weapon glow or spark must not tint the map
        map.draw(batch);
        batch.end();
        drawMapEdges();
        batch.begin();
        drawWorldSprites();
        drawVerdictPopup();
        batch.end();
        batch.setProjectionMatrix(camera.combined);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        drawSpeedLines();
        drawTimerBar();
        sr.begin(ShapeRenderer.ShapeType.Filled);
        drawWorldShapes();
        sr.end();
        // the sidebar, shifted to the right while it slides away
        if (phase != Phase.RESULT) { // the result screen is a screen of its own: no lesson sidebar behind it
            sr.setProjectionMatrix(sidebarMatrix());
            sr.begin(ShapeRenderer.ShapeType.Filled);
            drawSidebarShapes();
            sr.end();
            batch.setProjectionMatrix(sidebarMatrix());
            drawSidebarPictures();
        }
        batch.setProjectionMatrix(camera.combined);
        sr.setProjectionMatrix(camera.combined);
        if (phase == Phase.ENCOUNTER) {
            drawEncounterLayers();
        }
        if (phase == Phase.FEEDBACK && explainChallenge != null) {
            drawExplainPictures();
        }
        sr.begin(ShapeRenderer.ShapeType.Filled);
        if (phase == Phase.RESULT) {
            fill(0, 0, W, H, new Color(0f, 0f, 0f, 0.82f));
            if (!aiOpen) {
                Rect cont = continueButton();
                buttonFill(cont, new Color(0.3f, 0.1f, 0.1f, 1f), saveDone, LIFT);
                if (certificateEligible()) {
                    Rect cert = certButton();
                    buttonFill(cert, new Color(0.12f, 0.3f, 0.12f, 1f));
                }
                if (feedbackAvailable()) {
                    Rect fb = feedbackButton();
                    buttonFill(fb, new Color(0.25f, 0.18f, 0.5f, 1f));
                }
            }
        }
        sr.end();

        if (phase == Phase.RESULT && aiOpen) {
            drawAiLayers();
        }
        batch.begin();
        if (phase != Phase.RESULT) {
            batch.setProjectionMatrix(sidebarMatrix());
            drawSidebarText();
            batch.setProjectionMatrix(camera.combined);
        }
        if (phase == Phase.WALKING) {
            text("Section " + run.currentSectionNumber() + " / " + run.sectionCount(), 30, 20, 1.5f, Color.WHITE);
        } else if (phase == Phase.ENCOUNTER) {
            batch.setProjectionMatrix(panelMatrix());
            drawEncounterText();
            batch.setProjectionMatrix(camera.combined);
        } else if (phase == Phase.FEEDBACK) {
            if (explainChallenge != null) {
                batch.setProjectionMatrix(explainMatrix());
                drawExplanation();
                batch.setProjectionMatrix(camera.combined);
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
        return Assets.texture(path);
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
            // the batch keeps the color of the last trail dot (about 10 % opaque): without this the hero is drawn as a ghost
            batch.setColor(Color.WHITE);
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

    /**
     * Manga-style speed lines: thin wedges that start away from the middle of the action and widen toward the edges,
     * redrawn at random every fraction of a second so they flicker. Drawn over the map, under the panels.
     */
    private void drawSpeedLines() {
        if (lineFade < 0.005f) {
            return;
        }
        float progress = flightTime > 0f ? Math.max(0f, Math.min(1f, 1f - timeLeft / flightTime)) : 0f;
        // where the action appears on the screen (the world camera is zoomed and moved)
        float cx = W / 2f + (focusMapX - camX) / camZoom;
        float cy = H / 2f + (focusMapTop - camTop) / camZoom;
        int count = 34 + Math.round(60f * progress);
        Random random = new Random((long) (lineClock / 0.07f) * 7919L + 13L);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * (float) (Math.PI * 2);
            float inner = 330f + random.nextFloat() * 420f;
            float outer = 1900f;
            float half = (0.008f + random.nextFloat() * 0.02f) * (1f + progress);
            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            float px = -dy;
            float py = dx;
            float baseHalf = outer * (float) Math.tan(half);
            float tipX = cx + dx * inner;
            float tipY = H - (cy + dy * inner);
            float bx = cx + dx * outer;
            float by = cy + dy * outer;
            float alpha = Math.min(1f, lineFade * (0.7f + random.nextFloat() * 0.5f));
            sr.triangle(tipX, tipY,
                    bx + px * baseHalf, H - (by + py * baseHalf),
                    bx - px * baseHalf, H - (by - py * baseHalf),
                    new Color(0f, 0f, 0f, 0f), new Color(0f, 0f, 0f, alpha), new Color(0f, 0f, 0f, alpha));
        }
        sr.end();
    }

    private void drawWorldShapes() {
    }

    /** Where the (smaller) question panel sits on the screen: centred, at the top or the bottom. */
    private float panelScreenX() {
        return (W - 1280f * PANEL_SCALE) / 2f;
    }

    private float panelScreenTop() {
        return panelTop < 300f ? 96f : H - 30f - PANEL_H * PANEL_SCALE;
    }

    /** Draws in the panel's own coordinates (the layout the question screens were built with) but scaled and moved. */
    private com.badlogic.gdx.math.Matrix4 panelMatrix() {
        float tx = panelScreenX() - 60f * PANEL_SCALE;
        float ty = (H - panelScreenTop()) - (H - panelTop) * PANEL_SCALE;
        return camera.combined.cpy().translate(tx, ty, 0f).scale(PANEL_SCALE, PANEL_SCALE, 1f);
    }

    /** A point on the screen (top-left origin) in the panel's layout coordinates. */
    private float[] toPanel(float screenX, float screenTop) {
        return new float[] { 60f + (screenX - panelScreenX()) / PANEL_SCALE,
                panelTop + (screenTop - panelScreenTop()) / PANEL_SCALE };
    }

    private float explainScreenX() {
        return (MAP_WIDTH - 1280f * EXPLAIN_SCALE) / 2f;
    }

    private float explainScreenTop() {
        return explainOnTop ? 100f : H - 30f - 410f * EXPLAIN_SCALE;
    }

    private com.badlogic.gdx.math.Matrix4 explainMatrix() {
        float tx = explainScreenX() - 60f * EXPLAIN_SCALE;
        float ty = (H - explainScreenTop()) - (H - explainTop) * EXPLAIN_SCALE;
        return camera.combined.cpy().translate(tx, ty, 0f).scale(EXPLAIN_SCALE, EXPLAIN_SCALE, 1f);
    }

    private float[] toExplain(float screenX, float screenTop) {
        return new float[] { 60f + (screenX - explainScreenX()) / EXPLAIN_SCALE,
                explainTop + (screenTop - explainScreenTop()) / EXPLAIN_SCALE };
    }

    /** Puts the mouse position in the panel's layout coordinates, so every hit test and hover below keeps working. */
    private void mouseToPanel() {
        Vector2 m = viewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        float[] p = toPanel(m.x, H - m.y);
        mouseX = p[0];
        mouseY = p[1];
    }

    private static boolean sameColor(Color c, float r, float g, float b) {
        return Math.abs(c.r - r) < 0.01f && Math.abs(c.g - g) < 0.01f && Math.abs(c.b - b) < 0.01f;
    }

    /** Which picture of the question kit a button's flat colour stands for. */
    private static String pictureKind(Color c) {
        if (sameColor(c, 0.85f, 0.6f, 0.1f)) {
            return "hint";
        } else if (sameColor(c, 0.75f, 0.15f, 0.18f)) {
            return "clear";
        } else if (sameColor(c, 0.15f, 0.6f, 0.28f)) {
            return "check";
        } else if (sameColor(c, 0.15f, 0.55f, 0.3f)) {
            return "token_selected";
        } else if (sameColor(c, 0.5f, 0.31f, 0.08f)) {
            return "row_selected";
        } else if (sameColor(c, 0.09f, 0.12f, 0.21f)) {
            return "row_idle";
        }
        return "token";
    }

    private static UiKit.Patch panelPatch(String kind, String state) {
        return switch (kind) {
            case "hint", "clear", "check" -> UiKit.q(kind + "_" + state, 34, 0);
            case "token_selected" -> UiKit.q("token_selected", 34, 0);
            case "row_selected" -> UiKit.q("code_row_selected", 30, 0);
            case "row_idle" -> UiKit.q("code_row_idle", 30, 0);
            default -> UiKit.q("token_" + state, 34, 0);
        };
    }

    private record PanelButton(Rect rect, Color color, float up, boolean enabled) {
    }

    private final List<PanelButton> panelButtons = new ArrayList<>();
    private boolean collectingPanelButtons;
    private final java.util.Map<String, com.badlogic.gdx.scenes.scene2d.utils.Drawable> panelPictures = new java.util.HashMap<>();

    private com.badlogic.gdx.scenes.scene2d.utils.Drawable panelPicture(String key, UiKit.Patch patch) {
        return panelPictures.computeIfAbsent(key, k -> patch.drawable());
    }

    /** The question panel in three layers: its frame, the flat shapes inside, then the picture buttons (text comes last). */
    private void drawEncounterLayers() {
        com.badlogic.gdx.math.Matrix4 pm = panelMatrix();
        batch.setProjectionMatrix(pm);
        batch.begin();
        batch.setColor(Color.WHITE);
        panelPicture("frame", UiKit.q("question_frame", 34, 34)).draw(batch, 60f - 12f, H - (panelTop - 12f) - (PANEL_H + 24f),
                1280f + 24f, PANEL_H + 24f);
        // where the answer goes
        if (active != null) {
            Rect slot = switch (active.kind()) {
                case FILL_BLANK -> answerBox();
                case ARRANGE -> new Rect(90, builtY() - 8, ROW_WIDTH, CHIP_H + 16);
                default -> null;
            };
            if (slot != null) {
                panelPicture("slot", UiKit.q("answer_slot", 24, 0)).draw(batch, slot.x, H - (slot.y + slot.h), slot.w, slot.h);
            }
        }
        // the "code" badge on the panel's corner
        batch.draw(sprite("ui/q/question_tab_icon.png"), 24f, H - (panelTop - 24f) - 64f, 64f, 64f);
        batch.end();

        sr.setProjectionMatrix(pm);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        panelButtons.clear();
        collectingPanelButtons = true;
        drawEncounterShapes();
        collectingPanelButtons = false;
        sr.end();

        batch.begin();
        for (PanelButton b : panelButtons) {
            Rect rc = b.rect();
            boolean pressed = b.enabled() && Gdx.input.isTouched() && rc.contains(mouseX, mouseY);
            String kind = pictureKind(b.color());
            String state = !b.enabled() ? "idle" : pressed ? "pressed" : b.up() > 0f ? "hover" : "idle";
            float drop = pressed ? 3f : 0f;
            panelPicture(kind + state, panelPatch(kind, state)).draw(batch, rc.x - 5f,
                    H - (rc.y - b.up() + drop + rc.h) - 5f, rc.w + 10f, rc.h + 10f);
        }
        batch.end();
        batch.setProjectionMatrix(camera.combined);
        sr.setProjectionMatrix(camera.combined);
    }

    /** The map picture ends in a hard edge; a soft fade into the background colour hides it, at any zoom. */
    private void drawMapEdges() {
        float mapW = MAP_WIDTH;
        float mapH = MAP_WIDTH * 902f / 1640f;
        float bottom = (H - mapH) / 2f;
        float top = bottom + mapH;
        float fade = 64f;
        Color solid = new Color(0.04f, 0.06f, 0.15f, 1f);
        Color clear = new Color(0.04f, 0.06f, 0.15f, 0f);
        sr.setProjectionMatrix(worldCamera.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.rect(0, bottom, fade, mapH, solid, clear, clear, solid);                   // left
        sr.rect(mapW - fade, bottom, fade, mapH, clear, solid, solid, clear);         // right
        sr.rect(0, bottom, mapW, fade, solid, solid, clear, clear);                   // bottom
        sr.rect(0, top - fade, mapW, fade, clear, clear, solid, solid);               // top
        sr.end();
        sr.setProjectionMatrix(camera.combined);
    }

    /** "Deflected! +10" (or the hit) floats up from the hero, then fades. */
    private void drawVerdictPopup() {
        if (popAge > 2.2f || popText.isEmpty() || (phase != Phase.FEEDBACK && phase != Phase.WALKING)) {
            return;
        }
        Vector2 hero = pointAt(distance);
        float alpha = popAge < 1.6f ? 1f : Math.max(0f, 1f - (popAge - 1.6f) / 0.6f);
        float rise = Math.min(46f, popAge * 90f);
        float pulse = 1.25f - 0.25f * Math.min(1f, popAge * 6f);
        String[] lines = popText.split("\n");
        float y = hero.y - 120f - rise;
        Color main = new Color(feedbackColor.r, feedbackColor.g, feedbackColor.b, alpha);
        drawCentered(Face.SANS, lines[0], hero.x, y, 2.2f * pulse, new Color(0f, 0f, 0f, alpha * 0.55f));
        drawCentered(Face.SANS, lines[0], hero.x - 2f, y - 2f, 2.2f * pulse, main);
        for (int i = 1; i < lines.length; i++) {
            drawCentered(Face.SANS, lines[i], hero.x, y + 46f * i, 1.3f, new Color(1f, 1f, 1f, alpha));
        }
    }

    /** The time left for the open question: a bar with an hourglass across the top of the map, green, then yellow, then red. */
    private void drawTimerBar() {
        if (phase != Phase.ENCOUNTER || active == null || flightTime <= 0f) {
            return;
        }
        float x = 60f;
        float y = 10f;
        float ratio = Math.max(0f, Math.min(1f, timeLeft / flightTime));
        batch.begin();
        batch.setColor(Color.WHITE);
        panelPicture("timerbar", UiKit.hud("hud_timer_bar", 96, 0)).draw(batch, x, H - y - 70f, 1280f, 70f);
        if (ratio > 0.01f) {
            String fill = ratio > 0.5f ? "hud_timer_fill_green" : ratio > 0.25f ? "hud_timer_fill_yellow" : "hud_timer_fill_red";
            panelPicture(fill, UiKit.hud(fill, 16, 0)).draw(batch, x + 125f, H - (y + 20f) - 30f, Math.max(24f, 1015f * ratio), 30f);
        }
        batch.end();
    }

    private void drawSidebarPictures() {
        float sx = SIDEBAR_X;
        boolean program = run.buildsProgram();
        batch.begin();
        batch.setColor(Color.WHITE);
        panelPicture("hdr", UiKit.hud("side_header_panel", 44, 36)).draw(batch, sx + 8, H - 8 - 108, 504, 108);
        batch.draw(sprite("ui/hud/cap_icon.png"), sx + 22, H - 22 - 72, 72, 72);
        float statsH = program ? 540f : 836f;
        panelPicture("stats" + program, UiKit.hud("side_stats_panel", 44, 36)).draw(batch, sx + 8, H - 124 - statsH, 504, statsH);
        String pill = switch (run.difficulty()) {
            case EASY -> "pill_easy";
            case NORMAL -> "pill_normal";
            case HARD -> "pill_hard";
        };
        panelPicture(pill, UiKit.hud(pill, 30, 0)).draw(batch, sx + 362, H - 138 - 44, 130, 44);
        if (!classTest) {
            float ratio = Math.max(0f, Math.min(1f, run.hp() / (float) StageRun.MAX_HP));
            panelPicture("hptrack", UiKit.hud("hp_track", 16, 0)).draw(batch, sx + 30, H - 222 - 34, 440, 34);
            if (ratio > 0.01f) {
                String fill = ratio > 0.5f ? "hp_fill_green" : ratio > 0.25f ? "hp_fill_yellow" : "hp_fill_red";
                panelPicture(fill, UiKit.hud(fill, 14, 0)).draw(batch, sx + 30, H - 222 - 34, Math.max(28f, 440f * ratio), 34);
            }
            batch.draw(sprite("ui/hud/star_icon.png"), sx + 26, H - 270 - 40, 40, 40);
        }
        panelPicture("div", UiKit.hud("side_divider", 20, 0)).draw(batch, sx + 32, H - (classTest ? 340f : 324f) - 6, 440, 6);
        if (program) {
            panelPicture("term", UiKit.hud("side_terminal_panel", 44, 36)).draw(batch, sx + 8, H - 672 - 296, 504, 296);
            batch.draw(sprite("ui/hud/terminal_icon.png"), sx + 26, H - 684 - 38, 38, 38);
        }
        if (phase != Phase.RESULT) {
            boolean pressed = Gdx.input.isTouched() && QUIT_BTN.contains(mouseX, mouseY);
            String state = pressed ? "pressed" : QUIT_BTN.contains(mouseX, mouseY) ? "hover" : "idle";
            panelPicture("quit" + state, UiKit.hud("quit_button_" + state, 40, 0)).draw(batch, QUIT_BTN.x - 4,
                    H - (QUIT_BTN.y + QUIT_BTN.h) - 4 - (pressed ? 3f : 0f), QUIT_BTN.w + 8, QUIT_BTN.h + 8);
        }
        batch.end();
    }

    /** The explanation panel after an answer, with its green play button. */
    private void drawExplainPictures() {
        mouseToExplain();
        batch.setProjectionMatrix(explainMatrix());
        batch.begin();
        batch.setColor(Color.WHITE);
        panelPicture("explain", UiKit.hud("explain_panel", 50, 40)).draw(batch, 60, H - explainTop - 410, 1280, 410);
        Rect go = explainContinueButton();
        boolean ready = explainAge > 0.4f;
        float up = lift(go, ready, LIFT);
        boolean pressed = ready && Gdx.input.isTouched() && go.contains(mouseX, mouseY);
        String state = !ready ? "idle" : pressed ? "pressed" : up > 0f ? "hover" : "idle";
        panelPicture("play" + state, UiKit.hud("play_button_" + state, 34, 0)).draw(batch, go.x - 5,
                H - (go.y - up + go.h) - 5, go.w + 10, go.h + 10);
        batch.end();
        batch.setProjectionMatrix(camera.combined);
    }

    private void mouseToExplain() {
        Vector2 m = viewport.unproject(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
        float[] p = toExplain(m.x, H - m.y);
        mouseX = p[0];
        mouseY = p[1];
    }

    /** The screen matrix moved right by how far the sidebar has slid out. */
    private com.badlogic.gdx.math.Matrix4 sidebarMatrix() {
        float s = Math.max(0f, Math.min(1f, sidebarSlide));
        float eased = s * s * (3f - 2f * s);
        return camera.combined.cpy().translate((W - SIDEBAR_X + 30f) * eased, 0f, 0f);
    }

    private void drawSidebarShapes() {
        fill(SIDEBAR_X, 0, W - SIDEBAR_X, H, new Color(0.03f, 0.05f, 0.13f, 1f));
    }

    private void drawEncounterShapes() {
        if (!hintShown && !classTest) {
            Rect h = hintButton();
            buttonFill(h, new Color(0.85f, 0.6f, 0.1f, 1f));
        }
        mouseToPanel();
        Color idle = new Color(0.14f, 0.18f, 0.28f, 1f);
        switch (active.kind()) {
            case CHOICE, TRUE_FALSE -> {
                for (int i = 0; i < options.size(); i++) {
                    Rect b = tokenButton(i);
                    buttonFill(b, idle);
                }
            }
            case FIND_BUG -> {
                for (int i = 0; i < options.size(); i++) {
                    Rect r = lineRect(i);
                    boolean picked = i == pickedLine;
                    float up = lift(r, true, ROW_LIFT);
                    panelButtons.add(new PanelButton(r, picked ? new Color(0.5f, 0.31f, 0.08f, 1f)
                            : new Color(0.09f, 0.12f, 0.21f, 1f), up, true));
                }
                Rect check = bugCheckButton();
                buttonFill(check, pickedLine < 0 ? new Color(0.14f, 0.16f, 0.22f, 1f) : new Color(0.15f, 0.6f, 0.28f, 1f),
                        pickedLine >= 0, LIFT);
            }
            case FILL_BLANK -> {
                Rect go = submitButton();
                buttonFill(go, new Color(0.15f, 0.6f, 0.28f, 1f));
            }
            case ARRANGE -> {
                float scale = chipScale();
                for (Rect r : chipRects(labels(placed), builtY(), scale)) {
                    buttonFill(r, new Color(0.15f, 0.55f, 0.3f, 1f), true, ROW_LIFT);
                }
                for (Rect r : chipRects(labels(remaining()), bankY(), scale)) {
                    buttonFill(r, idle, true, ROW_LIFT);
                }
                Rect check = checkButton();
                buttonFill(check, new Color(0.15f, 0.6f, 0.28f, 1f));
                Rect clear = clearButton();
                buttonFill(clear, new Color(0.75f, 0.15f, 0.18f, 1f));
            }
        }
    }

    private void drawSidebarText() {
        float x = SIDEBAR_X + 30;
        text(classTest ? "CLASS TEST" : "LESSON " + lesson.number(), SIDEBAR_X + 104, 28, 2f, Color.WHITE);
        text(shorten(lesson.title(), 24), SIDEBAR_X + 104, 72, 1.2f, new Color(0.75f, 0.8f, 0.95f, 1f));
        text("Section " + run.currentSectionNumber() + "/" + run.sectionCount(), x, 148, 1.5f, Color.WHITE);
        drawCentered(Face.SANS, run.difficulty().name(), SIDEBAR_X + 362 + 65, 148, 1.2f, Color.WHITE);
        float codeHeader = classTest ? 356f : 340f;
        if (classTest) {
            updateStanding();
            text(liveTotal > 0 ? "Rank #" + liveRank + " of " + liveTotal : "Rank --", x, 196, 2.2f, GOLD_TEXT);
            text(pointsBehind > 0 ? pointsBehind + " pts behind the next player"
                    : liveRank == 1 && liveTotal > 1 ? "You are in the lead!" : "", x, 246, 1.3f, Color.LIGHT_GRAY);
            text("Points " + run.score(), x, 280, 1.7f, new Color(0.7f, 0.9f, 0.55f, 1f));
            text(run.streak() >= 2 ? "Streak x" + run.streak() : "", x, 316, 1.4f, new Color(1f, 0.6f, 0.2f, 1f));
        } else {
            text("HP " + run.hp() + "/" + StageRun.MAX_HP, x, 188, 1.3f, Color.WHITE);
            text("Score " + run.score(), x + 50, 280, 1.5f, new Color(0.7f, 0.9f, 0.55f, 1f));
        }

        int blocks = run.layout() == null ? 1 : run.layout().blocks();
        text("CODE BLOCK", x, codeHeader, 1.4f, Color.WHITE);
        if (run.buildsProgram() && blocks > 1) {
            text("Program " + (run.displayBlock() + 1) + " of " + blocks, x + 240, codeHeader + 2, 1.2f, GOLD_TEXT);
        }
        float body = codeHeader + 44f;
        if (run.buildsProgram()) {
            // the program being built: every answered question added its lines, green if right and red if wrong
            List<CodeBlock.Line> lines = run.codeBlock().lines(run.displayBlock());
            if (lines.isEmpty()) {
                text("(empty)", x, body + 3, 1.3f, Color.GRAY);
            }
            int firstLine = Math.max(0, lines.size() - 10);
            for (int i = firstLine; i < lines.size(); i++) {
                CodeBlock.Line l = lines.get(i);
                float ly = body + (i - firstLine) * 24f;
                text(Face.MONO, String.valueOf(l.number()), x, ly, 1.0f, new Color(0.45f, 0.5f, 0.65f, 1f));
                text(Face.MONO, l.text(), x + 34, ly, 1.1f,
                        l.correct() ? new Color(0.55f, 0.95f, 0.55f, 1f) : new Color(1f, 0.45f, 0.4f, 1f));
            }
            drawTerminal(x, 694, 960);
        } else {
            List<CodeBlock.Token> tokens = run.codeBlock().tokens();
            if (tokens.isEmpty()) {
                text("(empty)", x, body + 3, 1.3f, Color.GRAY);
            }
            int first = Math.max(0, tokens.size() - 14);
            for (int i = first; i < tokens.size(); i++) {
                CodeBlock.Token t = tokens.get(i);
                text(Face.MONO, (t.correct() ? "+ " : "x ") + t.text(), x, body + (i - first) * 36f, 1.45f,
                        t.correct() ? new Color(0.55f, 0.95f, 0.55f, 1f) : new Color(1f, 0.45f, 0.4f, 1f));
            }
        }
        if (phase != Phase.RESULT) {
            boolean over = QUIT_BTN.contains(mouseX, mouseY);
            drawCentered(classTest ? "Leave (you can resume)" : "Quit (progress is saved)", QUIT_BTN.x + QUIT_BTN.w / 2f + 26f,
                    QUIT_BTN.y + 17 - (over ? LIFT : 0f), 1.4f, Color.WHITE);
        }
    }

    /** The terminal under the code block: what the finished program prints, or the compiler's messages for the mistakes in it. */
    private void drawTerminal(float x, float top, float bottom) {
        text("TERMINAL", x + 40, top, 1.4f, Color.WHITE);
        List<Terminal.Row> rows = run.terminal();
        float width = 440f;
        if (rows.isEmpty()) {
            drawWrapped(Face.SANS, "Finish a program to see its output here.", x, top + 40, 1.1f, new Color(0.55f, 0.6f, 0.78f, 1f), width);
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
        text("Tower: " + active.topic().name().replace('_', ' '), 110, panelTop + 38, 1.8f,
                topicColor(active.topic()));
        AnswerKind kind = active.kind();
        Color muted = new Color(0.75f, 0.8f, 0.95f, 1f);
        if (kind == AnswerKind.FIND_BUG) {
            // unmistakable: this is not a multiple choice, one of the lines has a mistake
            text("FIND THE BUG", 520, panelTop + 18, 1.7f, new Color(1f, 0.6f, 0.2f, 1f));
            text(Face.SANS, "One line has a mistake. Tap it, then Check.", 735, panelTop + 22, 1.2f,
                    new Color(1f, 0.82f, 0.55f, 1f));
        } else {
            text(Face.SANS, kind.label() + "  -  " + kind.instruction(), 470, panelTop + 42, 1.5f, muted);
        }
        int lines = active.prompt().split("\n", -1).length;
        if (active.context() != null) {
            // what the code is for, so a blank like  if (age ___ 18)  has exactly one right answer
            text(Face.SANS, "Goal: " + shorten(active.context(), 80), 90, panelTop + 72, 1.5f, new Color(0.65f, 0.9f, 1f, 1f));
        }
        if (kind != AnswerKind.FIND_BUG) {
            drawWrapped(faceFor(active), active.prompt(), 90, panelTop + 112, lines <= 3 ? 2.2f : lines <= 5 ? 1.9f : 1.5f, Color.WHITE, 1160f);
        }
        Color gold = new Color(0.95f, 0.85f, 0.45f, 1f);
        if (classTest) {
            // no hints in a class test
        } else if (!hintShown) {
            Rect h = hintButton();
            drawCentered("Hint", h.x + h.w / 2f, h.y + 11 - lift(h), 2.0f, Color.WHITE);
        } else if (active.hint() != null) {
            if (kind == AnswerKind.FIND_BUG || kind == AnswerKind.ARRANGE) {
                // their rows fill the lower panel: arrange has room under its sentence, find the bug under its code lines
                float hintY = kind == AnswerKind.ARRANGE ? panelTop + 146 : panelTop + 80 + lines * 42f + 10f;
                text(Face.SANS, "Hint: " + shorten(active.hint(), 90), 90, hintY, 1.4f, gold);
            } else {
                drawWrapped(Face.SANS, "Hint: " + active.hint(), 90, panelTop + 280, 1.6f, gold, 1160f);
            }
        }
        switch (kind) {
            case CHOICE -> {
                for (int i = 0; i < options.size(); i++) {
                    Rect b = tokenButton(i);
                    String tok = options.get(i);
                    boolean tiny = tok.length() <= 2;
                    drawCentered(Face.MONO, tok, b.x + b.w / 2f, b.y + (tiny ? 14 : 24) - lift(b), tiny ? 3.0f : 2.2f, Color.WHITE);
                }
            }
            case TRUE_FALSE -> {
                for (int i = 0; i < options.size(); i++) {
                    Rect b = tokenButton(i);
                    drawCentered(Face.SANS, options.get(i), b.x + b.w / 2f, b.y + 22 - lift(b), 2.4f, Color.WHITE);
                }
            }
            case FIND_BUG -> {
                for (int i = 0; i < options.size(); i++) {
                    Rect r = lineRect(i);
                    float up = lift(r, true, ROW_LIFT);
                    drawCentered(Face.MONO, String.valueOf(i + 1), r.x + 25, r.y + 6 - up, 1.45f, new Color(0.65f, 0.72f, 0.9f, 1f));
                    text(Face.MONO, active.codeLines().get(i), r.x + 66, r.y + 6 - up, 1.55f, Color.WHITE);
                }
                Rect check = bugCheckButton();
                float checkUp = lift(check, pickedLine >= 0, LIFT);
                drawCentered(Face.SANS, pickedLine < 0 ? "Pick a line" : "Check line " + (pickedLine + 1),
                        check.x + check.w / 2f, check.y + 18 - checkUp, 1.5f, pickedLine < 0 ? Color.GRAY : Color.WHITE);
                drawCentered(Face.SANS, pickedLine < 0 ? "tap the buggy one" : "or press Enter", check.x + check.w / 2f,
                        check.y + 50 - checkUp, 1.05f, pickedLine < 0 ? Color.DARK_GRAY : new Color(0.75f, 0.95f, 0.75f, 1f));
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
                drawCentered(Face.SANS, "Answer  (Enter)", go.x + go.w / 2f, go.y + 26 - lift(go), 1.8f, Color.WHITE);
                if (run.difficulty() == Difficulty.EASY && !active.correctToken().isEmpty()) {
                    // an easy typed answer shows its shape: how it starts and how long it is
                    String shape = "Starts with \"" + active.correctToken().charAt(0) + "\"  -  " + active.correctToken().length()
                            + (active.correctToken().length() == 1 ? " character" : " characters");
                    text(Face.SANS, shape, box.x, box.y - 30, 1.45f, new Color(0.65f, 0.9f, 1f, 1f));
                }
            }
            case ARRANGE -> {
                float scale = chipScale();
                text(Face.SANS, "Your line  (tap a token to take it back)", 90, panelTop + 176, 1.4f,
                        new Color(0.65f, 0.72f, 0.9f, 1f));
                text(Face.SANS, "Tokens", 90, panelTop + 288, 1.4f, new Color(0.65f, 0.72f, 0.9f, 1f));
                drawChips(chipRects(labels(placed), builtY(), scale), labels(placed), scale);
                List<String> left = labels(remaining());
                drawChips(chipRects(left, bankY(), scale), left, scale);
                Rect check = checkButton();
                drawCentered(Face.SANS, "Check  (Enter)", check.x + check.w / 2f, check.y + 11 - lift(check), 1.5f, Color.WHITE);
                Rect clear = clearButton();
                drawCentered(Face.SANS, "Clear", clear.x + clear.w / 2f, clear.y + 11 - lift(clear), 1.5f, Color.WHITE);
            }
        }
    }

    private void drawChips(List<Rect> rects, List<String> chipLabels, float scale) {
        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);
            drawCentered(Face.MONO, chipLabels.get(i), r.x + r.w / 2f,
                    r.y + (CHIP_H - PixelFonts.BASE * scale) / 2f - 2f - lift(r, true, ROW_LIFT), scale, Color.WHITE);
        }
    }

    /** After an answer: the verdict, the right answer, what the student answered, and why. */
    private void drawExplanation() {
        Challenge c = explainChallenge;
        float y = explainTop;
        mouseToExplain();
        boolean timedOut = explainToken == null;
        Color verdict = explainCorrect ? new Color(0.55f, 0.95f, 0.55f, 1f) : new Color(1f, 0.55f, 0.5f, 1f);
        text(Face.SANS, explainCorrect ? "Correct!" : timedOut ? "Time ran out" : "Not quite", 90, y + 22, 2.7f, verdict);

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
        text(Face.SANS, "Right answer", 90, y + 84, 1.5f, new Color(0.65f, 0.72f, 0.9f, 1f));
        text(c.kind() == AnswerKind.TRUE_FALSE ? Face.SANS : Face.MONO, right, 90, y + 112, 2.2f, Color.WHITE);

        float next = y + 176;
        if (!explainCorrect && !timedOut) {
            String yours = c.kind() == AnswerKind.FIND_BUG ? "line " + explainToken : explainToken;
            text(Face.SANS, "You answered", 90, next, 1.5f, new Color(0.65f, 0.72f, 0.9f, 1f));
            text(c.kind() == AnswerKind.TRUE_FALSE ? Face.SANS : Face.MONO, shorten(yours, 60), 90, next + 28, 1.9f, verdict);
            next += 76;
        }
        text(Face.SANS, "Why", 90, next, 1.5f, GOLD_TEXT);
        drawWrapped(Face.SANS, c.explanationText(), 90, next + 32, 1.8f, Color.WHITE, 1160f);

        if (explainAge <= 0.4f) {
            Rect go = explainContinueButton();
            drawCentered(Face.SANS, "...", go.x + go.w / 2f, go.y + 15, 1.6f, Color.GRAY);
        }
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

    /** The AI's written feedback for this run, in a panel over the result screen. */
    private void drawAiFeedback() {
        Color cyan = new Color(0.55f, 0.85f, 1f, 1f);
        drawCentered(Face.SANS, "Feedback from your AI tutor", AI_X + 860f, AI_Y + 76f, 2.5f, Color.WHITE);

        // left card: rank, accuracy, answers
        float cx = AI_CARD_X + AI_CARD_W / 2f;
        drawCentered(Face.SANS, shorten(lesson.title(), 26), cx, AI_BODY_Y + 36f, 1.35f, new Color(0.7f, 0.78f, 0.95f, 1f));
        drawCentered(run.rank().name(), cx, AI_BODY_Y + 82f, 9f, new Color(1f, 0.85f, 0.35f, 1f));
        drawCentered(Face.SANS, "RANK", cx, AI_BODY_Y + 232f, 1.3f, cyan);
        int accuracy = (int) Math.round(run.overallAccuracy() * 100);
        drawCentered(Face.SANS, "Accuracy  " + accuracy + "%", cx, AI_BODY_Y + 262f, 2.1f, Color.WHITE);
        int right = 0;
        for (AiFeedback.Item i : feedbackItems) {
            right += i.correct() ? 1 : 0;
        }
        drawCentered(Face.SANS, right + " of " + feedbackItems.size() + " answers correct", cx, AI_BODY_Y + 342f, 1.4f,
                new Color(0.75f, 0.82f, 0.95f, 1f));

        // right: what the tutor says
        text(Face.SANS, "Tutor says", AI_TEXT_X + 58f, AI_BODY_Y + 24f, 1.9f, GOLD_TEXT);
        if (aiLoading) {
            String dots = ".".repeat(1 + (int) (System.currentTimeMillis() / 400 % 3));
            drawCentered(Face.SANS, "Writing your feedback" + dots, AI_TEXT_X + AI_TEXT_W / 2f, AI_BODY_Y + 190f, 2.0f, Color.LIGHT_GRAY);
        } else if (aiError != null) {
            drawWrapped(Face.SANS, aiError, AI_TEXT_X + 24f, AI_BODY_Y + 100f, 1.7f, new Color(1f, 0.55f, 0.5f, 1f), AI_TEXT_W - 48f);
        } else if (aiText != null) {
            fitAiText();
            drawWrapped(Face.SANS, aiFitText, AI_TEXT_X + 24f, AI_BODY_Y + 84f, aiFitScale, Color.WHITE, AI_TEXT_W - 48f);
        }
        for (AiChip c : aiChips()) {
            drawCentered(Face.SANS, c.label(), c.x() + c.w() / 2f, AI_CHIP_Y + 8f, 1.3f, Color.WHITE);
        }

        text(Face.SANS, "Written by AI. It can make mistakes.", AI_X + 195f, AI_Y + 730f, 1.35f, new Color(0.55f, 0.75f, 1f, 1f));
        Rect close = aiCloseButton();
        drawCentered(Face.SANS, "Close", close.x + close.w / 2f, close.y + 28f, 2.1f, Color.WHITE);
    }

    private static final float AI_X = 140f;
    private static final float AI_Y = 60f;
    private static final float AI_BODY_Y = AI_Y + 205f;      // top of the dark area inside the picture
    private static final float AI_CARD_X = AI_X + 100f;
    private static final float AI_CARD_W = 340f;
    private static final float AI_TEXT_X = AI_X + 480f;
    private static final float AI_TEXT_W = 1070f;
    private static final float AI_TEXT_H = 400f;
    private static final float AI_CHIP_Y = AI_BODY_Y + 405f;
    private static final int AI_DOT_COLS = 9;

    private record AiChip(String label, float x, float w, boolean allRight) {
    }

    private String aiFitSource;
    private String aiFitText = "";
    private float aiFitScale = 1.7f;

    /** The tutor's words at the biggest size that fits the text box; a very long answer is shortened with "...". */
    private void fitAiText() {
        String src = aiText;
        if (src == null || src == aiFitSource) {
            return;
        }
        float width = AI_TEXT_W - 48f;
        float maxH = AI_TEXT_H - 100f;
        for (float scale = 1.7f; scale >= 1.19f; scale -= 0.1f) {
            layout.setText(font(Face.SANS, scale), src, Color.WHITE, width, com.badlogic.gdx.utils.Align.left, true);
            if (layout.height <= maxH) {
                aiFitSource = src;
                aiFitText = src;
                aiFitScale = scale;
                return;
            }
        }
        aiFitScale = 1.2f;
        String s = src;
        while (s.length() > 20) {
            s = s.substring(0, Math.max(0, s.lastIndexOf(' '))).trim();
            layout.setText(font(Face.SANS, aiFitScale), s + "...", Color.WHITE, width, com.badlogic.gdx.utils.Align.left, true);
            if (layout.height <= maxH) {
                break;
            }
        }
        aiFitText = s + "...";
        aiFitSource = src;
    }

    private static float aiDotX(int i) {
        return AI_CARD_X + 30f + (i % AI_DOT_COLS) * 35f;
    }

    private static float aiDotY(int i) {
        return AI_BODY_Y + 394f + (i / AI_DOT_COLS) * 34f;
    }

    /** One chip per topic of this lesson: how many of its questions were right. */
    private List<AiChip> aiChips() {
        java.util.Map<String, int[]> byTopic = new java.util.LinkedHashMap<>();
        for (AiFeedback.Item i : feedbackItems) {
            int[] n = byTopic.computeIfAbsent(i.topic(), k -> new int[2]);
            n[0] += i.correct() ? 1 : 0;
            n[1]++;
        }
        List<AiChip> chips = new ArrayList<>();
        float x = AI_TEXT_X;
        for (var e : byTopic.entrySet()) {
            String name = e.getKey().isEmpty() ? "" : e.getKey().charAt(0) + e.getKey().substring(1).toLowerCase().replace('_', ' ');
            String label = name + "  " + e.getValue()[0] + "/" + e.getValue()[1];
            layout.setText(font(Face.SANS, 1.3f), label);
            float w = layout.width + 44f;
            if (x + w > AI_TEXT_X + AI_TEXT_W) {
                break;
            }
            chips.add(new AiChip(label, x, w, e.getValue()[0] == e.getValue()[1]));
            x += w + 14f;
        }
        return chips;
    }

    /** The feedback panel's pictures and shapes, under its text: the panel, the rank card, the accuracy bar, answer dots, chips. */
    private void drawAiLayers() {
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(sprite("ui/hud/ai_panel.png"), AI_X, H - AI_Y - 959f, 1639f, 959f);
        int accuracy = (int) Math.round(run.overallAccuracy() * 100);
        String barFill = accuracy >= 70 ? "hp_fill_green" : accuracy >= 40 ? "hp_fill_yellow" : "hp_fill_red";
        float barX = AI_CARD_X + 30f;
        float barY = AI_BODY_Y + 310f;
        panelPicture("aitrack", UiKit.hud("hp_track", 16, 0)).draw(batch, barX, H - barY - 28f, 280f, 28f);
        panelPicture("ai" + barFill, UiKit.hud(barFill, 14, 0)).draw(batch, barX, H - barY - 28f, Math.max(28f, 280f * accuracy / 100f), 28f);
        batch.draw(sprite("ui/icons/sparkle_ai.png"), AI_TEXT_X + 12f, H - (AI_BODY_Y + 12f) - 40f, 40f, 40f);
        batch.end();

        sr.begin(ShapeRenderer.ShapeType.Filled);
        // the rank card and the text box, as lighter plates on the dark area
        fill(AI_CARD_X, AI_BODY_Y + 4f, AI_CARD_W, 470f, new Color(0.09f, 0.17f, 0.38f, 0.75f));
        fill(AI_CARD_X, AI_BODY_Y + 4f, AI_CARD_W, 3f, new Color(0.2f, 0.75f, 1f, 1f));
        fill(AI_TEXT_X, AI_BODY_Y + 60f, AI_TEXT_W, AI_TEXT_H - 60f, new Color(0.04f, 0.1f, 0.26f, 0.85f));
        fill(AI_TEXT_X, AI_BODY_Y + 60f, 4f, AI_TEXT_H - 60f, new Color(1f, 0.78f, 0.2f, 1f));
        for (AiChip c : aiChips()) {
            Color base = c.allRight() ? new Color(0.1f, 0.45f, 0.25f, 1f) : new Color(0.55f, 0.38f, 0.08f, 1f);
            fill(c.x(), AI_CHIP_Y, c.w(), 40f, base);
            fill(c.x(), AI_CHIP_Y, c.w(), 3f, c.allRight() ? new Color(0.4f, 0.95f, 0.6f, 1f) : new Color(1f, 0.8f, 0.3f, 1f));
        }
        for (int i = 0; i < Math.min(feedbackItems.size(), AI_DOT_COLS * 3); i++) {
            boolean ok = feedbackItems.get(i).correct();
            float dx = aiDotX(i);
            float dy = aiDotY(i);
            circle(dx, dy, 14f, ok ? new Color(0.15f, 0.65f, 0.3f, 1f) : new Color(0.8f, 0.22f, 0.22f, 1f));
            sr.setColor(Color.WHITE);
            if (ok) {
                sr.rectLine(dx - 6f, H - dy, dx - 2f, H - dy - 5f, 3f);
                sr.rectLine(dx - 2f, H - dy - 5f, dx + 7f, H - dy + 6f, 3f);
            } else {
                sr.rectLine(dx - 6f, H - dy - 6f, dx + 6f, H - dy + 6f, 3f);
                sr.rectLine(dx - 6f, H - dy + 6f, dx + 6f, H - dy - 6f, 3f);
            }
        }
        Rect close = aiCloseButton();
        if (close.contains(mouseX, mouseY)) {
            fill(close.x + 14f, close.y + 8f, close.w - 28f, close.h - 16f, new Color(1f, 1f, 1f, Gdx.input.isTouched() ? 0.28f : 0.14f));
        }
        sr.end();
    }

    private void drawResultText() {
        if (aiOpen) {
            drawAiFeedback();
            return;
        }
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
                drawCentered("Reward already claimed.", W / 2f, 735,
                        1.5f, new Color(0.7f, 0.75f, 0.9f, 1f));
            } else {
                drawCentered("You earned a reward roll!  Open it on the next screen.", W / 2f, 735, 1.6f,
                        new Color(1f, 0.85f, 0.4f, 1f));
            }
        }
        if (certificateEligible()) {
            drawCentered(subjectDone.certificate() != null
                    ? "You finished every lesson of " + subjectDone.name() + ": you earned the subject certificate!"
                    : "You finished the " + lesson.periodEnum().label() + " period: you earned its certificate!", W / 2f, 780, 1.5f,
                    new Color(0.6f, 0.95f, 0.6f, 1f));
            Rect cert = certButton();
            drawCentered("Get certificate", cert.x + cert.w / 2f, cert.y + 28 - lift(cert), 1.8f, Color.WHITE);
        }
        if (cleared && !classTest) {
            drawCentered("Badge earned for lesson " + lesson.number() + "!  Find it under Badges and certificates.", W / 2f, 830, 1.4f,
                    new Color(1f, 0.85f, 0.4f, 1f));
        }
        if (feedbackAvailable()) {
            Rect fb = feedbackButton();
            drawCentered("Get feedback (AI)", fb.x + fb.w / 2f, fb.y + 28 - lift(fb), 1.7f, Color.WHITE);
        }
        Rect cont = continueButton();
        drawCentered(saveDone ? "Continue" : "Saving...", cont.x + cont.w / 2f, cont.y + 28 - lift(cont, saveDone, LIFT), 1.8f, Color.WHITE);
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
        map.dispose();
        if (standings != null) {
            standings.close();
        }
        if (uploader != null) {
            uploader.shutdown();
        }
    }
}
