package net.aqualoco.thelastone.insomnia;

import net.aqualoco.thelastone.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;

import java.util.Optional;

public final class InsomniaHandler {

    private InsomniaHandler() {
    }

    public static void init() {
        EntitySleepEvents.STOP_SLEEPING.register((living, sleepingPosition) -> {
            if (living instanceof ServerPlayerEntity player) {
                markPendingSleepReset(player);
            }
        });
    }

    public static InsomniaState getState(ServerPlayerEntity player) {
        return player.getAttachedOrCreate(ModAttachments.INSOMNIA_STATE);
    }

    public static InsomniaState refreshRestState(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return getState(player);

        ServerWorld overworld = server.getOverworld();
        long now = overworld.getTimeOfDay();

        InsomniaState before = getState(player);
        long lastRest = resolveRestTime(before.lastRestTimeOfDay(), now);
        long pending = before.pendingRestResetUntilTimeOfDay();
        Optional<java.util.UUID> active = before.activeEntityUuid();
        long lastSeen = before.lastSeenTimeOfDay();
        long cooldown = before.huntCooldownUntilTimeOfDay();

        boolean dirty = false;

        // reset pendente ao amanhecer
        if (pending > 0 && now >= pending && isDayish(overworld, now)) {
            discardActiveEntity(player, before);
            lastRest = now;
            pending = -1;
            active = Optional.empty();
            lastSeen = 0;
            cooldown = 0;
            dirty = true;
        }

        // init do lastRest
        if (lastRest < 0) {
            lastRest = now;
            dirty = true;
        }

        long ticksSinceRest = Math.max(0, now - lastRest);
        int days = (int) (ticksSinceRest / 24000L);
        int phase = computePhase(days);

        InsomniaState updated = new InsomniaState(
                days,
                phase,
                lastSeen,
                cooldown,
                active,
                lastRest,
                pending
        );

        if (dirty || !updated.equals(before)) {
            player.setAttached(ModAttachments.INSOMNIA_STATE, updated);
        }

        return updated;
    }

    public static void resetForSleep(ServerPlayerEntity player) {
        markPendingSleepReset(player);
    }

    public static int computePhase(int daysWithoutSleep) {
        if (daysWithoutSleep >= 5) return 3;
        if (daysWithoutSleep >= 4) return 2;
        if (daysWithoutSleep >= 3) return 1;
        return 0;
    }

    public static int getDaysSinceRest(ServerPlayerEntity player) {
        int ticksSinceRest = player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));
        return ticksSinceRest / 24000;
    }

    private static void markPendingSleepReset(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerWorld overworld = server.getOverworld();
        long now = overworld.getTimeOfDay();

        InsomniaState current = getState(player);
        long pending = now + 40; // ~2s depois
        InsomniaState updated = current.withPendingRestResetUntilTimeOfDay(pending);
        if (!updated.equals(current)) {
            player.setAttached(ModAttachments.INSOMNIA_STATE, updated);
        }
    }

    private static void discardActiveEntity(ServerPlayerEntity player, InsomniaState state) {
        state.activeEntityUuid().ifPresent(uuid -> {
            ServerWorld world = player.getServerWorld();
            var entity = world.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        });
    }

    private static long clampTime(long value, long now) {
        if (value < -1) return -1;
        return value;
    }

    private static long resolveRestTime(long value, long now) {
        if (value < 0) return value;
        return clampTime(value, now);
    }

    private static boolean isDayish(ServerWorld overworld, long now) {
        long dayTime = now % 24000L;
        return overworld.isDay() || dayTime < 1000L;
    }
}
