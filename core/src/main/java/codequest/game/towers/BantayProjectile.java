package codequest.game.towers;

import codequest.game.Enemy;
import codequest.game.Projectile;

public class BantayProjectile extends Projectile {

    public BantayProjectile(double x, double y, Enemy target, int damage) {
        super(x, y, target, damage, 14.0, 28, false, 0, 2);
        loadSprite("images/gameplay/bantay/projectile.png");
    }

    @Override
    public void onHit() {
        Enemy target = getTarget();
        if (!target.isAlive())
            return;

        // Flak Shot ignores Tikbalang's evasion — always hits
        target.takeDamage(getDamage());
    }
}
