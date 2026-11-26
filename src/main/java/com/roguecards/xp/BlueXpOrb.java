package com.roguecards.xp;

import com.roguecards.RoguecraftCards;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Esfera de XP azul. Funciona como a esfera normal: pula ao cair, segue o jogador
 * a até 8 blocos, junta com esferas iguais e some depois de 5 minutos.
 * Ao ser tocada, dá XP azul em vez de XP normal.
 */
public class BlueXpOrb extends Entity {
    private static final EntityDataAccessor<Integer> DATA_VALUE = SynchedEntityData.defineId(BlueXpOrb.class, EntityDataSerializers.INT);
    private static final int LIFETIME = 6000;

    private int age;
    private int health = 5;
    private int count = 1;
    private Player followingPlayer;

    public BlueXpOrb(EntityType<? extends BlueXpOrb> type, Level level) {
        super(type, level);
    }

    public BlueXpOrb(Level level, double x, double y, double z, int value) {
        this(RoguecraftCards.BLUE_XP_ORB.get(), level);
        this.setPos(x, y, z);
        this.setYRot((float) (this.random.nextDouble() * 360.0));
        this.setDeltaMovement(
                (this.random.nextDouble() * 0.2F - 0.1F) * 2.0,
                this.random.nextDouble() * 0.2 * 2.0,
                (this.random.nextDouble() * 0.2F - 0.1F) * 2.0);
        this.setValue(value);
    }

    /** Divide o valor em esferas do mesmo tamanho que o jogo usa para XP normal. */
    public static void award(ServerLevel level, Vec3 pos, int amount) {
        while (amount > 0) {
            int part = ExperienceOrb.getExperienceValue(amount);
            amount -= part;
            level.addFreshEntity(new BlueXpOrb(level, pos.x(), pos.y(), pos.z(), part));
        }
    }

    public int getValue() {
        return this.entityData.get(DATA_VALUE);
    }

    private void setValue(int value) {
        this.entityData.set(DATA_VALUE, value);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_VALUE, 1);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.03;
    }

    @Override
    public void tick() {
        super.tick();
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        if (this.isEyeInFluid(FluidTags.WATER)) {
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(v.x * 0.99F, Math.min(v.y + 5.0E-4F, 0.06F), v.z * 0.99F);
        } else {
            this.applyGravity();
        }

        if (this.level().getFluidState(this.blockPosition()).is(FluidTags.LAVA)) {
            this.setDeltaMovement(
                    (this.random.nextFloat() - this.random.nextFloat()) * 0.2F,
                    0.2F,
                    (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
        }

        if (!this.level().noCollision(this.getBoundingBox())) {
            this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0, this.getZ());
        }

        if (this.tickCount % 20 == 1) {
            this.scanForEntities();
        }

        if (this.followingPlayer != null && (this.followingPlayer.isSpectator() || this.followingPlayer.isDeadOrDying())) {
            this.followingPlayer = null;
        }

        if (this.followingPlayer != null) {
            Vec3 toPlayer = new Vec3(
                    this.followingPlayer.getX() - this.getX(),
                    this.followingPlayer.getY() + this.followingPlayer.getEyeHeight() / 2.0 - this.getY(),
                    this.followingPlayer.getZ() - this.getZ());
            double distSqr = toPlayer.lengthSqr();
            if (distSqr < 64.0) {
                double pull = 1.0 - Math.sqrt(distSqr) / 8.0;
                this.setDeltaMovement(this.getDeltaMovement().add(toPlayer.normalize().scale(pull * pull * 0.1)));
            }
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        float friction = 0.98F;
        if (this.onGround()) {
            BlockPos below = this.getBlockPosBelowThatAffectsMyMovement();
            friction = this.level().getBlockState(below).getFriction(this.level(), below, this) * 0.98F;
        }

        this.setDeltaMovement(this.getDeltaMovement().multiply(friction, 0.98, friction));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, -0.9, 1.0));
        }

        this.age++;
        if (this.age >= LIFETIME) {
            this.discard();
        }
    }

    @Override
    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void scanForEntities() {
        if (this.followingPlayer == null || this.followingPlayer.distanceToSqr(this) > 64.0) {
            Player nearest = this.level().getNearestPlayer(this, 8.0);
            this.followingPlayer = nearest != null && !nearest.isSpectator() ? nearest : null;
        }

        if (this.level() instanceof ServerLevel) {
            int value = this.getValue();
            for (BlueXpOrb other : this.level().getEntitiesOfClass(BlueXpOrb.class, this.getBoundingBox().inflate(0.5),
                    o -> o != this && !o.isRemoved() && o.getValue() == value)) {
                this.count += other.count;
                this.age = Math.min(this.age, other.age);
                other.discard();
            }
        }
    }

    @Override
    protected void doWaterSplashEffect() {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        if (this.level().isClientSide) {
            return true;
        }
        this.markHurt();
        this.health = (int) (this.health - amount);
        if (this.health <= 0) {
            this.discard();
        }
        return true;
    }

    @Override
    public void playerTouch(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.isSpectator()) {
            return;
        }
        RogueData data = RogueXp.get(serverPlayer);
        // Uma esfera a cada 2 ticks, como a esfera normal.
        if (serverPlayer.tickCount - data.lastPickupTick < 2 && serverPlayer.tickCount >= data.lastPickupTick) {
            return;
        }
        data.lastPickupTick = serverPlayer.tickCount;
        RogueXp.gainScaled(serverPlayer, this.getValue());
        this.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.12F,
                0.55F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
        this.count--;
        if (this.count <= 0) {
            this.discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putShort("Health", (short) this.health);
        tag.putShort("Age", (short) this.age);
        tag.putInt("Value", this.getValue());
        tag.putInt("Count", this.count);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.health = tag.getShort("Health");
        this.age = tag.getShort("Age");
        this.setValue(Math.max(1, tag.getInt("Value")));
        this.count = Math.max(tag.getInt("Count"), 1);
    }

    /** Tamanho do desenho, igual à esfera normal (0 = menor, 10 = maior). */
    public int getIcon() {
        int value = this.getValue();
        if (value >= 2477) return 10;
        if (value >= 1237) return 9;
        if (value >= 617) return 8;
        if (value >= 307) return 7;
        if (value >= 149) return 6;
        if (value >= 73) return 5;
        if (value >= 37) return 4;
        if (value >= 17) return 3;
        if (value >= 7) return 2;
        return value >= 3 ? 1 : 0;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }
}
