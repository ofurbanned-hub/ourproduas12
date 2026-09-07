package com.ht1client.module;

import com.ht1client.HT1Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

import java.util.Comparator;
import java.util.Random;
import java.util.stream.Stream;

public class CombatModule {

    private static final Random RANDOM = new Random();

    // Tracks the current target so a reaction delay only applies to *new* targets
    private static LivingEntity currentTarget = null;
    private static long targetAcquiredTick = -1;
    private static long reactionDelayTicks = 0;

    // Tracks damage taken so movement can react to being hit, not just chase blindly
    private static float lastHealth = -1f;

    public static void onClientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (!HT1Config.aiModeEnabled) {
            resetTargetState();
            return;
        }

        LivingEntity target = findNearestTarget(client);

        if (target == null) {
            resetTargetState();
            MovementModule.stop(client.player);
            return;
        }

        // New target acquired -> roll a human-like reaction delay before reacting
        if (target != currentTarget) {
            currentTarget = target;
            targetAcquiredTick = client.world.getTime();
            reactionDelayTicks = 1 + RANDOM.nextInt(3); // ~50-150ms at 20 tps
        }

        boolean reactionElapsed = (client.world.getTime() - targetAcquiredTick) >= reactionDelayTicks;
        if (!reactionElapsed) {
            return; // haven't "noticed" the target yet
        }

        boolean justGotHit = checkAndUpdateHealth(client.player);

        if (HT1Config.autoAimEnabled) {
            AimModule.updateAim(client.player, target);
        }

        if (HT1Config.autoMoveEnabled) {
            MovementModule.updateMovement(client, target, justGotHit);
        }

        if (HT1Config.autoAttackEnabled) {
            manageSprintAndAttack(client, target);
        }
    }

    /**
     * Vanilla Minecraft gives bonus knockback/damage on a hit landed while NOT
     * sprinting right after having sprinted in (a "sprint reset"). This isn't a
     * netcode trick - it's a real base-game combat mechanic good PvP players
     * use deliberately. We sprint while closing distance, then drop sprint for
     * one tick right before the swing lands.
     */
    private static void manageSprintAndAttack(MinecraftClient client, LivingEntity target) {
        ClientPlayerEntity player = client.player;
        float cooldownProgress = player.getAttackCooldownProgress(0.5f);
        double distance = player.distanceTo(target);
        boolean inRange = distance <= HT1Config.reachDistance;

        boolean aboutToSwing = cooldownProgress >= 0.9f && inRange;

        if (aboutToSwing) {
            player.setSprinting(false); // cancel sprint the moment before landing the hit
        } else if (distance > HT1Config.reachDistance * 0.75) {
            player.setSprinting(true); // sprint while closing distance otherwise
        }

        if (cooldownProgress >= 1.0f && inRange) {
            client.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
        }
    }

    /** Returns true the tick health drops, so movement can react to getting hit. */
    private static boolean checkAndUpdateHealth(ClientPlayerEntity player) {
        float health = player.getHealth();
        boolean dropped = lastHealth >= 0f && health < lastHealth;
        lastHealth = health;
        return dropped;
    }

    private static LivingEntity findNearestTarget(MinecraftClient client) {
        double range = HT1Config.reachDistance + 5.0;

        Stream<LivingEntity> hostiles = client.world.getEntitiesByClass(
                HostileEntity.class,
                client.player.getBoundingBox().expand(range),
                e -> e.isAlive() && client.player.canSee(e)
        ).stream().map(e -> (LivingEntity) e);

        Stream<LivingEntity> players = client.world.getEntitiesByClass(
                PlayerEntity.class,
                client.player.getBoundingBox().expand(range),
                p -> p.isAlive() && p != client.player && client.player.canSee(p)
        ).stream().map(p -> (LivingEntity) p);

        return Stream.concat(hostiles, players)
                .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)))
                .orElse(null);
    }

    private static void resetTargetState() {
        currentTarget = null;
        targetAcquiredTick = -1;
        reactionDelayTicks = 0;
        lastHealth = -1f;
    }
}
