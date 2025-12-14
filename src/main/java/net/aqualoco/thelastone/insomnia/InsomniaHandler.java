package net.aqualoco.thelastone.insomnia;

import net.aqualoco.thelastone.ModAttachments;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;

public final class InsomniaHandler {

    private InsomniaHandler() {
    }

    public static void init() {
        // sem eventos por enquanto; fazemos o reset durante o tick quando o player realmente dorme
    }

    public static InsomniaState getState(ServerPlayerEntity player) {
        return player.getAttachedOrCreate(ModAttachments.INSOMNIA_STATE);
    }

    public static InsomniaState refreshRestState(ServerPlayerEntity player) {
        long worldTime = player.getWorld().getTime();
        InsomniaState current = getState(player);

        long baseRest = current.lastRestGameTime() > 0 ? current.lastRestGameTime() : worldTime;
        long lastRest = baseRest;

        // Consideramos "dormiu" apenas se ficou deitado tempo suficiente
        if (player.isSleeping() && player.getSleepTimer() >= 80) {
            discardActiveEntity(player, current);
            lastRest = worldTime;
        }

        long ticksSinceRest = Math.max(0, worldTime - lastRest);
        int days = (int) (ticksSinceRest / 24000L);
        int phase = computePhase(days);
        InsomniaState updated = new InsomniaState(days, phase, current.lastSeenGameTime(), current.huntCooldownUntil(), current.activeEntityUuid(), lastRest);
        player.setAttached(ModAttachments.INSOMNIA_STATE, updated);
        return updated;
    }

    public static void resetForSleep(ServerPlayerEntity player) {
        // Mantido para chamadas manuais; não usado por eventos automáticos
        discardActiveEntity(player, getState(player));
        long now = player.getWorld().getTime();
        player.setAttached(ModAttachments.INSOMNIA_STATE, InsomniaState.empty().withLastRestGameTime(now));
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

    private static void discardActiveEntity(ServerPlayerEntity player, InsomniaState state) {
        state.activeEntityUuid().ifPresent(uuid -> {
            ServerWorld world = player.getServerWorld();
            var entity = world.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        });
    }
}
