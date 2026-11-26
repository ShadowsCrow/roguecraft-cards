package com.roguecards.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.roguecards.xp.RogueXp;

import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.phys.HitResult;

/** Marca o XP que vem do Frasco de Experiência, para a config xpBottlesGiveBlueXp poder ignorá-lo. */
@Mixin(ThrownExperienceBottle.class)
public abstract class ExperienceBottleMixin {

    @Inject(method = "onHit", at = @At("HEAD"))
    private void roguecards$markStart(HitResult result, CallbackInfo ci) {
        RogueXp.FROM_XP_BOTTLE.get()[0] = true;
    }

    @Inject(method = "onHit", at = @At("RETURN"))
    private void roguecards$markEnd(HitResult result, CallbackInfo ci) {
        RogueXp.FROM_XP_BOTTLE.get()[0] = false;
    }
}
