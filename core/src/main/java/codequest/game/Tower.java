package codequest.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import codequest.screens.BaseScreen;

import java.util.List;

/** Port of the Swing prototype's Tower. Range-circle drawing moves from
 *  Graphics2D fillOval/drawOval to ShapeRenderer, flipped into LibGDX's
 *  bottom-up space the same way GameEntity's sprite/HP-bar drawing is. */
public abstract class Tower extends GameEntity {

    private int damage;
    private double range;
    private final int attackSpeed;
    private int attackTimer;
    private final int cost;
    private final int spriteSize;
    private Enemy target;
    private boolean slow;

    public Tower(double x, double y, int hp, int damage, double range,
            int attackSpeed, int cost, int spriteSize, int frameDelay) {
        super(x, y, hp, frameDelay);
        this.damage = damage;
        this.range = range;
        this.attackSpeed = attackSpeed;
        this.attackTimer = 0;
        this.cost = cost;
        this.spriteSize = spriteSize;
        this.target = null;
        this.slow = false;
    }

    /** folderPath is an assets/-relative path, e.g. "images/gameplay/mandirigma". */
    protected void loadStance(String folderPath) {
        try {
            Texture stance = TextureCache.get(folderPath + "/stance.png");
            setFrames(new Texture[] { stance });
        } catch (Exception e) {
            Gdx.app.error("Tower", "Could not load stance: " + folderPath, e);
        }
    }

    @Override
    public Projectile update() {
        updateAnimation();
        if (target != null && (!target.isAlive() || !isInRange(target))) {
            target = null;
        }
        attackTimer++;
        if (attackTimer >= attackSpeed && target != null) {
            attackTimer = 0;
            return attack(target);
        }
        return null;
    }

    public void findTarget(List<Enemy> enemies) {
        target = null;
        Enemy bestTarget = null;

        for (Enemy e : enemies) {
            if (!e.isAlive())
                continue;
            if (distanceTo(e) > range)
                continue;

            if (bestTarget == null) {
                bestTarget = e;
            } else if (selectTarget(e, bestTarget)) {
                bestTarget = e;
            }
        }
        target = bestTarget;
    }

    protected boolean selectTarget(Enemy newEnemy, Enemy currentTarget) {
        return true;
    }

    public abstract Projectile attack(Enemy target);

    @Override
    public void draw(Batch batch) {
        if (!isAlive())
            return;
        drawSprite(batch, spriteSize, spriteSize);
    }

    /** Called separately from draw() since the range ring needs a ShapeRenderer, not a Batch. */
    public void drawRange(ShapeRenderer sr) {
        float cx = (float) getX();
        float topY = (float) getY();
        float cy = BaseScreen.VIRTUAL_HEIGHT - topY;
        float r = (float) range;

        sr.setColor(new Color(1f, 1f, 1f, 40 / 255f));
        sr.circle(cx, cy, r, 48);
    }

    protected boolean isInRange(Enemy e) {
        return distanceTo(e) <= range;
    }

    protected double distanceTo(Enemy e) {
        double dx = e.getX() - getX();
        double dy = e.getY() - getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public int getDamage() {
        return damage;
    }

    public double getRange() {
        return range;
    }

    public int getAttackSpeed() {
        return attackSpeed;
    }

    public int getCost() {
        return cost;
    }

    public int getSpriteSize() {
        return spriteSize;
    }

    public Enemy getTarget() {
        return target;
    }

    public boolean isSlow() {
        return slow;
    }

    public void setDamage(int damage) {
        this.damage = damage;
    }

    public void setRange(double range) {
        this.range = range;
    }

    public void setTarget(Enemy target) {
        this.target = target;
    }

    public void setSlow(boolean slow) {
        this.slow = slow;
    }
}
