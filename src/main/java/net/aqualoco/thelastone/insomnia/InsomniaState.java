package net.aqualoco.thelastone.insomnia;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

public record InsomniaState(
        int daysWithoutSleep, // armazenado para debug; derivado de timeOfDay
        int phase,
        long lastSeenTimeOfDay,
        long huntCooldownUntilTimeOfDay,
        Optional<UUID> activeEntityUuid,
        long lastRestTimeOfDay,
        long pendingRestResetUntilTimeOfDay
) {
    public static final InsomniaState EMPTY = new InsomniaState(0, 0, 0, 0, Optional.empty(), -1, -1);

    public static final Codec<InsomniaState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("daysWithoutSleep").orElse(0).forGetter(InsomniaState::daysWithoutSleep),
            Codec.INT.fieldOf("phase").orElse(0).forGetter(InsomniaState::phase),
            Codec.LONG.fieldOf("lastSeenTimeOfDay").orElse(0L).forGetter(InsomniaState::lastSeenTimeOfDay),
            Codec.LONG.fieldOf("huntCooldownUntilTimeOfDay").orElse(0L).forGetter(InsomniaState::huntCooldownUntilTimeOfDay),
            Codec.STRING
                    .optionalFieldOf("activeEntityUuid")
                    .xmap(opt -> opt.map(UUID::fromString), opt -> opt.map(UUID::toString))
                    .forGetter(InsomniaState::activeEntityUuid),
            Codec.LONG.fieldOf("lastRestTimeOfDay").orElse(-1L).forGetter(InsomniaState::lastRestTimeOfDay),
            Codec.LONG.fieldOf("pendingRestResetUntilTimeOfDay").orElse(-1L).forGetter(InsomniaState::pendingRestResetUntilTimeOfDay)
    ).apply(instance, InsomniaState::new));

    public static InsomniaState empty() {
        return EMPTY;
    }

    public InsomniaState withDaysWithoutSleep(int days) {
        return new InsomniaState(days, this.phase, this.lastSeenTimeOfDay, this.huntCooldownUntilTimeOfDay, this.activeEntityUuid, this.lastRestTimeOfDay, this.pendingRestResetUntilTimeOfDay);
    }

    public InsomniaState withPhase(int phase) {
        return new InsomniaState(this.daysWithoutSleep, clampPhase(phase), this.lastSeenTimeOfDay, this.huntCooldownUntilTimeOfDay, this.activeEntityUuid, this.lastRestTimeOfDay, this.pendingRestResetUntilTimeOfDay);
    }

    public InsomniaState withLastSeenTimeOfDay(long tick) {
        return new InsomniaState(this.daysWithoutSleep, this.phase, Math.max(0, tick), this.huntCooldownUntilTimeOfDay, this.activeEntityUuid, this.lastRestTimeOfDay, this.pendingRestResetUntilTimeOfDay);
    }

    public InsomniaState withHuntCooldownUntilTimeOfDay(long tick) {
        return new InsomniaState(this.daysWithoutSleep, this.phase, this.lastSeenTimeOfDay, Math.max(0, tick), this.activeEntityUuid, this.lastRestTimeOfDay, this.pendingRestResetUntilTimeOfDay);
    }

    public InsomniaState withActiveEntity(UUID uuid) {
        return new InsomniaState(this.daysWithoutSleep, this.phase, this.lastSeenTimeOfDay, this.huntCooldownUntilTimeOfDay, Optional.ofNullable(uuid), this.lastRestTimeOfDay, this.pendingRestResetUntilTimeOfDay);
    }

    public InsomniaState clearedActiveEntity() {
        return new InsomniaState(this.daysWithoutSleep, this.phase, this.lastSeenTimeOfDay, this.huntCooldownUntilTimeOfDay, Optional.empty(), this.lastRestTimeOfDay, this.pendingRestResetUntilTimeOfDay);
    }

    public InsomniaState withLastRestTimeOfDay(long tick) {
        return new InsomniaState(this.daysWithoutSleep, this.phase, this.lastSeenTimeOfDay, this.huntCooldownUntilTimeOfDay, this.activeEntityUuid, Math.max(-1, tick), this.pendingRestResetUntilTimeOfDay);
    }

    public InsomniaState withPendingRestResetUntilTimeOfDay(long tick) {
        return new InsomniaState(this.daysWithoutSleep, this.phase, this.lastSeenTimeOfDay, this.huntCooldownUntilTimeOfDay, this.activeEntityUuid, this.lastRestTimeOfDay, Math.max(-1, tick));
    }

    public boolean hasActiveEntity() {
        return this.activeEntityUuid.isPresent();
    }

    private static int clampPhase(int phase) {
        if (phase < 0) return 0;
        if (phase > 3) return 3;
        return phase;
    }
}
