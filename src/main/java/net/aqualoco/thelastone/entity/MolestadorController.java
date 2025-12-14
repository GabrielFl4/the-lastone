package net.aqualoco.thelastone.entity;

import net.aqualoco.thelastone.ModAttachments;
import net.aqualoco.thelastone.insomnia.InsomniaHandler;
import net.aqualoco.thelastone.insomnia.InsomniaState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

public final class MolestadorController {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final double FOV_DOT_THRESHOLD = 0.8660254; // cos(30deg)
    private static final int MAX_SPAWN_ATTEMPTS = 40;

    private MolestadorController() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(MolestadorController::tickWorld);
    }

    private static void tickWorld(ServerWorld world) {
        if ((world.getTime() % CHECK_INTERVAL_TICKS) != 0) {
            return;
        }

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || player.getAbilities().invulnerable) {
                continue;
            }

            handlePlayer(world, player);
        }
    }

    private static void handlePlayer(ServerWorld world, ServerPlayerEntity player) {
        InsomniaState state = InsomniaHandler.refreshRestState(player);
        state = validateActiveEntity(world, player, state);

        if (state.phase() <= 0) {
            return;
        }

        if (state.phase() == 1 && world.isDay()) {
            return;
        }

        if (state.hasActiveEntity()) {
            return;
        }

        long now = world.getTime();
        if (now < state.huntCooldownUntil()) {
            return;
        }

        Range range = rangeForPhase(state.phase());
        Vec3d spawnPos = findSpawnPos(world, player, range.min(), range.max());

        if (spawnPos == null) {
            // fallback: wait a bit before trying again
            pushCooldown(player, state, now + 100L);
            return;
        }

        MolestadorEntity mob = new MolestadorEntity(ModEntities.MOLESTADOR, world);

        float yaw = getFacingYaw(spawnPos, player.getPos());
        float pitch = getFacingPitch(spawnPos, player.getPos());

        mob.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, yaw, pitch);
        mob.setOwner(player);
        mob.setPhase(Math.max(1, state.phase()));

        if (world.spawnEntity(mob)) {
            long cooldownUntil = now + getCooldownTicks(world, state.phase());
            InsomniaState updated = new InsomniaState(
                    state.daysWithoutSleep(),
                    state.phase(),
                    now,
                    cooldownUntil,
                    Optional.of(mob.getUuid())
            );
            player.setAttached(ModAttachments.INSOMNIA_STATE, updated);
        }
    }

    private static InsomniaState validateActiveEntity(ServerWorld world, ServerPlayerEntity player, InsomniaState state) {
        if (!state.hasActiveEntity()) {
            return state;
        }

        UUID uuid = state.activeEntityUuid().orElse(null);
        if (uuid != null) {
            var entity = world.getEntity(uuid);
            if (entity instanceof MolestadorEntity molestador && !molestador.isRemoved()) {
                return state;
            }
        }

        InsomniaState cleared = state.clearedActiveEntity();
        player.setAttached(ModAttachments.INSOMNIA_STATE, cleared);
        return cleared;
    }

    private static void pushCooldown(ServerPlayerEntity player, InsomniaState state, long until) {
        if (until <= state.huntCooldownUntil()) {
            return;
        }

        InsomniaState updated = new InsomniaState(
                state.daysWithoutSleep(),
                state.phase(),
                state.lastSeenGameTime(),
                until,
                state.activeEntityUuid()
        );
        player.setAttached(ModAttachments.INSOMNIA_STATE, updated);
    }

    private static Range rangeForPhase(int phase) {
        return switch (phase) {
            case 1 -> new Range(30, 60);
            case 2 -> new Range(15, 35);
            default -> new Range(10, 20);
        };
    }

    private static int getCooldownTicks(ServerWorld world, int phase) {
        return switch (phase) {
            case 1 -> world.getRandom().nextBetween(20 * 60 * 3, 20 * 60 * 6);
            case 2 -> world.getRandom().nextBetween(20 * 60, 20 * 60 * 3);
            default -> world.getRandom().nextBetween(20 * 30, 20 * 90);
        };
    }

    private static Vec3d findSpawnPos(ServerWorld world, ServerPlayerEntity player, int minRange, int maxRange) {
        Vec3d eyePos = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f).normalize();

        for (int i = 0; i < MAX_SPAWN_ATTEMPTS; i++) {
            double distance = MathHelper.lerp(world.getRandom().nextDouble(), minRange, maxRange);
            double angle = world.getRandom().nextDouble() * Math.PI * 2.0;
            double dx = Math.cos(angle) * distance;
            double dz = Math.sin(angle) * distance;
            double dy = world.getRandom().nextBetween(-2, 2);

            BlockPos pos = BlockPos.ofFloored(player.getX() + dx, player.getY() + dy, player.getZ() + dz);
            if (!world.isInBuildLimit(pos)) {
                continue;
            }

            if (!world.isAir(pos) || !world.isAir(pos.up())) {
                continue;
            }

            if (!world.getBlockState(pos.down()).isSolid()) {
                continue;
            }

            Vec3d candidateCenter = Vec3d.ofCenter(pos);
            Vec3d dirToCandidate = candidateCenter.subtract(eyePos).normalize();
            double dot = look.dotProduct(dirToCandidate);
            if (dot >= FOV_DOT_THRESHOLD) {
                continue;
            }

            if (!hasCover(world, pos)) {
                continue;
            }

            return candidateCenter;
        }

        return null;
    }

    private static boolean hasCover(World world, BlockPos pos) {
        return world.getBlockState(pos.north()).isSolid() ||
                world.getBlockState(pos.south()).isSolid() ||
                world.getBlockState(pos.east()).isSolid() ||
                world.getBlockState(pos.west()).isSolid();
    }

    private static float getFacingYaw(Vec3d from, Vec3d to) {
        Vec3d diff = to.subtract(from);
        return (float) (MathHelper.atan2(diff.z, diff.x) * (180F / Math.PI)) - 90.0f;
    }

    private static float getFacingPitch(Vec3d from, Vec3d to) {
        Vec3d diff = to.subtract(from);
        double horizontal = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        return (float) (-(MathHelper.atan2(diff.y, horizontal) * (180F / Math.PI)));
    }

    private record Range(int min, int max) {
    }
}
