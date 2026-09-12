package codequest.game.towers;

import codequest.game.Enemy;
import codequest.game.Projectile;
import codequest.game.Tower;
import codequest.game.enemies.Manananggal;

public class BantayBantayan extends Tower {

    private static final int DAMAGE = 25;
    private static final double RANGE = 350;
    private static final int ATTACK_SPEED = 72;
    private static final int COST = 100;
    private static final int SPRITE_SIZE = 85;
    private static final int FRAME_DELAY = 9;

    public BantayBantayan(double x, double y) {
        super(x, y, 999, DAMAGE, RANGE, ATTACK_SPEED, COST, SPRITE_SIZE, FRAME_DELAY);
        loadStance("images/gameplay/bantay");
    }

    // Prioritize flying enemies first
    @Override
    protected boolean selectTarget(Enemy newEnemy, Enemy currentTarget) {
        if (currentTarget == null)
            return true;
        boolean newIsFlying = newEnemy instanceof Manananggal;
        boolean currentIsFlying = currentTarget instanceof Manananggal;
        if (newIsFlying && !currentIsFlying)
            return true;
        return false;
    }

    @Override
    public Projectile attack(Enemy target) {
        int actualDamage = DAMAGE;
        // Flak Shot — 2x damage vs flying units
        if (target instanceof Manananggal) {
            actualDamage = DAMAGE * 2;
        }
        return new BantayProjectile(getX(), getY(), target, actualDamage);
    }
}
