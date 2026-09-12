package codequest.game.enemies;

import codequest.game.Enemy;
import codequest.game.Projectile;

import java.awt.Point;
import java.util.List;
import java.util.Random;

public class Tikbalang extends Enemy {

    private static final int BASE_HP = 85;
    private static final double BASE_SPEED = 2.5;
    private static final int REWARD = 10;
    private static final int DAMAGE = 2;
    private static final int FRAME_DELAY = 4;
    private static final int SPRITE_SIZE = 64;

    private static final double DODGE_CHANCE = 0.30;
    private final Random random;

    public Tikbalang(double x, double y, List<Point> waypoints, String difficulty) {
        super(x, y, scaleHp(difficulty), scaleSpeed(difficulty), REWARD, DAMAGE,
                FRAME_DELAY, waypoints, SPRITE_SIZE);
        this.random = new Random();
        loadDirectionalFrames("images/gameplay/Tikbalang", "Tikbalang");
    }

    private static int scaleHp(String difficulty) {
        switch (difficulty) {
            case "easy":
                return 70;
            case "hard":
                return 120;
            default:
                return BASE_HP;
        }
    }

    private static double scaleSpeed(String difficulty) {
        switch (difficulty) {
            case "easy":
                return BASE_SPEED * 0.9;
            case "hard":
                return BASE_SPEED * 1.15;
            default:
                return BASE_SPEED;
        }
    }

    // Swift Shadow — 30% chance to dodge single target projectiles
    public boolean attemptDodge() {
        return random.nextDouble() < DODGE_CHANCE;
    }

    @Override
    public Projectile update() {
        return super.update();
    }
}
