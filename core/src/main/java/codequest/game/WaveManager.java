package codequest.game;

import codequest.game.enemies.Kapre;
import codequest.game.enemies.Manananggal;
import codequest.game.enemies.Tikbalang;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/** Direct port of the Swing prototype's WaveManager — pure game logic, no rendering, unchanged. */
public class WaveManager {

    private final int stageNumber;
    private final String difficulty;
    private final List<Point> waypoints;
    private final List<Enemy> enemies;

    private int currentWave;
    private final int totalWaves;
    private int spawnTimer;
    private int spawnIndex;
    private List<String> spawnQueue;
    private boolean waveInProgress;
    private boolean allWavesComplete;

    public WaveManager(int stageNumber, String difficulty,
            List<Point> waypoints, List<Enemy> enemies) {
        this.stageNumber = stageNumber;
        this.difficulty = difficulty;
        this.waypoints = waypoints;
        this.enemies = enemies;
        this.currentWave = 0;
        this.totalWaves = 5;
        this.spawnTimer = 0;
        this.spawnIndex = 0;
        this.spawnQueue = new ArrayList<>();
        this.waveInProgress = false;
        this.allWavesComplete = false;
    }

    public void update() {
        if (!waveInProgress || allWavesComplete)
            return;

        if (spawnIndex < spawnQueue.size()) {
            spawnTimer++;

            String nextEnemyType = spawnQueue.get(spawnIndex);
            int requiredDelay = getSpawnDelay(nextEnemyType);

            if (spawnTimer >= requiredDelay) {
                spawnTimer = 0;
                spawnEnemy(nextEnemyType);
                spawnIndex++;
            }
        } else {
            waveInProgress = false;
            if (currentWave >= totalWaves) {
                allWavesComplete = true;
            }
        }
    }

    public void startNextWave() {
        if (waveInProgress || allWavesComplete)
            return;
        currentWave++;
        if (currentWave > totalWaves) {
            allWavesComplete = true;
            return;
        }
        spawnQueue = buildWaveQueue(currentWave);
        spawnIndex = 0;
        spawnTimer = 0;
        waveInProgress = true;
        System.out.println("Wave " + currentWave + " started!");
    }

    private List<String> buildWaveQueue(int wave) {
        List<String> queue = new ArrayList<>();
        switch (stageNumber) {
            case 1:
                buildStage1Wave(queue, wave);
                break;
            case 2:
                buildStage2Wave(queue, wave);
                break;
            case 3:
                buildStage3Wave(queue, wave);
                break;
        }
        return queue;
    }

    private void buildStage1Wave(List<String> queue, int wave) {
        switch (wave) {
            case 1:
                addEnemies(queue, "tikbalang", 5);
                break;
            case 2:
                addEnemies(queue, "tikbalang", 5);
                addEnemies(queue, "kapre", 2);
                break;
            case 3:
                addEnemies(queue, "tikbalang", 6);
                addEnemies(queue, "kapre", 3);
                addEnemies(queue, "manananggal", 2);
                break;
            case 4:
                addEnemies(queue, "kapre", 4);
                addEnemies(queue, "manananggal", 3);
                addEnemies(queue, "tikbalang", 4);
                break;
            case 5:
                addEnemies(queue, "kapre", 5);
                addEnemies(queue, "manananggal", 4);
                addEnemies(queue, "tikbalang", 6);
                break;
        }
    }

    private void buildStage2Wave(List<String> queue, int wave) {
        switch (wave) {
            case 1:
                addEnemies(queue, "tikbalang", 6);
                addEnemies(queue, "kapre", 2);
                break;
            case 2:
                addEnemies(queue, "tikbalang", 6);
                addEnemies(queue, "kapre", 3);
                addEnemies(queue, "manananggal", 2);
                break;
            case 3:
                addEnemies(queue, "kapre", 4);
                addEnemies(queue, "manananggal", 4);
                addEnemies(queue, "tikbalang", 5);
                break;
            case 4:
                addEnemies(queue, "kapre", 5);
                addEnemies(queue, "manananggal", 5);
                addEnemies(queue, "tikbalang", 6);
                break;
            case 5:
                addEnemies(queue, "kapre", 6);
                addEnemies(queue, "manananggal", 6);
                addEnemies(queue, "tikbalang", 8);
                break;
        }
    }

    private void buildStage3Wave(List<String> queue, int wave) {
        switch (wave) {
            case 1:
                addEnemies(queue, "kapre", 3);
                addEnemies(queue, "manananggal", 3);
                addEnemies(queue, "tikbalang", 6);
                break;
            case 2:
                addEnemies(queue, "kapre", 4);
                addEnemies(queue, "manananggal", 4);
                addEnemies(queue, "tikbalang", 8);
                break;
            case 3:
                addEnemies(queue, "kapre", 5);
                addEnemies(queue, "manananggal", 5);
                addEnemies(queue, "tikbalang", 8);
                break;
            case 4:
                addEnemies(queue, "kapre", 6);
                addEnemies(queue, "manananggal", 6);
                addEnemies(queue, "tikbalang", 10);
                break;
            case 5:
                addEnemies(queue, "kapre", 8);
                addEnemies(queue, "manananggal", 8);
                addEnemies(queue, "tikbalang", 10);
                break;
        }
    }

    private void addEnemies(List<String> queue, String type, int count) {
        for (int i = 0; i < count; i++)
            queue.add(type);
    }

    private void spawnEnemy(String type) {
        Point start = waypoints.get(0);
        Enemy e;
        switch (type) {
            case "kapre":
                e = new Kapre(start.x, start.y, waypoints, difficulty);
                break;
            case "tikbalang":
                e = new Tikbalang(start.x, start.y, waypoints, difficulty);
                break;
            case "manananggal":
                e = new Manananggal(start.x, start.y, waypoints, difficulty);
                break;
            default:
                e = new Tikbalang(start.x, start.y, waypoints, difficulty);
        }
        enemies.add(e);
    }

    public boolean isWaveCleared() {
        if (waveInProgress)
            return false;
        for (Enemy e : enemies) {
            if (e.isAlive())
                return false;
        }
        return true;
    }

    private int getSpawnDelay(String enemyType) {
        switch (enemyType) {
            case "tikbalang":
                return 40;
            case "kapre":
                return 160;
            case "manananggal":
                return 90;
            default:
                return 90;
        }
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public int getTotalWaves() {
        return totalWaves;
    }

    public boolean isWaveInProgress() {
        return waveInProgress;
    }

    public boolean isAllWavesComplete() {
        return allWavesComplete;
    }
}
