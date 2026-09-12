package codequest.game.enemies;

import codequest.game.Enemy;
import codequest.game.Projectile;

import java.awt.Point;
import java.util.List;

public class Kapre extends Enemy {

    private static final int BASE_HP = 250;
    private static final double BASE_SPEED = 1.0;
    private static final int REWARD = 15;
    private static final int DAMAGE = 5;
    private static final int FRAME_DELAY = 8;
    private static final int SPRITE_SIZE = 80;

    private final boolean immuneToSlow;
    private final boolean immuneToStun;

    public Kapre(double x, double y, List<Point> waypoints, String difficulty) {
        super(x, y, scaleHp(difficulty), BASE_SPEED, REWARD, DAMAGE,
                FRAME_DELAY, waypoints, SPRITE_SIZE);
        this.immuneToSlow = true;
        this.immuneToStun = true;
        loadDirectionalFrames("images/gameplay/Kapre", "Kapre");
    }

    private static int scaleHp(String difficulty) {
        switch (difficulty) {
            case "easy":
                return 200;
            case "hard":
                return 350;
            default:
                return BASE_HP;
        }
    }

    // Anito's Resolve — immune to slow and stun
    @Override
    public void applySpecialAbility(Object target) {
    }

    @Override
    public Projectile update() {
        return super.update();
    }

    public boolean isImmuneToSlow() {
        return immuneToSlow;
    }

    public boolean isImmuneToStun() {
        return immuneToStun;
    }
}
