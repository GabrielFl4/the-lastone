package net.aqualoco.thelastone.entity;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.tslat.smartbrainlib.api.SmartBrainOwner;
import net.tslat.smartbrainlib.api.core.BrainActivityGroup;
import net.tslat.smartbrainlib.api.core.SmartBrainProvider;
import net.tslat.smartbrainlib.api.core.behaviour.FirstApplicableBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.OneRandomBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.custom.look.LookAtTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.misc.Idle;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.MoveToWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.path.SetRandomWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.path.SetWalkTargetToAttackTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.InvalidateAttackTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.SetPlayerLookTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.SetRandomLookTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.TargetOrRetaliate;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import net.tslat.smartbrainlib.api.core.sensor.custom.GenericAttackTargetSensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.HurtBySensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyLivingEntitySensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyPlayersSensor;
import net.tslat.smartbrainlib.util.BrainUtil;

import java.util.Optional;
import java.util.List;

public class MolestadorEntity extends PathAwareEntity implements SmartBrainOwner<MolestadorEntity> {
    private static final TrackedData<Optional<LazyEntityReference<LivingEntity>>> OWNER_REF = DataTracker.registerData(MolestadorEntity.class, TrackedDataHandlerRegistry.LAZY_ENTITY_REFERENCE);
    private static final TrackedData<Integer> PHASE = DataTracker.registerData(MolestadorEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> REPOSITION_USED = DataTracker.registerData(MolestadorEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Byte> HUNT_STATE = DataTracker.registerData(MolestadorEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Integer> NEXT_ACTION_TICK = DataTracker.registerData(MolestadorEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> JITTER_SEED = DataTracker.registerData(MolestadorEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final int LIFETIME_TICKS = 200; // 10s
    private static final double TOO_CLOSE_SQUARED = 6 * 6;
    private static final int LOOK_HOLD_TICKS = 10;
    private static final int PHASE3_RUSH_DURATION = 60;
    private static final double HIT_RANGE = 2.5;
    private static final double REPOSITION_FOV_MAX_DOT = 0.90;
    private static final int MAX_BLINK_TELEPORTS = 2;

    private int lifeTicks;
    private int lookHoldTicks;
    private int blinkCount;
    private int blinkTargetCount = 1;

    private enum HuntState {
        IDLE((byte)0),
        STALK((byte)1),
        BLINK((byte)2),
        RUSH((byte)3),
        COOLDOWN((byte)4);

        private final byte id;

        HuntState(byte id) {
            this.id = id;
        }

        public byte id() {
            return this.id;
        }

        public static HuntState fromId(byte id) {
            for (HuntState state : values()) {
                if (state.id == id) {
                    return state;
                }
            }
            return IDLE;
        }
    }

    public MolestadorEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setAiDisabled(false);
        this.noClip = false;
        this.setNoGravity(false);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.FOLLOW_RANGE, 100.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(OWNER_REF, Optional.empty());
        builder.add(PHASE, 1);
        builder.add(REPOSITION_USED, false);
        builder.add(HUNT_STATE, HuntState.IDLE.id());
        builder.add(NEXT_ACTION_TICK, 0);
        builder.add(JITTER_SEED, 0);
    }

    @Override
    protected Brain.Profile<?> createBrainProfile() {
        return new SmartBrainProvider<>(this);
    }

    @Override
    protected void mobTick(ServerWorld world) {
        super.mobTick(world);
        tickBrain(this);

        this.lifeTicks++;

        PlayerEntity owner = getOwner();

        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }

        // manter olhar fixo no dono
        this.getLookControl().lookAt(owner, 100.0f, 100.0f);
        this.getNavigation().setCanSwim(true);

        if (checkHardLimits(owner)) {
            return;
        }

        boolean lookedTooLong = updateLookHold(owner);

        int phase = getPhase();
        if (phase >= 3) {
            tickPhaseThree(owner, lookedTooLong);
        } else if (phase == 2) {
            tickPhaseTwo(owner, lookedTooLong);
        } else {
            tickPhaseOne(owner, lookedTooLong);
        }
    }

    @Override
    protected void initGoals() {
        // sem AI vanilla; usamos SmartBrainLib
    }

    @Override
    public List<? extends ExtendedSensor<? extends MolestadorEntity>> getSensors() {
        return ObjectArrayList.of(
                new NearbyPlayersSensor<>(),
                new NearbyLivingEntitySensor<MolestadorEntity>().setPredicate((target, entity) -> target != entity),
                new GenericAttackTargetSensor<>(),
                new HurtBySensor<>());
    }

    @Override
    public BrainActivityGroup<? extends MolestadorEntity> getCoreTasks() {
        return BrainActivityGroup.coreTasks(
                new LookAtTarget<>(),
                new MoveToWalkTarget<>());
    }

    @Override
    public BrainActivityGroup<? extends MolestadorEntity> getIdleTasks() {
        return BrainActivityGroup.idleTasks(
                new FirstApplicableBehaviour<MolestadorEntity>(
                        new TargetOrRetaliate<MolestadorEntity>()
                                .attackablePredicate(target -> target instanceof PlayerEntity player
                                        && !player.getAbilities().creativeMode
                                        && !player.getAbilities().invulnerable),
                        new SetPlayerLookTarget<>(),
                        new SetRandomLookTarget<>()),
                new OneRandomBehaviour<>(
                        new SetRandomWalkTarget<>()
                                .speedModifier(0.6f),
                        new Idle<>()
                                .runFor(entity -> 20 + entity.getRandom().nextInt(40))));
    }

    @Override
    public BrainActivityGroup<? extends MolestadorEntity> getFightTasks() {
        return BrainActivityGroup.fightTasks(
                new InvalidateAttackTarget<MolestadorEntity>()
                        .startCondition(entity -> entity.getHuntState() == HuntState.RUSH),
                new SetWalkTargetToAttackTarget<MolestadorEntity>()
                        .speedMod(0.85f)
                        .closeEnoughDist((entity, target) -> 3)
                        .startCondition(entity -> entity.getHuntState() == HuntState.RUSH));
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(net.minecraft.entity.Entity entity) {
        // nada
    }

    @Override
    public boolean collidesWith(net.minecraft.entity.Entity other) {
        return false;
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return super.damage(world, source, amount);
    }

    public void setOwner(PlayerEntity player) {
        this.dataTracker.set(OWNER_REF, Optional.of(new LazyEntityReference<LivingEntity>(player)));
    }

    public PlayerEntity getOwner() {
        Optional<LazyEntityReference<LivingEntity>> ref = this.dataTracker.get(OWNER_REF);
        LivingEntity resolved = ref.map(lazy -> lazy.resolve(this.getWorld(), LivingEntity.class)).orElse(null);
        return resolved instanceof PlayerEntity player ? player : null;
    }

    public void setPhase(int phase) {
        int clamped = Math.max(1, Math.min(3, phase));
        this.dataTracker.set(PHASE, clamped);
        if (clamped >= 3) {
            setHuntState(HuntState.STALK);
            setNextActionTick(0);
            this.blinkCount = 0;
            this.blinkTargetCount = 1;
        } else {
            setHuntState(HuntState.IDLE);
            setNextActionTick(0);
        }
    }

    public int getPhase() {
        return this.dataTracker.get(PHASE);
    }

    private void setRepositionUsed(boolean value) {
        this.dataTracker.set(REPOSITION_USED, value);
    }

    private boolean hasRepositioned() {
        return this.dataTracker.get(REPOSITION_USED);
    }

    private HuntState getHuntState() {
        return HuntState.fromId(this.dataTracker.get(HUNT_STATE));
    }

    private void setHuntState(HuntState state) {
        this.dataTracker.set(HUNT_STATE, state.id());
    }

    private void setNextActionTick(int tick) {
        this.dataTracker.set(NEXT_ACTION_TICK, tick);
    }

    private int getNextActionTick() {
        return this.dataTracker.get(NEXT_ACTION_TICK);
    }

    public int getJitterSeed() {
        return this.dataTracker.get(JITTER_SEED);
    }

    private void bumpJitterSeed() {
        this.dataTracker.set(JITTER_SEED, getJitterSeed() + 1);
    }

    private boolean checkHardLimits(PlayerEntity owner) {
        double distSq = this.squaredDistanceTo(owner);

        if (distSq <= TOO_CLOSE_SQUARED || this.lifeTicks >= LIFETIME_TICKS) {
            discard();
            return true;
        }

        return false;
    }

    private boolean updateLookHold(PlayerEntity owner) {
        Vec3d fromOwner = this.getPos().subtract(owner.getCameraPosVec(1.0f)).normalize();
        double dot = owner.getRotationVec(1.0f).normalize().dotProduct(fromOwner);
        boolean canSee = owner.canSee(this);

        if (dot > 0.985 && canSee) {
            this.lookHoldTicks++;
        } else {
            this.lookHoldTicks = 0;
        }

        return this.lookHoldTicks >= LOOK_HOLD_TICKS;
    }

    private void tickPhaseOne(PlayerEntity owner, boolean lookedTooLong) {
        this.getNavigation().stop();
        this.getBrain().forget(MemoryModuleType.WALK_TARGET);

        if (lookedTooLong) {
            discard();
        }
    }

    private void tickPhaseTwo(PlayerEntity owner, boolean lookedTooLong) {
        this.getNavigation().stop();
        this.getBrain().forget(MemoryModuleType.WALK_TARGET);

        if (lookedTooLong) {
            if (!hasRepositioned()) {
                Vec3d avoid = this.getPos().subtract(owner.getPos()).normalize();
                if (tryReposition(owner, 10, 25, avoid)) {
                    setRepositionUsed(true);
                    bumpJitterSeed();
                    playGlitchEffect();
                    this.lookHoldTicks = 0;
                    return;
                }
            }

            discard();
        }
    }

    private void tickPhaseThree(PlayerEntity owner, boolean lookedTooLong) {
        HuntState state = getHuntState();
        int now = this.age;

        if (state == HuntState.IDLE) {
            setHuntState(HuntState.STALK);
            state = HuntState.STALK;
            setNextActionTick(now + this.random.nextBetween(40, 80));
        }

        switch (state) {
            case STALK -> {
                if (getNextActionTick() == 0) {
                    setNextActionTick(now + this.random.nextBetween(40, 80));
                }

                this.getNavigation().stop();
                this.getBrain().forget(MemoryModuleType.WALK_TARGET);

                if (lookedTooLong && !hasRepositioned()) {
                    Vec3d avoid = this.getPos().subtract(owner.getPos()).normalize();
                    if (tryReposition(owner, 8, 18, avoid)) {
                        setRepositionUsed(true);
                        bumpJitterSeed();
                        playGlitchEffect();
                        this.lookHoldTicks = 0;
                        setNextActionTick(now + this.random.nextBetween(20, 40));
                        return;
                    }
                }

                if (now >= getNextActionTick()) {
                    beginBlinkPhase(now);
                }
            }
            case BLINK -> {
                // blink sequence antes do rush
                this.getNavigation().stop();
                this.getBrain().forget(MemoryModuleType.WALK_TARGET);
                if (now >= getNextActionTick()) {
                    boolean teleported = blinkTeleport(owner);
                    if (!teleported) {
                        setHuntState(HuntState.RUSH);
                        setNextActionTick(now + PHASE3_RUSH_DURATION);
                        return;
                    }

                    bumpJitterSeed();
                    playGlitchEffect();
                    this.lookHoldTicks = 0;
                    this.blinkCount++;

                    if (this.blinkCount >= this.blinkTargetCount) {
                        setHuntState(HuntState.RUSH);
                        setNextActionTick(now + PHASE3_RUSH_DURATION);
                    } else {
                        setNextActionTick(now + this.random.nextBetween(6, 14));
                    }
                }
            }
            case RUSH -> {
                if (getBrain().getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET).isEmpty()) {
                    getBrain().remember(MemoryModuleType.ATTACK_TARGET, owner);
                }

                this.getNavigation().startMovingTo(owner, 1.25);

                if (this.distanceTo(owner) <= HIT_RANGE) {
                    hitAndVanish(owner);
                    return;
                }

                if (now >= getNextActionTick()) {
                    discard();
                }
            }
            default -> {
                // idle / cooldown not used por enquanto
            }
        }
    }

    private void beginBlinkPhase(int now) {
        this.blinkCount = 0;
        this.blinkTargetCount = this.random.nextBetween(1, MAX_BLINK_TELEPORTS);
        setHuntState(HuntState.BLINK);
        setNextActionTick(now + this.random.nextBetween(4, 10));
        this.getNavigation().stop();
        this.getBrain().forget(MemoryModuleType.WALK_TARGET);
    }

    private boolean blinkTeleport(PlayerEntity owner) {
        return tryReposition(owner, 8, 18, null);
    }

    private boolean tryReposition(PlayerEntity owner, int minRange, int maxRange, Vec3d avoidDirection) {
        Vec3d look = owner.getRotationVec(1.0f).normalize();

        for (int i = 0; i < 14; i++) {
            double distance = minRange + this.random.nextDouble() * (maxRange - minRange);
            double angle = this.random.nextDouble() * Math.PI * 2;
            double dx = Math.cos(angle) * distance;
            double dz = Math.sin(angle) * distance;
            double dy = this.random.nextBetween(-2, 2);

            Vec3d targetPos = owner.getPos().add(dx, dy, dz);
            BlockPos pos = BlockPos.ofFloored(targetPos);

            if (!this.getWorld().isInBuildLimit(pos)) {
                continue;
            }

            boolean hasFloor = this.getWorld().getBlockState(pos.down()).isSolid();
            boolean space = this.getWorld().isAir(pos) && this.getWorld().isAir(pos.up());

            if (!hasFloor || !space) {
                continue;
            }

            if (!hasCover(pos)) {
                continue;
            }

            Vec3d fromOwner = targetPos.subtract(owner.getPos()).normalize();
            if (avoidDirection != null && fromOwner.dotProduct(avoidDirection) > 0.25) {
                continue;
            }

            double dot = look.dotProduct(fromOwner);
            if (dot >= REPOSITION_FOV_MAX_DOT) {
                continue;
            }

            this.refreshPositionAndAngles(targetPos.x, pos.getY(), targetPos.z, this.getYaw(), this.getPitch());
            this.getNavigation().stop();
            this.getBrain().forget(MemoryModuleType.WALK_TARGET);
            return true;
        }

        return false;
    }

    private void playGlitchEffect() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        serverWorld.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.8f, 1.2f + this.random.nextFloat() * 0.2f);
        serverWorld.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getEyeY(), this.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
    }

    private boolean hasCover(BlockPos pos) {
        return this.getWorld().getBlockState(pos.north()).isSolid() ||
                this.getWorld().getBlockState(pos.south()).isSolid() ||
                this.getWorld().getBlockState(pos.east()).isSolid() ||
                this.getWorld().getBlockState(pos.west()).isSolid();
    }

    private void hitAndVanish(PlayerEntity owner) {
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            owner.damage(serverWorld, this.getDamageSources().mobAttack(this), 6.0f);
        }
        // efeitos curtos para dar clima
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 20, 0));
        owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
        discard();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        this.dataTracker.get(OWNER_REF).ifPresent(ref -> ref.writeNbt(nbt, "Owner"));
        nbt.putInt("Phase", getPhase());
        nbt.putBoolean("RepositionUsed", hasRepositioned());
        nbt.putByte("HuntState", this.dataTracker.get(HUNT_STATE));
        nbt.putInt("NextActionTick", getNextActionTick());
        nbt.putInt("LifeTicks", this.lifeTicks);
        nbt.putInt("BlinkCount", this.blinkCount);
        nbt.putInt("BlinkTargetCount", this.blinkTargetCount);
        nbt.putInt("JitterSeed", getJitterSeed());
        nbt.putInt("LookHoldTicks", this.lookHoldTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        LazyEntityReference<LivingEntity> ownerRef = LazyEntityReference.fromNbt(nbt, "Owner");
        this.dataTracker.set(OWNER_REF, ownerRef == null ? Optional.empty() : Optional.of(ownerRef));
        setPhase(nbt.getInt("Phase", 1));
        setRepositionUsed(nbt.getBoolean("RepositionUsed", false));
        this.dataTracker.set(HUNT_STATE, nbt.getByte("HuntState", HuntState.IDLE.id()));
        setNextActionTick(nbt.getInt("NextActionTick", 0));
        this.lifeTicks = nbt.getInt("LifeTicks", 0);
        this.blinkCount = nbt.getInt("BlinkCount", 0);
        this.blinkTargetCount = Math.max(1, nbt.getInt("BlinkTargetCount", 1));
        this.dataTracker.set(JITTER_SEED, nbt.getInt("JitterSeed", 0));
        this.lookHoldTicks = nbt.getInt("LookHoldTicks", 0);
    }
}
