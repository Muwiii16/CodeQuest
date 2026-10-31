package codequest.screens;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WeaponRigTest {

    private static final float DT = 1f / 60f;
    private static final float AX = 600f;
    private static final float AY = 500f;

    private static WeaponRig rig() {
        return new WeaponRig(0, Color.WHITE, Color.WHITE);
    }

    private static void run(WeaponRig rig, float seconds) {
        for (float t = 0; t < seconds; t += DT) {
            rig.update(DT, AX, AY, 0f);
        }
    }

    @Test
    void itHoversNearTheStudentAndKeepsBobbingAroundTheSpotBesideThem() {
        WeaponRig rig = rig();
        run(rig, 3f);
        assertEquals(WeaponRig.State.HOVER, rig.state);
        assertEquals(AX + 62f, rig.x, 4f);
        assertEquals(AY - 74f, rig.y, 8f);
    }

    @Test
    void guardingAimsTowardTheProjectileAndMovesAwayFromTheRestSpot() {
        WeaponRig rig = rig();
        run(rig, 1f);
        float restX = rig.x;
        for (int i = 0; i < 60; i++) {
            rig.guard(AX - 300f, AY - 40f, 0.5f); // projectile coming from the left
            rig.update(DT, AX, AY, 0f);
        }
        assertEquals(WeaponRig.State.GUARD, rig.state);
        assertTrue(rig.x < restX - 40f, "the weapon should have swung to the projectile's side");
        assertTrue(Math.abs(Math.abs(rig.rot) - 180f) < 40f, "it should point roughly left, got " + rig.rot);
    }

    @Test
    void aStrikeReachesTheProjectileCallsBackOnceAndReturnsToHovering() {
        WeaponRig rig = rig();
        run(rig, 0.5f);
        AtomicInteger hits = new AtomicInteger();
        float[] hitAt = new float[2];
        rig.guard(AX - 250f, AY - 20f, 0.7f);
        rig.update(DT, AX, AY, 0f);
        rig.strike(AX - 250f, AY - 20f, () -> {
            hits.incrementAndGet();
            hitAt[0] = rig.x;
            hitAt[1] = rig.y;
        });

        boolean sawWindup = false;
        boolean sawStrike = false;
        boolean sawRecover = false;
        float total = WeaponRig.WINDUP_TIME + WeaponRig.STRIKE_TIME + WeaponRig.IMPACT_TIME + WeaponRig.RECOVER_TIME;
        for (float t = 0; t < total + 0.2f; t += DT) {
            rig.update(DT, AX, AY, 0f);
            sawWindup |= rig.state == WeaponRig.State.WINDUP;
            sawStrike |= rig.state == WeaponRig.State.STRIKE;
            sawRecover |= rig.state == WeaponRig.State.RECOVER;
        }
        assertTrue(sawWindup && sawStrike && sawRecover);
        assertEquals(1, hits.get());
        assertEquals(AX - 250f, hitAt[0], 2f);
        assertEquals(AY - 20f, hitAt[1], 2f);
        assertEquals(WeaponRig.State.HOVER, rig.state);
    }

    @Test
    void aGuardCallCannotInterruptAStrikeInProgress() {
        WeaponRig rig = rig();
        run(rig, 0.3f);
        rig.strike(AX - 200f, AY, () -> { });
        rig.update(DT, AX, AY, 0f);
        rig.guard(AX - 100f, AY, 1f);
        rig.update(DT, AX, AY, 0f);
        assertNotEquals(WeaponRig.State.GUARD, rig.state);
    }

    @Test
    void aWrongAnswerMakesTheWeaponStumbleThenRecover() {
        WeaponRig rig = rig();
        run(rig, 0.5f);
        float restY = rig.y;
        rig.stumble();
        run(rig, 0.3f);
        assertEquals(WeaponRig.State.STUMBLE, rig.state);
        assertTrue(rig.y > restY + 15f, "it should droop below its resting height");
        run(rig, WeaponRig.STUMBLE_TIME);
        assertEquals(WeaponRig.State.HOVER, rig.state);
    }

    @Test
    void theStrikeNeverMovesTheWeaponFartherThanTheProjectile() {
        WeaponRig rig = rig();
        run(rig, 0.3f);
        rig.strike(AX - 220f, AY - 10f, () -> { });
        float closest = Float.MAX_VALUE;
        for (int i = 0; i < 60; i++) {
            rig.update(DT, AX, AY, 0f);
            closest = Math.min(closest, Math.abs(rig.x - (AX - 220f)));
        }
        assertTrue(closest < 3f);
    }
}
