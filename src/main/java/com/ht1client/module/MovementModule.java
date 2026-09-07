package com.ht1client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;

import java.util.Random;

public class MovementModule {

    private static final Random RANDOM = new Random();

    private static final double STOP_DISTANCE = 2.5;
    private static final double STRAFE_RADIUS = 3.5;

    // How often (in ticks) the strafe direction flips, and how much it varies -
    // real players don't hold A or D for a fixed rhythm, they tap somewhat irregularly.
    private static final int STRAFE_MIN_TICKS = 4;
    private static final int STRAFE_MAX_TICKS = 9;

    // W-tap pattern: brief forward releases instead of holding forward solid,
    // which is what makes constant-hold movement read as bot-like on camera.
    private static final int TAP_ON_MIN_TICKS = 3;
    private static final int TAP_ON_MAX_TICKS = 6;
    private static final int TAP_OFF_MIN_TICKS = 1;
    private static final int TAP_OFF_MAX_TICKS = 2;

    private static int strafeDirection = 1;
    private static long nextStrafeFlipTick = -1;

    private static boolean tapForwardOn = true;
    private static long nextTapChangeTick = -1;

    public static void updateMovement(MinecraftClient client, Entity target, boolean justGotHit) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        long tick = client.world.getTime();

        // Reacting to getting hit reads as far more "alive" than a fixed pattern -
        // force an immediate strafe-direction flip instead of waiting for the
        // next scheduled flip, like a player instinctively repositioning.
        if (justGotHit) {
            strafeDirection = -strafeDirection;
            nextStrafeFlipTick = tick + STRAFE_MIN_TICKS + RANDOM.nextInt(STRAFE_MAX_TICKS - STRAFE_MIN_TICKS + 1);
        }

        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        boolean shouldApproach = dist > STOP_DISTANCE;
        boolean shouldStrafe = dist <= STRAFE_RADIUS;

        // --- Forward movement: tap in irregular bursts instead of a solid hold ---
        if (shouldApproach) {
            if (nextTapChangeTick < 0) {
                scheduleNextTap(tick);
            }
            if (tick >= nextTapChangeTick) {
                tapForwardOn = !tapForwardOn;
                scheduleNextTap(tick);
            }
            player.input.movementForward = tapForwardOn ? 1f : 0f;
        } else {
            player.input.movementForward = 0f;
            nextTapChangeTick = -1; // reset so next approach starts fresh
        }

        // --- Strafe: irregular direction flips instead of a smooth sine wave ---
        if (shouldStrafe) {
            if (nextStrafeFlipTick < 0) {
                scheduleNextStrafeFlip(tick);
            }
            if (tick >= nextStrafeFlipTick) {
                strafeDirection = -strafeDirection;
                scheduleNextStrafeFlip(tick);
            }
            player.input.movementSideways = strafeDirection;
        } else {
            player.input.movementSideways = 0f;
            nextStrafeFlipTick = -1;
        }

        // --- Jump timing: hop right around attack range rather than on a fixed
        // clock, so it reads as combat movement instead of terrain-clearing ---
        boolean nearAttackRange = dist <= STOP_DISTANCE + 1.0;
        boolean cooldownNearlyReady = player.getAttackCooldownProgress(0.5f) > 0.85f;
        if (nearAttackRange && cooldownNearlyReady && RANDOM.nextInt(6) == 0) {
            player.jump();
        } else if (player.horizontalCollision && tick % 20 == 0) {
            // fallback: still clear ledges/slabs if genuinely stuck
            player.jump();
        }
    }

    private static void scheduleNextTap(long tick) {
        int duration = tapForwardOn
                ? TAP_ON_MIN_TICKS + RANDOM.nextInt(TAP_ON_MAX_TICKS - TAP_ON_MIN_TICKS + 1)
                : TAP_OFF_MIN_TICKS + RANDOM.nextInt(TAP_OFF_MAX_TICKS - TAP_OFF_MIN_TICKS + 1);
        nextTapChangeTick = tick + duration;
    }

    private static void scheduleNextStrafeFlip(long tick) {
        int duration = STRAFE_MIN_TICKS + RANDOM.nextInt(STRAFE_MAX_TICKS - STRAFE_MIN_TICKS + 1);
        nextStrafeFlipTick = tick + duration;
    }

    public static void stop(ClientPlayerEntity player) {
        player.input.movementForward = 0f;
        player.input.movementSideways = 0f;
        nextStrafeFlipTick = -1;
        nextTapChangeTick = -1;
    }
}
