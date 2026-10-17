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
import codequest.session.RunSummary;
import codequest.game.GameMap;
import codequest.session.CodeBlock;
import codequest.session.Grades;
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
    private static final Rect CONTINUE_ALONE = new Rect(760, 880, 400, 80);

    private final Main game;
    private final String username;
    private final Lesson lesson;
    private final ClassTestData.Session testSession;
    private final boolean classTest;
    private final Difficulty startDifficulty;
    private final int towerCount;
    private final DatabaseManager db = new DatabaseManager();
    private Color skinTint = Color.WHITE;
    private Color trailColor;
    private String rewardLine = "";

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

    /** Campaign play of a lesson. */
    public PlayScreen(Main game, String username, Lesson lesson) {
        this(game, username, lesson, null);
    }

    /** testSession != null plays the lesson as a class test: fixed difficulty, same questions for everyone. */
    public PlayScreen(Main game, String username, Lesson lesson, ClassTestData.Session testSession) {
        this.game = game;
        this.username = username;
        this.lesson = lesson;
        this.testSession = testSession;
        this.classTest = testSession != null;
        this.startDifficulty = lesson.difficulty();
        this.rng = classTest ? new Random(testSession.id()) : new Random();

        Stage stage = DefaultStages.forNumber(DefaultStages.layoutFor(lesson.number()));
        this.towerCount = stage.towerCount();
        // D4 (campaign content) first; the built-in questions only cover what D4 does not have yet.
        List<Challenge> authored = db.getStageChallenges(lesson.stageId());
        ChallengeProvider provider = new FallbackChallengeProvider(
                classTest ? new PooledChallengeProvider(authored, testSession.id()) : new PooledChallengeProvider(authored),
                new StaticChallengeProvider());
        this.run = new StageRun(stage, startDifficulty, classTest ? StageRun.Mode.CLASS_TEST : StageRun.Mode.CAMPAIGN,
                provider, lesson.adaptive());
        loadLook();

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
        StageRun.Outcome o = run.answer(token);
        towerIdx++;
        active = null;

        StringBuilder sb = new StringBuilder();
        if (o.correct()) {
            sb.append("Deflected!  +").append(StageRun.POINTS_PER_CORRECT);
            feedbackColor = new Color(0.55f, 0.95f, 0.55f, 1f);
        } else {
            sb.append(token == null ? "Too slow!" : "Hit!")
                    .append("  Answer: ").append(o.correctToken())
                    .append("   -").append(o.damage()).append(" HP");
            feedbackColor = new Color(1f, 0.55f, 0.5f, 1f);
        }
        SectionResult closed = o.sectionClosed();
        if (closed != null) {
            sb.append("\nSection ").append(closed.sectionIndex()).append(" done: ")
                    .append(Math.round(closed.accuracy() * 100)).append("%");
            if (closed.nextDifficulty() != closed.playedAt()) {
                sb.append("   difficulty ").append(closed.playedAt()).append(" -> ").append(closed.nextDifficulty());
            }
        }
        feedback = sb.toString();
        feedbackTimer = closed != null ? 2.8f : 1.5f;
        phase = Phase.FEEDBACK;
    }

    private void finishRun() {
        phase = Phase.RESULT;
        if (saved) {
            return;
        }
        saved = true;
        if (classTest) {
            long studentId = db.profileId(username);
            if (studentId >= 0) {
                new ClassTestData().submitResult(testSession.id(), studentId, run.score());
            }
        } else if (!run.isFailed()) {
            db.saveLessonProgress(username, lesson.stageId(), startDifficulty.name().toLowerCase(), run.score(),
                    run.rank().name());
            List<DatabaseManager.Cosmetic> rewards = db.grantLessonRewards(username, lesson.number());
            if (!rewards.isEmpty()) {
                StringBuilder sb = new StringBuilder("Reward unlocked: ");
                for (int i = 0; i < rewards.size(); i++) {
                    sb.append(i > 0 ? ", " : "").append(rewards.get(i).name());
                }
                rewardLine = sb.toString();
            }
        }
    }

    private void leave() {
        if (classTest && phase == Phase.RESULT) {
            game.setScreen(new ClassTestResultScreen(game, username, testSession, RunSummary.of(run, towerCount)));
        } else if (classTest) {
            game.setScreen(new MainMenuScreen(game, username));
        } else {
            game.setScreen(new CampaignScreen(game, username));
        }
    }

    /** Applies the equipped skin and trail (placeholder colors until the cosmetic art exists). */
    private void loadLook() {
        long studentId = db.profileId(username);
        if (studentId < 0) {
            return;
        }
        Map<String, Long> equipped = db.getEquippedCosmetics(studentId);
        if (equipped.isEmpty()) {
            return;
        }
        for (DatabaseManager.Cosmetic c : db.getCatalog()) {
            if (c.id() == equipped.getOrDefault(c.category(), -1L)) {
                if ("avatar_skin".equals(c.category()) && !c.isDefault()) {
                    skinTint = CosmeticLook.colorOf(c);
                } else if ("trail".equals(c.category()) && !c.isDefault()) {
                    trailColor = CosmeticLook.colorOf(c);
                }
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
            if (continueButton().contains(mx, my)) {
                leave();
            } else if (certificateEligible() && CERT_BTN.contains(mx, my)) {
                game.setScreen(new CertificateScreen(game, username, lesson,
                        run.rank().name(), run.overallAccuracy()));
            }
            return true;
        }
        if (QUIT_BTN.contains(mx, my)) {
            leave();
            return true;
        }
        if (phase == Phase.ENCOUNTER) {
            if (!hintShown && hintButton().contains(mx, my)) {
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

    /** Towers, avatar and the incoming projectile (placeholder sprites; y is flipped from the top-left layout). */
    private void drawWorldSprites() {
        for (int i = 0; i < towers.size(); i++) {
            TowerSpot t = towers.get(i);
            Texture tex = sprite("game/towers/tower_" + t.topic().name().toLowerCase() + ".png");
            batch.setColor(i < towerIdx ? new Color(0.5f, 0.5f, 0.55f, 0.9f) : Color.WHITE);
            batch.draw(tex, t.x() - 42, H - t.y() - 60, 84, 105);
        }
        batch.setColor(Color.WHITE);

        Vector2 a = pointAt(distance);
        if (trailColor != null) {
            for (int i = 1; i <= 5; i++) {
                Vector2 back = pointAt(distance - i * 18f);
                batch.setColor(trailColor.r, trailColor.g, trailColor.b, 0.7f - i * 0.12f);
                float size = 26 - i * 3;
                batch.draw(sprite("game/projectile.png"), back.x - size / 2, H - back.y - size / 2, size, size);
            }
        }
        batch.setColor(skinTint);
        batch.draw(sprite("game/avatar.png"), a.x - 36, H - a.y - 40, 72, 72);
        batch.setColor(Color.WHITE);

        if (phase == Phase.ENCOUNTER) {
            TowerSpot t = towers.get(towerIdx);
            float p = 1f - timeLeft / flightTime;
            float px = t.x() + (a.x - t.x()) * p;
            float py = t.y() + (a.y - t.y()) * p;
            batch.draw(sprite("game/projectile.png"), px - 30, H - py - 30, 60, 60);
        }
    }

    private void drawWorldShapes() {
    }

    private void drawSidebarShapes() {
        fill(SIDEBAR_X, 0, W - SIDEBAR_X, H, new Color(0.05f, 0.07f, 0.17f, 0.96f));
        float ratio = run.hp() / (float) StageRun.MAX_HP;
        fill(SIDEBAR_X + 20, 150, 480, 26, new Color(0.25f, 0.05f, 0.05f, 1f));
        Color hpColor = ratio > 0.5f ? new Color(0.2f, 0.75f, 0.2f, 1f)
                : ratio > 0.25f ? new Color(0.85f, 0.7f, 0.1f, 1f) : new Color(0.8f, 0.15f, 0.15f, 1f);
        fill(SIDEBAR_X + 20, 150, 480 * ratio, 26, hpColor);
        fill(QUIT_BTN.x, QUIT_BTN.y, QUIT_BTN.w, QUIT_BTN.h, new Color(0.3f, 0.1f, 0.1f, 1f));
    }

    private void drawEncounterShapes() {
        fill(60, panelTop, 1280, PANEL_H, new Color(0.05f, 0.07f, 0.17f, 0.95f));
        float ratio = Math.max(0f, timeLeft / flightTime);
        fill(60, panelTop, 1280 * ratio, 10, ratio > 0.4f ? new Color(0.3f, 0.8f, 0.3f, 1f)
                : new Color(0.9f, 0.25f, 0.2f, 1f));
        if (!hintShown) {
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
        text("HP " + run.hp() + "/" + StageRun.MAX_HP, x, 188, 1.3f, Color.WHITE);
        text("Score " + run.score(), x, 230, 1.5f, new Color(0.7f, 0.9f, 0.55f, 1f));

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
        drawCentered("Quit to menu", QUIT_BTN.x + QUIT_BTN.w / 2f, QUIT_BTN.y + 18, 1.4f, Color.WHITE);
    }

    private void drawEncounterText() {
        text("Tower: " + active.topic().name().replace('_', ' '), 90, panelTop + 25, 1.4f,
                topicColor(active.topic()));
        int lines = active.prompt().split("\n", -1).length;
        text(active.prompt(), 90, panelTop + 80, lines <= 5 ? 2.0f : 1.55f, Color.WHITE);
        if (!hintShown) {
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
        drawCentered(classTest ? "TEST FINISHED" : cleared ? "LESSON COMPLETE" : "RUN FAILED", W / 2f, 130, 3f,
                cleared ? new Color(0.6f, 0.95f, 0.6f, 1f) : new Color(1f, 0.5f, 0.45f, 1f));
        drawCentered(run.rank().name(), W / 2f, 230, 8f, new Color(1f, 0.85f, 0.35f, 1f));
        drawCentered("Accuracy " + Math.round(run.overallAccuracy() * 100) + "%     Score " + run.score()
                + "     HP " + run.hp(), W / 2f, 440, 1.8f, Color.WHITE);

        float y = 500;
        for (SectionResult r : run.sectionResults()) {
            drawCentered("Section " + r.sectionIndex() + ":  " + r.correct() + "/" + r.total() + "  ("
                    + Math.round(r.accuracy() * 100) + "%)   " + r.playedAt() + " -> " + r.nextDifficulty(),
                    W / 2f, y, 1.5f, Color.LIGHT_GRAY);
            y += 40;
        }
        if (classTest) {
            drawCentered("Grade " + Grades.percent(run.score(), Grades.maxScore(towerCount)) + "%", W / 2f, 780, 1.8f,
                    new Color(0.6f, 0.95f, 0.6f, 1f));
        } else if (!rewardLine.isEmpty()) {
            drawCentered(rewardLine, W / 2f, 735, 1.6f, new Color(1f, 0.85f, 0.4f, 1f));
        }
        if (certificateEligible()) {
            drawCentered("Rank B or higher: you earned a lesson certificate", W / 2f, 780, 1.5f,
                    new Color(0.6f, 0.95f, 0.6f, 1f));
            drawCentered("Get certificate", CERT_BTN.x + CERT_BTN.w / 2f, CERT_BTN.y + 28, 1.8f, Color.WHITE);
        }
        Rect cont = continueButton();
        drawCentered("Continue", cont.x + cont.w / 2f, cont.y + 28, 1.8f, Color.WHITE);
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
    }
}
