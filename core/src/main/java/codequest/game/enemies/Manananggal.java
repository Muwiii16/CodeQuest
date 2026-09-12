package codequest.game.enemies;

import codequest.game.Enemy;
import codequest.game.Projectile;

import java.awt.Point;
import java.util.List;

public class Manananggal extends Enemy {

    private static final int BASE_HP = 45;
    private static final double BASE_SPEED = 1.5;
    private static final int REWARD = 12;
    private static final int DAMAGE = 3;
    private static final int FRAME_DELAY = 6;
    private static final int SPRITE_SIZE = 72;

    private final boolean isFlying;

    public Manananggal(double x, double y, List<Point> waypoints, String difficulty) {
        super(x, y, scaleHp(difficulty), scaleSpeed(difficulty), REWARD, DAMAGE,
                FRAME_DELAY, waypoints, SPRITE_SIZE);
        this.isFlying = true;
        loadDirectionalFrames("images/gameplay/Manananggal", "Manananggal");
    }

    private static int scaleHp(String difficulty) {
        switch (difficulty) {
            case "easy":
                return 35;
            case "hard":
                return 65;
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

    // Skybound — flying unit, ignores ground towers
    public boolean isFlying() {
        return isFlying;
    }

    @Override
    public Projectile update() {
        return super.update();
    }
}
