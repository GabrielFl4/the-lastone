package net.aqualoco.thelastone.insomnia;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.UUID;

public record InsomniaState(
        int daysWithoutSleep,
        int phase,
        long lastSeenGameTime,
        long huntCooldownUntil,
        Optional<UUID> activeEntityUuid,
        long lastRestGameTime
) {
    public static final InsomniaState EMPTY = new InsomniaState(0, 0, 0, 0, Optional.empty(), 0);

    public static final Codec<InsomniaState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("daysWithoutSleep").orElse(0).forGetter(InsomniaState::daysWithoutSleep),
            Codec.INT.fieldOf("phase").orElse(0).forGetter(InsomniaState::phase),
            Codec.LONG.fieldOf("lastSeenGameTime").orElse(0L).forGetter(InsomniaState::lastSeenGameTime),
            Codec.LONG.fieldOf("huntCooldownUntil").orElse(0L).forGetter(InsomniaState::huntCooldownUntil),
            Codec.STRING
                    .optionalFieldOf("activeEntityUuid")
                    .xmap(opt -> opt.map(UUID::fromString), opt -> opt.map(UUID::toString))
                    .forGetter(InsomniaState::activeEntityUuid),
            Codec.LONG.fieldOf("lastRestGameTime").orElse(0L).forGetter(InsomniaState::lastRestGameTime)
    ).apply(instance, InsomniaState::new));

    public static InsomniaState empty() {
        return EMPTY;
    }

    public InsomniaState withDaysWithoutSleep(int days) {
        return new InsomniaState(days, this.phase, this.lastSeenGameTime, this.huntCooldownUntil, this.activeEntityUuid, this.lastRestGameTime);
    }

    public InsomniaState withPhase(int phase) {
        return new InsomniaState(this.daysWithoutSleep, clampPhase(phase), this.lastSeenGameTime, this.huntCooldownUntil, this.activeEntityUuid, this.lastRestGameTime);
    }

    public InsomniaState withLastSeenGameTime(long tick) {
        return new InsomniaState(this.daysWithoutSleep, this.phase, Math.max(0, tick), this.huntCooldownUntil, this.activeEntityUuid, this.lastRestGameTime);
    }

    public InsomniaState withHuntCooldownUntil(long tick) {
        return new InsomniaState(this.daysWithoutSleep, this.phase, this.lastSeenGameTime, Math.max(0, tick), this.activeEntityUuid, this.lastRestGameTime);
    }

    public InsomniaState withActiveEntity(UUID uuid) {
        return new InsomniaState(this.daysWithoutSleep, this.phase, this.lastSeenGameTime, this.huntCooldownUntil, Optional.ofNullable(uuid), this.lastRestGameTime);
    }

    public InsomniaState clearedActiveEntity() {
        return new InsomniaState(this.daysWithoutSleep, this.phase, this.lastSeenGameTime, this.huntCooldownUntil, Optional.empty(), this.lastRestGameTime);
    }

    public InsomniaState withLastRestGameTime(long tick) {
        return new InsomniaState(this.daysWithoutSleep, this.phase, this.lastSeenGameTime, this.huntCooldownUntil, this.activeEntityUuid, Math.max(0, tick));
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
