package com.ht1client.module;

import com.ht1client.HT1Config;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public class AimModule {

    private static final Random RANDOM = new Random();

    // Small per-tick jitter so the camera doesn't move with robotic precision
    private static final float JITTER_DEGREES = 0.4f;

    // How many ticks ahead to lead a moving target. Tuned to roughly match
    // travel time at typical PvP range - too high and it overshoots dodges,
    // too low and it just aims at their current spot like a static target.
    private static final double LEAD_TICKS = 2.0;

    public static void updateAim(ClientPlayerEntity player, Entity target) {
        // Predict where the target will be a couple ticks from now based on
        // its current velocity, so strafing/dodging targets actually get hit
        // instead of the aim always trailing behind real movement.
        double predictedX = target.getX() + target.getVelocity().x * LEAD_TICKS;
        double predictedZ = target.getZ() + target.getVelocity().z * LEAD_TICKS;

        double dx = predictedX - player.getX();
        double dz = predictedZ - player.getZ();
        // Aim slightly below eye height, real players rarely aim at the exact eye point
        double dy = (target.getEyeY() - 0.15) - player.getEyeY();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float desiredYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));

        float yawDelta = MathHelper.wrapDegrees(desiredYaw - player.getYaw());
        float pitchDelta = desiredPitch - player.getPitch();

        yawDelta = MathHelper.clamp(yawDelta, -HT1Config.maxDegreesPerTick, HT1Config.maxDegreesPerTick);
        pitchDelta = MathHelper.clamp(pitchDelta, -HT1Config.maxDegreesPerTick, HT1Config.maxDegreesPerTick);

        float jitterYaw = (RANDOM.nextFloat() - 0.5f) * JITTER_DEGREES;
        float jitterPitch = (RANDOM.nextFloat() - 0.5f) * JITTER_DEGREES;

        player.setYaw(player.getYaw() + yawDelta * HT1Config.turnSmoothing + jitterYaw);
        player.setPitch(MathHelper.clamp(
                player.getPitch() + pitchDelta * HT1Config.turnSmoothing + jitterPitch,
                -90f, 90f
        ));
    }
}
