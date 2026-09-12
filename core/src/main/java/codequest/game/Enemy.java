package codequest.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.awt.Point;
import java.util.List;

/** Port of the Swing prototype's Enemy. Frame loading swaps ImageIO/File for
 *  LibGDX's internal Texture loading; all movement/targeting math is unchanged. */
public abstract class Enemy extends GameEntity {

    private final List<Point> waypoints;
    private int currentWaypoint;
    private double speed;
    private final int reward;
    private final int damage;
    private boolean reachedEnd;
    private final int spriteSize;

    private Texture[] downFrames;
    private Texture[] leftFrames;
    private Texture[] rightFrames;
    private Texture[] upFrames;
    private String currentDirection = "D";

    public Enemy(double x, double y, int hp, double speed,
            int reward, int damage, int frameDelay,
            List<Point> waypoints, int spriteSize) {
        super(x, y, hp, frameDelay);
        this.speed = speed;
        this.reward = reward;
        this.damage = damage;
        this.waypoints = waypoints;
        this.currentWaypoint = 0;
        this.reachedEnd = false;
        this.spriteSize = spriteSize;
    }

    /** folderPath is an assets/-relative path, e.g. "images/gameplay/Kapre". */
    protected void loadDirectionalFrames(String folderPath, String prefix) {
        downFrames = loadFrameSet(folderPath, prefix + "_D", 3);
        leftFrames = loadFrameSet(folderPath, prefix + "_L", 3);
        rightFrames = loadFrameSet(folderPath, prefix + "_R", 3);
        upFrames = loadFrameSet(folderPath, prefix + "_U", 3);

        setFrames(downFrames);
    }

    private Texture[] loadFrameSet(String folder, String prefix, int count) {
        Texture[] frames = new Texture[count];
        for (int i = 0; i < count; i++) {
            try {
                frames[i] = TextureCache.get(folder + "/" + prefix + (i + 1) + ".png");
            } catch (Exception e) {
                Gdx.app.error("Enemy", "Could not load: " + prefix + (i + 1) + ".png", e);
            }
        }
        return frames;
    }

    @Override
    public Projectile update() {
        if (!isAlive() || reachedEnd)
            return null;
        moveAlongPath();
        updateAnimation();
        return null;
    }

    private void moveAlongPath() {
        if (currentWaypoint >= waypoints.size()) {
            reachedEnd = true;
            setAlive(false);
            return;
        }

        Point target = waypoints.get(currentWaypoint);
        double tx = target.x;
        double ty = target.y;
        double dx = tx - getX();
        double dy = ty - getY();
        double dist = Math.sqrt(dx * dx + dy * dy);

        updateDirection(dx, dy);

        if (dist <= speed) {
            setX(tx);
            setY(ty);
            currentWaypoint++;
        } else {
            setX(getX() + (dx / dist) * speed);
            setY(getY() + (dy / dist) * speed);
        }
    }

    private void updateDirection(double dx, double dy) {
        String newDir;
        if (Math.abs(dx) > Math.abs(dy)) {
            newDir = dx > 0 ? "R" : "L";
        } else {
            newDir = dy > 0 ? "D" : "U";
        }

        if (!newDir.equals(currentDirection)) {
            currentDirection = newDir;
            switch (currentDirection) {
                case "D":
                    setFrames(downFrames);
                    break;
                case "L":
                    setFrames(leftFrames);
                    break;
                case "R":
                    setFrames(rightFrames);
                    break;
                case "U":
                    setFrames(upFrames);
                    break;
            }
        }
    }

    @Override
    public void draw(Batch batch) {
        if (!isAlive())
            return;
        drawSprite(batch, spriteSize, spriteSize);
    }

    /** Called separately from draw() since HP bars need a ShapeRenderer, not a Batch. */
    public void drawHpBar(ShapeRenderer sr) {
        if (!isAlive())
            return;
        drawHpBar(sr, spriteSize);
    }

    public void applySpecialAbility(Object target) {
    }

    public double getSpeed() {
        return speed;
    }

    public int getReward() {
        return reward;
    }

    public int getDamage() {
        return damage;
    }

    public boolean hasReachedEnd() {
        return reachedEnd;
    }

    public int getSpriteSize() {
        return spriteSize;
    }

    public int getCurrentWaypoint() {
        return currentWaypoint;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public void setReachedEnd(boolean r) {
        this.reachedEnd = r;
    }
}
