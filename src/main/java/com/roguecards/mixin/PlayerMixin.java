package com.roguecards.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.roguecards.api.card.CardType;
import com.roguecards.xp.RogueXp;

import net.minecraft.world.entity.player.Player;

/** Carta Saciedade: diminui o gasto de fome. O jogo não tem evento para isso, então o mod ajusta o valor na entrada. */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float roguecards$satiety(float exhaustion) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) {
            return exhaustion;
        }
        double reduction = RogueXp.bonus(self, CardType.SATIETY);
        return reduction > 0 ? (float) (exhaustion * (1.0 - Math.min(reduction, 0.95))) : exhaustion;
    }
}
