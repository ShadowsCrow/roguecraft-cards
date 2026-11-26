package com.roguecards.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.roguecards.xp.RogueXp;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Detecta todo XP normal que nasce no mundo, para soltar XP azul junto.
 * O construtor cobre mob, minério, fornalha, pesca, troca e procriação.
 * O tryMergeToExisting cobre o caso em que o jogo soma o XP numa esfera que já existe.
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;DDDI)V", at = @At("RETURN"))
    private void roguecards$onCreate(Level level, double x, double y, double z, int value, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel && ((Object) this).getClass() == ExperienceOrb.class) {
            RogueXp.onVanillaOrb(serverLevel, x, y, z, value);
        }
    }

    @Inject(method = "tryMergeToExisting", at = @At("RETURN"))
    private static void roguecards$onMerge(ServerLevel level, Vec3 pos, int amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            RogueXp.onVanillaOrb(level, pos.x(), pos.y(), pos.z(), amount);
        }
    }
}
