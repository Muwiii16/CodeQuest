package codequest.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import codequest.screens.BaseScreen;

/**
 * Port of the Swing prototype's GameEntity. x/y stay in the same top-left,
 * y-grows-downward pixel space GameMap/Tower/Enemy used (matching BaseScreen's
 * VIRTUAL_WIDTH/HEIGHT design surface) so the movement/targeting math carries
 * over unchanged; only the two draw methods flip into LibGDX's bottom-up space.
 *
 * The original drew a sprite and its HP bar in a single Graphics2D pass.
 * LibGDX can't interleave textured and shape drawing in one batch, so that's
 * now two calls: drawSprite (SpriteBatch) and drawHpBar (ShapeRenderer), each
 * called during their own begin/end block by the screen that owns them.
 */
public abstract class GameEntity {

    private double x;
    private double y;
    private int hp;
    private int maxHp;
    private boolean alive;
    private Texture[] frames;
    private int currentFrame;
    private int frameTimer;
    private int frameDelay;

    public GameEntity(double x, double y, int hp, int frameDelay) {
        this.x = x;
        this.y = y;
        this.hp = hp;
        this.maxHp = hp;
        this.alive = true;
        this.currentFrame = 0;
        this.frameTimer = 0;
        this.frameDelay = frameDelay;
    }

    public abstract Projectile update();

    /** Sprite-only draw, called inside the owning screen's SpriteBatch begin/end block. */
    public abstract void draw(Batch batch);

    protected void updateAnimation() {
        if (frames == null || frames.length == 0)
            return;
        frameTimer++;
        if (frameTimer >= frameDelay) {
            frameTimer = 0;
            currentFrame = (currentFrame + 1) % frames.length;
        }
    }

    protected void drawSprite(Batch batch, float width, float height) {
        if (frames == null || frames.length == 0)
            return;
        Texture frame = frames[currentFrame];
        if (frame == null)
            return;
        float drawX = (float) x - width / 2f;
        float topY = (float) y - height / 2f;
        float drawY = BaseScreen.VIRTUAL_HEIGHT - topY - height;
        batch.draw(frame, drawX, drawY, width, height);
    }

    protected void drawHpBar(ShapeRenderer sr, float width) {
        float barW = width;
        float barH = 6f;
        float barX = (float) x - width / 2f;
        float barTopY = (float) y - width / 2f - 10f;
        float barY = BaseScreen.VIRTUAL_HEIGHT - barTopY - barH;

        sr.setColor(new Color(60 / 255f, 0, 0, 1f));
        sr.rect(barX, barY, barW, barH);

        float ratio = (float) hp / maxHp;
        Color hpColor = ratio > 0.5f ? new Color(0, 200 / 255f, 0, 1f)
                : ratio > 0.25f ? new Color(220 / 255f, 180 / 255f, 0, 1f)
                        : new Color(200 / 255f, 0, 0, 1f);
        sr.setColor(hpColor);
        sr.rect(barX, barY, barW * ratio, barH);
    }

    public void takeDamage(int damage) {
        hp -= damage;
        if (hp <= 0) {
            hp = 0;
            alive = false;
        }
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public boolean isAlive() {
        return alive;
    }

    public Texture[] getFrames() {
        return frames;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public void setFrames(Texture[] frames) {
        this.frames = frames;
    }

    public void setFrameDelay(int frameDelay) {
        this.frameDelay = frameDelay;
    }
}
