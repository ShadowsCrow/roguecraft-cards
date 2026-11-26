package com.roguecards.item;

import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

/** Soma da sorte dos berloques equipados nos espaços do Curios. */
public final class CharmLuck {
    private CharmLuck() {
    }

    /**
     * Exemplo: berloque de netherita equipado → 5.
     * Esse valor soma ao limite de sorte do sorteio de raridade (padrão 50), então o berloque "passa do limite".
     */
    public static double equipped(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findCurios(stack -> stack.getItem() instanceof LuckCharmItem).stream()
                        .mapToDouble(result -> ((LuckCharmItem) result.stack().getItem()).luck())
                        .sum())
                .orElse(0.0);
    }
}
