package com.roguecards.xp;

import java.util.ArrayList;
import java.util.List;

import com.roguecards.RoguecraftCards;
import com.roguecards.api.card.CardType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Efeitos das cartas de habilidade. Tudo roda no servidor.
 * A Saciedade fica no PlayerMixin, porque o jogo não tem evento para o gasto de fome.
 */
@EventBusSubscriber(modid = RoguecraftCards.MODID)
public final class AbilityEvents {
    /** Segundos sem dar nem receber dano para a Regeneração funcionar. */
    private static final int OUT_OF_COMBAT_TICKS = 5 * 20;
    /** Vida da Execução: alvos abaixo desta fração da vida máxima levam dano extra. */
    private static final float EXECUTION_THRESHOLD = 0.3F;
    private static final float STORM_DAMAGE = 5.0F;

    private AbilityEvents() {
    }

    private static double val(ServerPlayer player, CardType type) {
        return RogueXp.bonus(player, type);
    }

    /** Dano do jogador, sem contar o reflexo dos Espinhos (evita um ciclo de reflexos). */
    private static ServerPlayer attackerOf(DamageSource source) {
        if (source.is(DamageTypes.THORNS)) {
            return null;
        }
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }

    // ---------- Antes do dano: Crítico, Execução, Queda Leve ----------

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        if (target instanceof ServerPlayer victim && source.is(DamageTypeTags.IS_FALL)) {
            double reduction = val(victim, CardType.FEATHER_FALL);
            if (reduction >= 1.0) {
                event.setCanceled(true);
                return;
            }
            if (reduction > 0) {
                event.setAmount((float) (event.getAmount() * (1.0 - reduction)));
            }
        }

        ServerPlayer attacker = attackerOf(source);
        if (attacker == null || target == attacker) {
            return;
        }
        float amount = event.getAmount();
        double crit = val(attacker, CardType.CRITICAL);
        if (crit > 0 && attacker.getRandom().nextDouble() < crit) {
            amount *= 2;
            ServerLevel level = attacker.serverLevel();
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 14, 0.3, 0.3, 0.3, 0.25);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
        double execution = val(attacker, CardType.EXECUTION);
        if (execution > 0 && target.getHealth() < target.getMaxHealth() * EXECUTION_THRESHOLD) {
            amount *= (float) (1.0 + execution);
        }
        event.setAmount(amount);
    }

    // ---------- Depois do dano: Espinhos, Vampirismo, Tempestade, marca de combate ----------

    @SubscribeEvent
    static void onDamaged(LivingDamageEvent.Post event) {
        float dealt = event.getNewDamage();
        if (dealt <= 0) {
            return;
        }
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        if (target instanceof ServerPlayer victim) {
            RogueXp.get(victim).lastCombatTick = victim.tickCount;
            if (source.getEntity() instanceof LivingEntity hitter && hitter != victim && !source.is(DamageTypes.THORNS)) {
                double thorns = val(victim, CardType.THORNS);
                if (thorns > 0 && hitter.isAlive()) {
                    hitter.hurt(victim.damageSources().thorns(victim), (float) (dealt * thorns));
                }
            }
        }

        ServerPlayer attacker = attackerOf(source);
        if (attacker == null || target == attacker) {
            return;
        }
        RogueXp.get(attacker).lastCombatTick = attacker.tickCount;

        double vampirism = val(attacker, CardType.VAMPIRISM);
        if (vampirism > 0) {
            attacker.heal((float) (dealt * vampirism));
        }

        double storm = val(attacker, CardType.STORM);
        if (storm > 0 && target.isAlive() && attacker.getRandom().nextDouble() < storm) {
            ServerLevel level = attacker.serverLevel();
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(target.getX(), target.getY(), target.getZ());
                bolt.setVisualOnly(true);
                bolt.setCause(attacker);
                level.addFreshEntity(bolt);
            }
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().lightningBolt(), STORM_DAMAGE);
            target.igniteForSeconds(3);
        }
    }

    // ---------- Morte: Segunda Chance e Colheita ----------

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            trySecondChance(event, player);
            return;
        }
        if (event.getEntity() instanceof Mob && event.getSource().getEntity() instanceof ServerPlayer killer) {
            double harvest = val(killer, CardType.HARVEST);
            if (harvest > 0) {
                RogueXp.gainScaled(killer, harvest, RogueXp.SOURCE_HARVEST);
            }
        }
    }

    private static void trySecondChance(LivingDeathEvent event, ServerPlayer player) {
        double minutes = val(player, CardType.SECOND_CHANCE);
        if (minutes <= 0) {
            return;
        }
        RogueData data = RogueXp.get(player);
        long now = player.level().getGameTime();
        if (now < data.secondChanceReadyAt) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth() / 2);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 2));
        if (player.isOnFire()) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
        }
        data.secondChanceReadyAt = now + Math.round(minutes * 60 * 20);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY(1.0), player.getZ(), 60, 0.4, 0.6, 0.4, 0.4);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.1F);
        player.displayClientMessage(Component.translatable("message.roguecards.second_chance", CardType.trim(minutes))
                .withStyle(ChatFormatting.GOLD), true);
    }

    // ---------- A cada tick: Regeneração, Ímã, Toupeira ----------

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()) {
            return;
        }
        RogueData data = RogueXp.get(player);

        double regenSeconds = val(player, CardType.REGENERATION);
        if (regenSeconds > 0 && player.getHealth() < player.getMaxHealth()
                && player.tickCount - data.lastCombatTick > OUT_OF_COMBAT_TICKS) {
            int interval = Math.max(20, (int) Math.round(regenSeconds * 20));
            if (player.tickCount % interval == 0) {
                player.heal(2.0F);
            }
        }

        double radius = val(player, CardType.MAGNET);
        if (radius > 0 && player.tickCount % 2 == 0 && !player.isShiftKeyDown()) {
            pullNearby(player, radius);
        }

        if (val(player, CardType.MOLE) > 0 && player.tickCount % 40 == 0 && player.getY() < player.level().getSeaLevel()) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
        }
    }

    /** Puxa itens e esferas de XP. Não puxa itens que o próprio jogador jogou fora. Agachar desliga o ímã. */
    private static void pullNearby(ServerPlayer player, double radius) {
        Vec3 center = player.position().add(0, 0.75, 0);
        AABB box = player.getBoundingBox().inflate(radius);
        List<Entity> targets = player.level().getEntities(player, box,
                e -> e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof BlueXpOrb);
        for (Entity entity : targets) {
            if (entity instanceof ItemEntity item
                    && (item.getOwner() == player || item.getPersistentData().getBoolean("PreventRemoteMovement"))) {
                continue;
            }
            Vec3 toPlayer = center.subtract(entity.position());
            double distance = toPlayer.length();
            if (distance < 0.6 || distance > radius) {
                continue;
            }
            entity.setDeltaMovement(toPlayer.normalize().scale(0.35));
            entity.hurtMarked = true;
        }
    }

    // ---------- Minério: Garimpo ----------

    @SubscribeEvent
    static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || !event.getState().is(Tags.Blocks.ORES)) {
            return;
        }
        double chance = val(player, CardType.PROSPECTOR);
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) {
            return;
        }
        // Não duplica o próprio bloco de minério (Toque de Seda), só o que ele solta.
        Item oreItem = event.getState().getBlock().asItem();
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!stack.is(oreItem)) {
                extra.add(new ItemEntity(event.getLevel(), drop.getX(), drop.getY(), drop.getZ(), stack.copy()));
            }
        }
        if (!extra.isEmpty()) {
            event.getDrops().addAll(extra);
            event.getLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    event.getPos().getX() + 0.5, event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.0);
        }
    }
}
