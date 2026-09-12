package codequest.game.towers;

import codequest.game.Enemy;
import codequest.game.Projectile;
import codequest.game.Tower;
import codequest.game.enemies.Manananggal;

public class LantakaCannon extends Tower {

    private static final int DAMAGE = 75;
    private static final double RANGE = 175;
    private static final int ATTACK_SPEED = 120;
    private static final int COST = 150;
    private static final int SPRITE_SIZE = 90;
    private static final int FRAME_DELAY = 10;
    private static final double AOE_RADIUS = 160;

    public LantakaCannon(double x, double y) {
        super(x, y, 999, DAMAGE, RANGE, ATTACK_SPEED, COST, SPRITE_SIZE, FRAME_DELAY);
        loadStance("images/gameplay/lantaka");
    }

    // Prioritize strongest enemy (most HP)
    @Override
    protected boolean selectTarget(Enemy newEnemy, Enemy currentTarget) {
        if (currentTarget == null)
            return true;
        // Skip flying enemies — can't hit them
        if (newEnemy instanceof Manananggal)
            return false;
        return newEnemy.getHp() > currentTarget.getHp();
    }

    @Override
    public Projectile attack(Enemy target) {
        // Don't attack flying units
        if (target instanceof Manananggal)
            return null;
        return new LantakaProjectile(getX(), getY(), target, DAMAGE, AOE_RADIUS);
    }
}
