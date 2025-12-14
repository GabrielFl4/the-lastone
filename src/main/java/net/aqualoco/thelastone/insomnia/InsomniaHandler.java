package net.aqualoco.thelastone.insomnia;

import net.aqualoco.thelastone.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;

public final class InsomniaHandler {

    private InsomniaHandler() {
    }

    public static void init() {
        EntitySleepEvents.STOP_SLEEPING.register((living, sleepingPosition) -> {
            if (living instanceof ServerPlayerEntity player) {
                resetForSleep(player);
            }
        });
    }

    public static InsomniaState getState(ServerPlayerEntity player) {
        return player.getAttachedOrCreate(ModAttachments.INSOMNIA_STATE);
    }

    public static InsomniaState refreshRestState(ServerPlayerEntity player) {
        int days = getDaysSinceRest(player);
        int phase = computePhase(days);
        InsomniaState current = getState(player);
        InsomniaState updated = new InsomniaState(days, phase, current.lastSeenGameTime(), current.huntCooldownUntil(), current.activeEntityUuid());

        if (!updated.equals(current)) {
            player.setAttached(ModAttachments.INSOMNIA_STATE, updated);
        }

        return updated;
    }

    public static void resetForSleep(ServerPlayerEntity player) {
        InsomniaState current = getState(player);
        current.activeEntityUuid().ifPresent(uuid -> {
            ServerWorld world = player.getServerWorld();
            var entity = world.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        });

        player.setAttached(ModAttachments.INSOMNIA_STATE, InsomniaState.empty());
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
}
