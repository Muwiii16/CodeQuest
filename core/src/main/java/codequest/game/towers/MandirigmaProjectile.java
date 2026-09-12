package codequest.game.towers;

import codequest.game.Enemy;
import codequest.game.Projectile;
import codequest.game.enemies.Tikbalang;

public class MandirigmaProjectile extends Projectile {

    public MandirigmaProjectile(double x, double y, Enemy target, int damage) {
        super(x, y, target, damage, 12.0, 28, false, 0, 2);
        loadSprite("images/gameplay/mandirigma/projectile.png");
    }

    @Override
    public void onHit() {
        Enemy target = getTarget();
        if (!target.isAlive())
            return;

        // Check Tikbalang dodge
        if (target instanceof Tikbalang) {
            Tikbalang t = (Tikbalang) target;
            if (t.attemptDodge()) {
                System.out.println("Tikbalang dodged!");
                return;
            }
        }
        target.takeDamage(getDamage());
    }
}
