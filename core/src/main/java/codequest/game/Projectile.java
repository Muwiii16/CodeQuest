package codequest.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Affine2;
import codequest.screens.BaseScreen;

/** Port of the Swing prototype's Projectile. Rotation around the projectile's
 *  own center used a translated/rotated Graphics2D copy; the LibGDX equivalent
 *  is an Affine2 transform drawn via Batch#draw(TextureRegion, Affine2). */
public abstract class Projectile extends GameEntity {

    private final double speedX;
    private final double speedY;
    private final int damage;
    private final int spriteSize;
    private final boolean isAOE;
    private final double aoeRadius;
    private final Enemy target;
    private double angle;

    public Projectile(double x, double y, Enemy target, int damage,
            double projectileSpeed, int spriteSize,
            boolean isAOE, double aoeRadius, int frameDelay) {
        super(x, y, 1, frameDelay);
        this.target = target;
        this.damage = damage;
        this.spriteSize = spriteSize;
        this.isAOE = isAOE;
        this.aoeRadius = aoeRadius;

        double dx = target.getX() - x;
        double dy = target.getY() - y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double sx = 0, sy = 0;
        if (dist > 0) {
            sx = (dx / dist) * projectileSpeed;
            sy = (dy / dist) * projectileSpeed;
        }
        this.speedX = sx;
        this.speedY = sy;
    }

    @Override
    public Projectile update() {
        if (!isAlive())
            return null;

        if (!target.isAlive()) {
            setAlive(false);
            return null;
        }

        double dx = target.getX() - getX();
        double dy = target.getY() - getY();
        double dist = Math.sqrt(dx * dx + dy * dy);
        // Screen-space y grows downward, so flip dy for the on-screen rotation angle.
        angle = Math.atan2(-dy, dx);

        double projectileSpeed = Math.sqrt(speedX * speedX + speedY * speedY);

        if (dist <= projectileSpeed) {
            setX(target.getX());
            setY(target.getY());
            onHit();
            setAlive(false);
        } else {
            setX(getX() + (dx / dist) * projectileSpeed);
            setY(getY() + (dy / dist) * projectileSpeed);
        }

        updateAnimation();
        return null;
    }

    public abstract void onHit();

    @Override
    public void draw(Batch batch) {
        if (!isAlive())
            return;
        Texture[] frames = getFrames();
        if (frames == null || frames.length == 0)
            return;
        Texture img = frames[getCurrentFrame()];
        if (img == null)
            return;

        float size = spriteSize;
        float topY = (float) getY();
        float worldX = (float) getX();
        float worldY = BaseScreen.VIRTUAL_HEIGHT - topY;

        Affine2 transform = new Affine2();
        transform.setToTrnRotScl(worldX, worldY, (float) Math.toDegrees(angle), 1f, 1f);
        transform.translate(-size / 2f, -size / 2f);
        batch.draw(new TextureRegion(img), size, size, transform);
    }

    protected void loadSprite(String assetPath) {
        try {
            Texture img = TextureCache.get(assetPath);
            setFrames(new Texture[] { img });
        } catch (Exception e) {
            Gdx.app.error("Projectile", "Could not load projectile sprite: " + assetPath, e);
        }
    }

    public int getDamage() {
        return damage;
    }

    public boolean isAOE() {
        return isAOE;
    }

    public double getAoeRadius() {
        return aoeRadius;
    }

    public Enemy getTarget() {
        return target;
    }

    public int getSpriteSize() {
        return spriteSize;
    }
}
