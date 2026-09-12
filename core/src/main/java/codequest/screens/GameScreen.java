package codequest.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
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
import codequest.Main;
import codequest.data.DatabaseManager;
import codequest.game.*;
import codequest.game.towers.BantayBantayan;
import codequest.game.towers.LantakaCannon;
import codequest.game.towers.LantakaProjectile;
import codequest.game.towers.Mandirigma;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Port of GamePanel. The Swing version ran on a discrete 16ms javax.swing.Timer
 * (60 ticks/sec) and every balance number (spawn delays, attack cooldowns,
 * prep/wave-delay timers) was tuned in units of "ticks". LibGDX's render(delta)
 * fires at a variable rate, so this uses a fixed-timestep accumulator to keep
 * calling update() exactly 60 times/sec regardless of the actual frame rate,
 * preserving that original tick-based tuning unchanged.
 *
 * Also: this doesn't extend BaseScreen. BaseScreen is built around Scene2D's
 * Stage/Actor model for menus with buttons; GamePanel painted everything by
 * hand and tracked raw mouse coordinates for hover/placement, which fits a
 * plain InputAdapter + SpriteBatch/ShapeRenderer loop better.
 */
public class GameScreen extends InputAdapter implements Screen {

    private enum State { PLAYING, PAUSED, VICTORY, GAME_OVER }

    private static final float SIDEBAR_WIDTH = 520f;
    private static final float MAP_WIDTH = BaseScreen.VIRTUAL_WIDTH - SIDEBAR_WIDTH;
    private static final int PREP_SECONDS = 10;
    private static final int WAVE_DELAY_SECONDS = 9;
    private static final float TICK = 1f / 60f;
    private static final String[] STAGE_NAMES = {
            "", "Gubat ng mga Nilalang", "Bundok ng Sumpa", "Tore ng Engkanto"
    };

    private final Main game;
    private final String username;
    private final int stageNumber;
    private final String difficulty;
    private final DatabaseManager dbManager = new DatabaseManager();

    private final OrthographicCamera camera = new OrthographicCamera();
    private final Viewport viewport;
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer sr = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();

    private int gold, lives, score;
    private State state = State.PLAYING;

    private GameMap gameMap;
    private WaveManager waveManager;
    private List<Enemy> enemies;
    private List<Tower> towers;
    private List<Projectile> projectiles;
    private boolean[][] occupiedCells;

    private String selectedTowerType;
    private int hoverCol = -1, hoverRow = -1;
    private Tower selectedPlacedTower;

    private Texture mandirigmaImg, lantakaImg, bantayImg;

    private int prepTimer, waveDelayTimer;
    private boolean prepPhase, waitingForNextWave;

    private int cursorX, cursorY;
    private float accumulator = 0f;

    private String toastMessage;
    private float toastTimer;

    public GameScreen(Main game, String username, int stageNumber, String difficulty) {
        this.game = game;
        this.username = username;
        this.stageNumber = stageNumber;
        this.difficulty = difficulty;
        this.viewport = new FitViewport(BaseScreen.VIRTUAL_WIDTH, BaseScreen.VIRTUAL_HEIGHT, camera);

        switch (difficulty) {
            case "easy":
                gold = 250;
                lives = 30;
                break;
            case "hard":
                gold = 150;
                lives = 10;
                break;
            default:
                gold = 200;
                lives = 20;
                break;
        }
        score = 0;
        prepPhase = true;
        prepTimer = PREP_SECONDS * 60;
        waitingForNextWave = false;
        waveDelayTimer = 0;

        enemies = new ArrayList<>();
        towers = new ArrayList<>();
        projectiles = new ArrayList<>();
        gameMap = new GameMap(stageNumber, (int) MAP_WIDTH);
        occupiedCells = new boolean[gameMap.getCols()][gameMap.getRows()];
        waveManager = new WaveManager(stageNumber, difficulty, gameMap.getWaypoints(), enemies);

        mandirigmaImg = TextureCache.get("images/enemies_towers/mandirigma.png");
        lantakaImg = TextureCache.get("images/enemies_towers/lantaka_cannon.png");
        bantayImg = TextureCache.get("images/enemies_towers/bantay.png");
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(this);
    }

    // ── Game Loop ──────────────────────────────────────

    @Override
    public void render(float delta) {
        if (state == State.PLAYING) {
            accumulator += delta;
            while (accumulator >= TICK) {
                update();
                accumulator -= TICK;
            }
        }
        if (toastTimer > 0) {
            toastTimer -= delta;
        }

        ScreenUtils.clear(0, 0, 0, 1);
        viewport.apply();
        batch.setProjectionMatrix(camera.combined);
        sr.setProjectionMatrix(camera.combined);

        draw();
    }

    private void update() {
        if (waveManager.isAllWavesComplete() && enemies.isEmpty()) {
            triggerVictory();
            return;
        }

        if (prepPhase) {
            prepTimer--;
            if (prepTimer <= 0) {
                prepPhase = false;
                waveManager.startNextWave();
            }
            updateEnemies();
            updateTowers();
            updateProjectiles();
            return;
        }

        if (waitingForNextWave) {
            waveDelayTimer--;
            if (waveDelayTimer <= 0) {
                waitingForNextWave = false;
                if (!waveManager.isAllWavesComplete()) {
                    waveManager.startNextWave();
                }
            }
            updateEnemies();
            updateTowers();
            updateProjectiles();
            return;
        }

        waveManager.update();
        updateEnemies();
        updateTowers();
        updateProjectiles();
        checkWaveCleared();
        checkGameOver();
    }

    private void updateEnemies() {
        for (Enemy e : enemies) {
            e.update();
            if (e.hasReachedEnd()) {
                lives -= e.getDamage();
                e.setAlive(false);
            }
        }
        enemies.removeIf(e -> !e.isAlive());
    }

    private void updateTowers() {
        for (Tower t : towers) {
            t.findTarget(enemies);
            Projectile p = t.update();
            if (p != null) {
                if (p instanceof LantakaProjectile) {
                    ((LantakaProjectile) p).setAllEnemies(enemies);
                }
                projectiles.add(p);
            }
        }
    }

    private void updateProjectiles() {
        Iterator<Projectile> it = projectiles.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            p.update();
            if (!p.isAlive()) {
                if (p.getTarget() != null && !p.getTarget().isAlive()) {
                    gold += p.getTarget().getReward();
                    score += getScoreForEnemy(p.getTarget());
                }
                it.remove();
            }
        }
    }

    private int getScoreForEnemy(Enemy e) {
        int base = e.getReward() * 10;
        if (difficulty.equals("hard"))
            return (int) (base * 1.5);
        if (difficulty.equals("easy"))
            return (int) (base * 0.8);
        return base;
    }

    private void checkWaveCleared() {
        if (waveManager.isAllWavesComplete() && enemies.isEmpty()) {
            triggerVictory();
            return;
        }

        boolean isLastWave = waveManager.getCurrentWave() >= waveManager.getTotalWaves();
        if (!waveManager.isWaveInProgress() && enemies.isEmpty()
                && !waveManager.isAllWavesComplete()
                && !waitingForNextWave && !prepPhase && !isLastWave) {
            waitingForNextWave = true;
            waveDelayTimer = WAVE_DELAY_SECONDS * 60;
        }
    }

    private void checkGameOver() {
        if (lives <= 0) {
            lives = 0;
            state = State.GAME_OVER;
        }
    }

    private void triggerVictory() {
        state = State.VICTORY;
        dbManager.saveGameProgress(username, stageNumber, difficulty, score);
    }

    private void returnToMainMenu() {
        int unlockedStage = dbManager.getUnlockedStage(username);
        game.setScreen(new MainMenuScreen(game, username, unlockedStage));
    }

    // ── Input ──────────────────────────────────────────

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        Vector2 world = viewport.unproject(new Vector2(screenX, screenY));
        int mx = (int) world.x;
        int my = (int) (BaseScreen.VIRTUAL_HEIGHT - world.y);

        if (state == State.PAUSED) {
            handlePauseClick(mx, my);
            return true;
        }
        if (state == State.VICTORY || state == State.GAME_OVER) {
            if (isInOverlayContinue(mx, my)) {
                returnToMainMenu();
            }
            return true;
        }

        if (button == Input.Buttons.RIGHT) {
            selectedTowerType = null;
            return true;
        }

        float btnX = MAP_WIDTH + 20f, btnY = 800f, btnW = SIDEBAR_WIDTH - 40f, btnH = 80f;
        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
            waveManager.startNextWave();
            return true;
        }

        float pauseSize = 44f, pauseX = MAP_WIDTH - pauseSize - 12f, pauseY = 8f;
        if (mx >= pauseX && mx <= pauseX + pauseSize && my >= pauseY && my <= pauseY + pauseSize) {
            state = State.PAUSED;
            return true;
        }

        if (mx >= MAP_WIDTH + 20f && my >= 960f) {
            returnToMainMenu();
            return true;
        }

        if (mx >= MAP_WIDTH) {
            handleSidebarClick(mx, my);
            return true;
        }

        handleMapClick(mx, my);
        return true;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        Vector2 world = viewport.unproject(new Vector2(screenX, screenY));
        cursorX = (int) world.x;
        cursorY = (int) (BaseScreen.VIRTUAL_HEIGHT - world.y);

        if (cursorX >= MAP_WIDTH) {
            hoverCol = -1;
            hoverRow = -1;
            return false;
        }
        Point cell = gameMap.pixelToCell(cursorX, cursorY);
        if (cell != null) {
            hoverCol = cell.x;
            hoverRow = cell.y;
        }
        return false;
    }

    private void handleSidebarClick(float mx, float my) {
        float x = MAP_WIDTH + 20f;
        float btnH = 110f, startY = 270f, spacing = 140f;

        if (mx >= x && mx <= MAP_WIDTH + SIDEBAR_WIDTH - 20f) {
            if (my >= startY && my <= startY + btnH)
                selectedTowerType = "mandirigma";
            else if (my >= startY + spacing && my <= startY + spacing + btnH)
                selectedTowerType = "lantaka";
            else if (my >= startY + spacing * 2 && my <= startY + spacing * 2 + btnH)
                selectedTowerType = "bantay";
        }
    }

    private void handleMapClick(float mx, float my) {
        if (selectedTowerType == null)
            return;
        Point cell = gameMap.pixelToCell((int) mx, (int) my);
        if (cell == null)
            return;

        int col = cell.x, row = cell.y;
        if (!gameMap.canPlaceTower(col, row, occupiedCells))
            return;

        Tower tower = createTower(selectedTowerType, col, row);
        if (tower == null)
            return;

        if (gold < tower.getCost()) {
            showToast("Not enough sampaguita!");
            return;
        }

        for (Tower t : towers) {
            double dx = mx - t.getX();
            double dy = my - t.getY();
            if (Math.sqrt(dx * dx + dy * dy) <= gameMap.getCellSize() / 2f) {
                selectedPlacedTower = (selectedPlacedTower == t) ? null : t;
                return;
            }
        }

        gold -= tower.getCost();
        occupiedCells[col][row] = true;
        towers.add(tower);
        selectedTowerType = null;
    }

    private Tower createTower(String type, int col, int row) {
        Point pixel = gameMap.cellToPixel(col, row);
        switch (type) {
            case "mandirigma":
                return new Mandirigma(pixel.x, pixel.y);
            case "lantaka":
                return new LantakaCannon(pixel.x, pixel.y);
            case "bantay":
                return new BantayBantayan(pixel.x, pixel.y);
            default:
                return null;
        }
    }

    private void showToast(String message) {
        toastMessage = message;
        toastTimer = 2f;
    }

    // ── Pause / Victory / Game Over overlays ────────────

    private static final float OVERLAY_BTN_W = 400f, OVERLAY_BTN_H = 80f, OVERLAY_SPACING = 100f;

    private void handlePauseClick(int mx, int my) {
        float x = (BaseScreen.VIRTUAL_WIDTH - OVERLAY_BTN_W) / 2f;
        float startY = 420f;
        String[] options = { "Resume", "Restart", "Main Menu", "Exit Game" };
        for (int i = 0; i < options.length; i++) {
            float y = startY + OVERLAY_SPACING * i;
            if (mx >= x && mx <= x + OVERLAY_BTN_W && my >= y && my <= y + OVERLAY_BTN_H) {
                switch (i) {
                    case 0:
                        state = State.PLAYING;
                        break;
                    case 1:
                        game.setScreen(new GameScreen(game, username, stageNumber, difficulty));
                        break;
                    case 2:
                        returnToMainMenu();
                        break;
                    case 3:
                        Gdx.app.exit();
                        break;
                }
                return;
            }
        }
    }

    private boolean isInOverlayContinue(int mx, int my) {
        float x = (BaseScreen.VIRTUAL_WIDTH - OVERLAY_BTN_W) / 2f;
        float y = 600f;
        return mx >= x && mx <= x + OVERLAY_BTN_W && my >= y && my <= y + OVERLAY_BTN_H;
    }

    // ── Drawing ────────────────────────────────────────

    private void draw() {
        batch.begin();
        gameMap.draw(batch);
        batch.end();

        sr.begin(ShapeRenderer.ShapeType.Filled);
        drawStageInfoBg();
        drawPauseButtonChrome(true);
        sr.end();

        batch.begin();
        drawStageInfoText();
        batch.end();

        if (hoverCol >= 0 && selectedTowerType != null) {
            boolean canPlace = gameMap.canPlaceTower(hoverCol, hoverRow, occupiedCells);
            sr.begin(ShapeRenderer.ShapeType.Filled);
            gameMap.drawHoverCell(sr, hoverCol, hoverRow, canPlace, true);
            sr.end();
            sr.begin(ShapeRenderer.ShapeType.Line);
            gameMap.drawHoverCell(sr, hoverCol, hoverRow, canPlace, false);
            sr.end();
        }

        if (selectedTowerType != null && cursorX < MAP_WIDTH) {
            batch.begin();
            drawTowerGhost(cursorX, cursorY);
            batch.end();
        }

        if (selectedPlacedTower != null) {
            drawRangeCircle((float) selectedPlacedTower.getX(), (float) selectedPlacedTower.getY(),
                    (float) selectedPlacedTower.getRange());
        }
        if (selectedTowerType != null && cursorX < MAP_WIDTH) {
            drawRangeCircle(cursorX, cursorY, (float) getTowerRange(selectedTowerType));
        }

        batch.begin();
        for (Tower t : towers)
            t.draw(batch);
        for (Enemy e : enemies)
            e.draw(batch);
        for (Projectile p : projectiles)
            p.draw(batch);
        batch.end();

        sr.begin(ShapeRenderer.ShapeType.Filled);
        for (Enemy e : enemies)
            e.drawHpBar(sr);
        sr.end();

        drawSidebar();

        if (toastTimer > 0 && toastMessage != null) {
            batch.begin();
            drawCentered(toastMessage, MAP_WIDTH / 2f, 120f, new Color(1f, 0.5f, 0.5f, 1f), 1.2f);
            batch.end();
        }

        if (state == State.PAUSED) {
            drawPauseOverlay();
        } else if (state == State.VICTORY) {
            drawEndOverlay("STAGE " + stageNumber + " CLEARED!",
                    "Score: " + score + "   Lives remaining: " + lives);
        } else if (state == State.GAME_OVER) {
            drawEndOverlay("GAME OVER", "Score: " + score);
        }
    }

    private void drawRangeCircle(float cx, float topCy, float radius) {
        float cy = BaseScreen.VIRTUAL_HEIGHT - topCy;
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(new Color(1f, 1f, 1f, 40 / 255f));
        sr.circle(cx, cy, radius, 48);
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(new Color(1f, 1f, 1f, 120 / 255f));
        sr.circle(cx, cy, radius, 48);
        sr.end();
    }

    private void drawTowerGhost(float mx, float topMy) {
        Texture img;
        switch (selectedTowerType) {
            case "mandirigma":
                img = mandirigmaImg;
                break;
            case "lantaka":
                img = lantakaImg;
                break;
            case "bantay":
                img = bantayImg;
                break;
            default:
                return;
        }
        float size = 52f;
        float y = BaseScreen.VIRTUAL_HEIGHT - topMy - size / 2f;
        batch.setColor(1f, 1f, 1f, 0.5f);
        batch.draw(img, mx - size / 2f, y, size, size);
        batch.setColor(Color.WHITE);
    }

    private double getTowerRange(String type) {
        switch (type) {
            case "mandirigma":
                return 210;
            case "lantaka":
                return 175;
            case "bantay":
                return 350;
            default:
                return 200;
        }
    }

    private void drawStageInfoBg() {
        float barHeight = gameMap.getOffsetY();
        sr.setColor(Color.BLACK);
        sr.rect(0, BaseScreen.VIRTUAL_HEIGHT - barHeight, MAP_WIDTH, barHeight);
    }

    private void drawStageInfoText() {
        float barHeight = gameMap.getOffsetY();
        font.setColor(new Color(180 / 255f, 140 / 255f, 40 / 255f, 1f));
        font.getData().setScale(1.4f);
        font.draw(batch, "STAGE " + stageNumber, 20f, barHeight - 35f + 20f);

        font.setColor(new Color(200 / 255f, 180 / 255f, 120 / 255f, 1f));
        font.getData().setScale(1.1f);
        font.draw(batch, STAGE_NAMES[stageNumber], 20f, barHeight - 10f + 14f);

        Color diffColor;
        switch (difficulty) {
            case "easy":
                diffColor = new Color(80 / 255f, 200 / 255f, 80 / 255f, 1f);
                break;
            case "hard":
                diffColor = new Color(220 / 255f, 60 / 255f, 60 / 255f, 1f);
                break;
            default:
                diffColor = new Color(220 / 255f, 180 / 255f, 60 / 255f, 1f);
                break;
        }
        String diff = difficulty.substring(0, 1).toUpperCase() + difficulty.substring(1);
        String label = "[ " + diff + " ]";
        font.setColor(diffColor);
        font.getData().setScale(1.1f);
        layout.setText(font, label);
        font.draw(batch, label, MAP_WIDTH - layout.width - 20f, barHeight - 20f + layout.height);
        font.getData().setScale(1f);
    }

    private void drawPauseButtonChrome(boolean filled) {
        float size = 44f, x = MAP_WIDTH - size - 12f, topY = 8f;
        float y = BaseScreen.VIRTUAL_HEIGHT - topY - size;

        sr.setColor(new Color(20 / 255f, 15 / 255f, 8 / 255f, 200 / 255f));
        sr.rect(x, y, size, size);

        float barW = 5f, barH = 18f, gap = 5f;
        float totalW = barW * 2 + gap;
        float barX1 = x + (size - totalW) / 2f;
        float barX2 = barX1 + barW + gap;
        float barY = y + (size - barH) / 2f;
        sr.setColor(new Color(220 / 255f, 200 / 255f, 140 / 255f, 1f));
        sr.rect(barX1, barY, barW, barH);
        sr.rect(barX2, barY, barW, barH);
    }

    private void drawSidebar() {
        float sx = MAP_WIDTH;

        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(new Color(15 / 255f, 10 / 255f, 5 / 255f, 230 / 255f));
        sr.rect(sx, 0, SIDEBAR_WIDTH, BaseScreen.VIRTUAL_HEIGHT);
        drawTowerSlotBg(mandirigmaImg != null, sx, 270f, "mandirigma");
        drawTowerSlotBg(true, sx, 410f, "lantaka");
        drawTowerSlotBg(true, sx, 550f, "bantay");
        drawStartWaveButtonBg(sx);
        sr.end();

        batch.begin();
        font.setColor(new Color(220 / 255f, 180 / 255f, 60 / 255f, 1f));
        font.getData().setScale(1.1f);
        font.draw(batch, "Wave: " + waveManager.getCurrentWave() + "/" + waveManager.getTotalWaves(),
                sx + 20f, BaseScreen.VIRTUAL_HEIGHT - 60f + 14f);
        font.setColor(new Color(220 / 255f, 80 / 255f, 80 / 255f, 1f));
        font.draw(batch, "Lives: " + lives, sx + 20f, BaseScreen.VIRTUAL_HEIGHT - 100f + 14f);
        font.setColor(new Color(1f, 210 / 255f, 60 / 255f, 1f));
        font.draw(batch, "Sampaguita: " + gold, sx + 20f, BaseScreen.VIRTUAL_HEIGHT - 140f + 14f);
        font.setColor(new Color(180 / 255f, 220 / 255f, 140 / 255f, 1f));
        font.draw(batch, "Score: " + score, sx + 20f, BaseScreen.VIRTUAL_HEIGHT - 180f + 14f);

        font.setColor(new Color(200 / 255f, 200 / 255f, 200 / 255f, 1f));
        font.getData().setScale(0.9f);
        font.draw(batch, "TOWERS", sx + 20f, BaseScreen.VIRTUAL_HEIGHT - 250f + 11f);

        drawTowerSlotContent(mandirigmaImg, "Mandirigma", "Cost: 75g", sx, 270f, "mandirigma");
        drawTowerSlotContent(lantakaImg, "Lantaka Cannon", "Cost: 150g", sx, 410f, "lantaka");
        drawTowerSlotContent(bantayImg, "Bantay-Bantayan", "Cost: 100g", sx, 550f, "bantay");

        drawStartWaveButtonText(sx);

        if (selectedTowerType != null) {
            font.setColor(new Color(100 / 255f, 220 / 255f, 100 / 255f, 1f));
            font.getData().setScale(0.75f);
            font.draw(batch, "Click map to place", sx + 20f, BaseScreen.VIRTUAL_HEIGHT - 740f + 10f);
            font.draw(batch, "Right-click to cancel", sx + 20f, BaseScreen.VIRTUAL_HEIGHT - 760f + 10f);
            font.getData().setScale(1f);
        }
        font.getData().setScale(1f);
        batch.end();

        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(new Color(180 / 255f, 140 / 255f, 40 / 255f, 1f));
        sr.line(sx, 0, sx, BaseScreen.VIRTUAL_HEIGHT);
        drawTowerSlotBorder(sx, 270f, "mandirigma");
        drawTowerSlotBorder(sx, 410f, "lantaka");
        drawTowerSlotBorder(sx, 550f, "bantay");
        drawStartWaveButtonBorder(sx);
        sr.end();

        batch.begin();
        batch.draw(mandirigmaImg, sx + 25f, BaseScreen.VIRTUAL_HEIGHT - (270f + 90f), 80, 80);
        batch.draw(lantakaImg, sx + 25f, BaseScreen.VIRTUAL_HEIGHT - (410f + 90f), 80, 80);
        batch.draw(bantayImg, sx + 25f, BaseScreen.VIRTUAL_HEIGHT - (550f + 90f), 80, 80);
        batch.end();
    }

    private void drawTowerSlotBg(boolean ignored, float sx, float y, String type) {
        float btnW = SIDEBAR_WIDTH - 40f, btnH = 110f, x = sx + 20f;
        boolean selected = type.equals(selectedTowerType);
        sr.setColor(selected ? new Color(60 / 255f, 50 / 255f, 20 / 255f, 220 / 255f)
                : new Color(25 / 255f, 20 / 255f, 10 / 255f, 200 / 255f));
        sr.rect(x, BaseScreen.VIRTUAL_HEIGHT - y - btnH, btnW, btnH);
    }

    private void drawTowerSlotBorder(float sx, float y, String type) {
        float btnW = SIDEBAR_WIDTH - 40f, btnH = 110f, x = sx + 20f;
        boolean selected = type.equals(selectedTowerType);
        sr.setColor(selected ? new Color(220 / 255f, 180 / 255f, 60 / 255f, 1f)
                : new Color(120 / 255f, 90 / 255f, 30 / 255f, 150 / 255f));
        sr.rect(x, BaseScreen.VIRTUAL_HEIGHT - y - btnH, btnW, btnH);
    }

    private void drawTowerSlotContent(Texture img, String name, String cost, float sx, float y, String type) {
        float x = sx + 20f;
        font.setColor(new Color(220 / 255f, 200 / 255f, 140 / 255f, 1f));
        font.getData().setScale(0.85f);
        font.draw(batch, name, x + 100f, BaseScreen.VIRTUAL_HEIGHT - y - 40f + 12f);
        font.setColor(new Color(180 / 255f, 180 / 255f, 180 / 255f, 1f));
        font.getData().setScale(0.7f);
        font.draw(batch, cost, x + 100f, BaseScreen.VIRTUAL_HEIGHT - y - 65f + 10f);
    }

    private void drawStartWaveButtonBg(float sx) {
        float btnX = sx + 20f, btnY = 800f, btnW = SIDEBAR_WIDTH - 40f, btnH = 80f;
        boolean canStart = !waveManager.isWaveInProgress() && !waveManager.isAllWavesComplete()
                && state == State.PLAYING;
        sr.setColor(canStart ? new Color(30 / 255f, 80 / 255f, 30 / 255f, 220 / 255f)
                : new Color(40 / 255f, 40 / 255f, 40 / 255f, 180 / 255f));
        sr.rect(btnX, BaseScreen.VIRTUAL_HEIGHT - btnY - btnH, btnW, btnH);
    }

    private void drawStartWaveButtonBorder(float sx) {
        float btnX = sx + 20f, btnY = 800f, btnW = SIDEBAR_WIDTH - 40f, btnH = 80f;
        boolean canStart = !waveManager.isWaveInProgress() && !waveManager.isAllWavesComplete()
                && state == State.PLAYING;
        sr.setColor(canStart ? new Color(80 / 255f, 200 / 255f, 80 / 255f, 1f)
                : new Color(100 / 255f, 100 / 255f, 100 / 255f, 1f));
        sr.rect(btnX, BaseScreen.VIRTUAL_HEIGHT - btnY - btnH, btnW, btnH);
    }

    private void drawStartWaveButtonText(float sx) {
        float btnX = sx + 20f, btnY = 800f, btnW = SIDEBAR_WIDTH - 40f, btnH = 80f;
        boolean canStart = !waveManager.isWaveInProgress() && !waveManager.isAllWavesComplete()
                && state == State.PLAYING;

        String label;
        if (waveManager.isAllWavesComplete()) {
            label = "All Waves Done!";
        } else if (prepPhase) {
            label = "Starting in " + (prepTimer / 60 + 1) + "s...";
        } else if (waitingForNextWave) {
            label = "Next wave in " + (waveDelayTimer / 60 + 1) + "s...";
        } else if (waveManager.isWaveInProgress()) {
            label = "Wave in Progress...";
        } else {
            label = "Start Wave " + (waveManager.getCurrentWave() + 1);
        }

        font.setColor(canStart ? new Color(150 / 255f, 1f, 150 / 255f, 1f) : new Color(120 / 255f, 120 / 255f, 120 / 255f, 1f));
        font.getData().setScale(1f);
        layout.setText(font, label);
        font.draw(batch, label, btnX + (btnW - layout.width) / 2f,
                BaseScreen.VIRTUAL_HEIGHT - btnY - (btnH + layout.height) / 2f);
    }

    private void drawPauseOverlay() {
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(new Color(0, 0, 0, 0.7f));
        sr.rect(0, 0, BaseScreen.VIRTUAL_WIDTH, BaseScreen.VIRTUAL_HEIGHT);

        float x = (BaseScreen.VIRTUAL_WIDTH - OVERLAY_BTN_W) / 2f;
        float startY = 420f;
        for (int i = 0; i < 4; i++) {
            float topY = startY + OVERLAY_SPACING * i;
            sr.setColor(new Color(30 / 255f, 25 / 255f, 15 / 255f, 220 / 255f));
            sr.rect(x, BaseScreen.VIRTUAL_HEIGHT - topY - OVERLAY_BTN_H, OVERLAY_BTN_W, OVERLAY_BTN_H);
        }
        sr.end();

        batch.begin();
        drawCentered("PAUSED", BaseScreen.VIRTUAL_WIDTH / 2f, 300f, Color.WHITE, 2f);
        String[] options = { "Resume", "Restart", "Main Menu", "Exit Game" };
        for (int i = 0; i < options.length; i++) {
            float topY = startY + OVERLAY_SPACING * i;
            drawCentered(options[i], BaseScreen.VIRTUAL_WIDTH / 2f, topY + OVERLAY_BTN_H / 2f + 10f, Color.WHITE, 1.2f);
        }
        batch.end();
    }

    private void drawEndOverlay(String title, String subtitle) {
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(new Color(0, 0, 0, 0.75f));
        sr.rect(0, 0, BaseScreen.VIRTUAL_WIDTH, BaseScreen.VIRTUAL_HEIGHT);
        float x = (BaseScreen.VIRTUAL_WIDTH - OVERLAY_BTN_W) / 2f;
        sr.setColor(new Color(30 / 255f, 25 / 255f, 15 / 255f, 220 / 255f));
        sr.rect(x, BaseScreen.VIRTUAL_HEIGHT - 600f - OVERLAY_BTN_H, OVERLAY_BTN_W, OVERLAY_BTN_H);
        sr.end();

        batch.begin();
        drawCentered(title, BaseScreen.VIRTUAL_WIDTH / 2f, 380f, Color.WHITE, 2f);
        drawCentered(subtitle, BaseScreen.VIRTUAL_WIDTH / 2f, 460f, new Color(0.85f, 0.85f, 0.85f, 1f), 1.2f);
        drawCentered("Continue", BaseScreen.VIRTUAL_WIDTH / 2f, 600f + OVERLAY_BTN_H / 2f + 10f, Color.WHITE, 1.2f);
        batch.end();
    }

    private void drawCentered(String text, float centerX, float topY, Color color, float scale) {
        font.setColor(color);
        font.getData().setScale(scale);
        layout.setText(font, text);
        font.draw(batch, text, centerX - layout.width / 2f, BaseScreen.VIRTUAL_HEIGHT - topY + layout.height / 2f);
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
        gameMap.dispose();
        TextureCache.disposeAll();
    }
}
