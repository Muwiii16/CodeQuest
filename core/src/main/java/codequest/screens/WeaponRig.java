package codequest.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Interpolation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The floating weapon beside the student: how it hovers, aims at an incoming projectile, strikes it, or
 * stumbles when the answer was wrong. Every weapon moves the same way; rarer ones only look flashier
 * (a glow, orbiting sparks, drifting sparkles). The motion is plain easing math, so the weapon art is one
 * picture drawn pointing right with its grip at the left end.
 *
 * Coordinates are the game's layout coordinates: origin top-left, y down. Angles are degrees, 0 = pointing
 * right and positive = clockwise on screen.
 */
final class WeaponRig {

    enum State { HOVER, GUARD, WINDUP, STRIKE, IMPACT, RECOVER, STUMBLE }

    static final float WINDUP_TIME = 0.09f;
    static final float STRIKE_TIME = 0.13f;
    static final float IMPACT_TIME = 0.06f;
    static final float RECOVER_TIME = 0.28f;
    static final float STUMBLE_TIME = 0.6f;

    private static final float ANCHOR_DX = 62f;
    private static final float ANCHOR_DY = -74f;
    private static final float WEAPON_W = 120f;
    private static final float WEAPON_H = 45f;
    private static final float GRIP_X = 14f;
    private static final float GRIP_Y = 22.5f;

    private final int rank;
    private final Color tint;
    private final Color auraColor;
    private final Random random = new Random();

    State state = State.HOVER;
    float x;
    float y;
    float rot;

    private float stateTime;
    private float time;
    private boolean positioned;
    private float ax;
    private float ay;

    private float aimAngle;
    private float urgency;
    private float targetX;
    private float targetY;
    private float fromX;
    private float fromY;
    private float fromRot;
    private Runnable onImpact;

    private static final class Ghost {
        float x;
        float y;
        float rot;
        float age;
    }

    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float life;
        float maxLife;
        float size;
        Color color;
    }

    private static final class Shock {
        float x;
        float y;
        float age;
        Color color;
    }

    private final List<Ghost> ghosts = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<Shock> shocks = new ArrayList<>();
    private float emitTimer;

    /** rank: 0 common to 3 legendary; tint colors the weapon; auraColor is the rarity color of its glow. */
    WeaponRig(int rank, Color tint, Color auraColor) {
        this.rank = rank;
        this.tint = new Color(tint);
        this.auraColor = new Color(auraColor);
    }

    // ── commands from the game ───────────────────────────

    /** Called every frame while a projectile is flying: aim at it; urgency 0..1 grows as time runs out. */
    void guard(float projectileX, float projectileY, float urgency) {
        if (state == State.HOVER || state == State.GUARD || state == State.RECOVER) {
            if (state != State.GUARD) {
                state = State.GUARD;
                stateTime = 0f;
            }
            aimAngle = (float) Math.toDegrees(Math.atan2(projectileY - (ay - 50f), projectileX - ax));
            this.urgency = Math.max(0f, Math.min(1f, urgency));
        }
    }

    /** The answer was right: wind up, dash through the projectile at (px, py), then return. onImpact runs once at the hit. */
    void strike(float px, float py, Runnable onImpact) {
        aimAngle = (float) Math.toDegrees(Math.atan2(py - (ay - 50f), px - ax));
        targetX = px;
        targetY = py;
        this.onImpact = onImpact;
        enter(State.WINDUP);
    }

    /** The answer was wrong or too slow: the weapon droops and flickers, then recovers. */
    void stumble() {
        enter(State.STUMBLE);
    }

    private void enter(State next) {
        state = next;
        stateTime = 0f;
        fromX = x;
        fromY = y;
        fromRot = rot;
    }

    // ── effects ──────────────────────────────────────────

    /** A burst of sparks and a shockwave ring at a point (a projectile being hit). */
    void burst(float px, float py, Color color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            double a = random.nextDouble() * Math.PI * 2;
            float v = speed * (0.35f + random.nextFloat() * 0.65f);
            p.x = px;
            p.y = py;
            p.vx = (float) Math.cos(a) * v;
            p.vy = (float) Math.sin(a) * v;
            p.maxLife = 0.35f + random.nextFloat() * 0.35f;
            p.life = p.maxLife;
            p.size = 14f + random.nextFloat() * 16f;
            p.color = new Color(color);
            particles.add(p);
        }
        Shock s = new Shock();
        s.x = px;
        s.y = py;
        s.color = new Color(color);
        shocks.add(s);
    }

    // ── motion ───────────────────────────────────────────

    private static float approach(float current, float target, float rate, float dt) {
        return current + (target - current) * (1f - (float) Math.exp(-rate * dt));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Advances the weapon. (avatarX, avatarY) is where the student stands; walkTilt leans the weapon while walking. */
    void update(float dt, float avatarX, float avatarY, float walkTilt) {
        time += dt;
        stateTime += dt;
        ax = avatarX;
        ay = avatarY;
        float bob = (float) Math.sin(time * 3.1f) * 6f;
        float sway = (float) Math.sin(time * 2.2f) * 6f;
        float anchorX = ax + ANCHOR_DX;
        float anchorY = ay + ANCHOR_DY + bob;
        if (!positioned) {
            x = anchorX;
            y = anchorY;
            positioned = true;
        }
        float cos = (float) Math.cos(Math.toRadians(aimAngle));
        float sin = (float) Math.sin(Math.toRadians(aimAngle));

        switch (state) {
            case HOVER -> {
                x = approach(x, anchorX, 9f, dt);
                y = approach(y, anchorY, 9f, dt);
                rot = approach(rot, sway + walkTilt, 8f, dt);
            }
            case GUARD -> {
                x = approach(x, ax + cos * 85f, 12f, dt);
                y = approach(y, ay - 50f + sin * 55f, 12f, dt);
                rot = approach(rot, aimAngle - 8f + (float) Math.sin(time * (8f + 14f * urgency)) * 6f, 14f, dt);
            }
            case WINDUP -> {
                float t = Math.min(1f, stateTime / WINDUP_TIME);
                float e = Interpolation.pow2Out.apply(t);
                x = lerp(fromX, ax - cos * 38f, e);
                y = lerp(fromY, ay - 50f - sin * 20f, e);
                rot = lerp(fromRot, aimAngle - 60f, e);
                if (t >= 1f) {
                    enter(State.STRIKE);
                    fromRot = aimAngle - 60f;
                }
            }
            case STRIKE -> {
                float t = Math.min(1f, stateTime / STRIKE_TIME);
                float e = Interpolation.pow3Out.apply(t);
                x = lerp(fromX, targetX, e);
                y = lerp(fromY, targetY, e);
                rot = lerp(fromRot, aimAngle + 95f, e);
                addGhost();
                if (t >= 1f) {
                    state = State.IMPACT;
                    stateTime = 0f;
                    if (onImpact != null) {
                        Runnable r = onImpact;
                        onImpact = null;
                        r.run();
                    }
                }
            }
            case IMPACT -> {
                x = targetX + (float) Math.sin(time * 90f) * 3f;
                y = targetY;
                if (stateTime >= IMPACT_TIME) {
                    enter(State.RECOVER);
                }
            }
            case RECOVER -> {
                float t = Math.min(1f, stateTime / RECOVER_TIME);
                float e = Interpolation.swingOut.apply(t);
                x = lerp(fromX, anchorX, e);
                y = lerp(fromY, anchorY, e);
                rot = lerp(fromRot, 0f, e);
                if (t >= 1f) {
                    state = State.HOVER;
                    stateTime = 0f;
                }
            }
            case STUMBLE -> {
                float shake = (float) Math.sin(time * 40f) * 5f * Math.max(0f, 1f - stateTime / STUMBLE_TIME);
                x = approach(x, anchorX + shake, 6f, dt);
                y = approach(y, anchorY + 38f, 5f, dt);
                rot = approach(rot, 75f, 5f, dt);
                if (stateTime >= STUMBLE_TIME) {
                    state = State.HOVER;
                    stateTime = 0f;
                }
            }
        }

        for (int i = ghosts.size() - 1; i >= 0; i--) {
            Ghost g = ghosts.get(i);
            g.age += dt;
            if (g.age > 0.18f) {
                ghosts.remove(i);
            }
        }
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0f) {
                particles.remove(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy += 260f * dt;
            p.vx *= (1f - 2f * dt);
        }
        for (int i = shocks.size() - 1; i >= 0; i--) {
            Shock s = shocks.get(i);
            s.age += dt;
            if (s.age > 0.4f) {
                shocks.remove(i);
            }
        }
        if (rank >= 3 && state != State.STUMBLE) {
            emitTimer -= dt;
            if (emitTimer <= 0f) {
                emitTimer = 0.07f;
                Particle p = new Particle();
                p.x = centerX() + (random.nextFloat() - 0.5f) * 70f;
                p.y = centerY() + (random.nextFloat() - 0.5f) * 40f;
                p.vx = (random.nextFloat() - 0.5f) * 20f;
                p.vy = -30f - random.nextFloat() * 30f;
                p.maxLife = 0.6f;
                p.life = p.maxLife;
                p.size = 10f + random.nextFloat() * 10f;
                p.color = new Color(auraColor);
                particles.add(p);
            }
        }
    }

    private void addGhost() {
        Ghost g = new Ghost();
        g.x = x;
        g.y = y;
        g.rot = rot;
        ghosts.add(g);
    }

    /** The middle of the weapon in layout coordinates. */
    float centerX() {
        return x + (float) Math.cos(Math.toRadians(rot)) * (WEAPON_W * 0.45f);
    }

    float centerY() {
        return y + (float) Math.sin(Math.toRadians(rot)) * (WEAPON_W * 0.45f);
    }

    // ── drawing ──────────────────────────────────────────

    /** Draws the aura, afterimages, the weapon and the effects. Call between batch.begin() and batch.end(). */
    void draw(Batch batch, float screenHeight, Texture weapon, Texture glow, Texture spark, Texture ring) {
        float alpha = state == State.STUMBLE ? 0.55f + 0.25f * (float) Math.sin(time * 30f) : 1f;

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive: glows add light instead of covering
        if (rank >= 1) {
            float pulse = 1f + 0.08f * (float) Math.sin(time * (state == State.GUARD ? 4f + 10f * urgency : 3f));
            float size = (rank == 1 ? 120f : rank == 2 ? 170f : 230f) * pulse;
            float a = (rank == 1 ? 0.30f : rank == 2 ? 0.42f : 0.55f) * alpha;
            batch.setColor(auraColor.r, auraColor.g, auraColor.b, a);
            batch.draw(glow, centerX() - size / 2f, screenHeight - centerY() - size / 2f, size, size);
            if (rank >= 3) {
                float inner = size * 0.55f * (1f + 0.1f * (float) Math.sin(time * 7f));
                batch.setColor(1f, 0.95f, 0.7f, 0.5f * alpha);
                batch.draw(glow, centerX() - inner / 2f, screenHeight - centerY() - inner / 2f, inner, inner);
            }
        }
        if (rank >= 2) {
            int n = rank == 2 ? 3 : 6;
            for (int i = 0; i < n; i++) {
                double ang = time * (rank == 2 ? 2.0 : 2.8) + i * (Math.PI * 2 / n);
                float sx = centerX() + (float) Math.cos(ang) * (48f + rank * 8f);
                float sy = centerY() + (float) Math.sin(ang) * (30f + rank * 6f);
                float size = 18f + 6f * (float) Math.sin(time * 6f + i);
                batch.setColor(auraColor.r, auraColor.g, auraColor.b, 0.85f * alpha);
                batch.draw(spark, sx - size / 2f, screenHeight - sy - size / 2f, size, size);
            }
        }
        for (Shock s : shocks) {
            float t = s.age / 0.4f;
            float size = 40f + 260f * Interpolation.pow2Out.apply(t);
            batch.setColor(s.color.r, s.color.g, s.color.b, 0.8f * (1f - t));
            batch.draw(ring, s.x - size / 2f, screenHeight - s.y - size / 2f, size, size);
        }
        for (Particle p : particles) {
            float t = p.life / p.maxLife;
            batch.setColor(p.color.r, p.color.g, p.color.b, t);
            float size = p.size * (0.5f + 0.5f * t);
            batch.draw(spark, p.x - size / 2f, screenHeight - p.y - size / 2f, size, size);
        }
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        for (Ghost g : ghosts) {
            float t = 1f - g.age / 0.18f;
            batch.setColor(tint.r, tint.g, tint.b, 0.35f * t);
            drawSprite(batch, screenHeight, weapon, g.x, g.y, g.rot);
        }
        batch.setColor(tint.r, tint.g, tint.b, alpha);
        drawSprite(batch, screenHeight, weapon, x, y, rot);
        batch.setColor(Color.WHITE);
    }

    private static void drawSprite(Batch batch, float screenHeight, Texture weapon, float gx, float gy, float rotation) {
        batch.draw(weapon, gx - GRIP_X, screenHeight - gy - GRIP_Y, GRIP_X, GRIP_Y, WEAPON_W, WEAPON_H, 1f, 1f,
                -rotation, 0, 0, weapon.getWidth(), weapon.getHeight(), false, false);
    }
}
