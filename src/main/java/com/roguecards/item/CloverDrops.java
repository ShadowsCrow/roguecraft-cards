package com.roguecards.item;

import com.roguecards.Config;
import com.roguecards.RoguecraftCards;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Trevo de quatro folhas: ao quebrar grama ou samambaia, há uma chance de cair um trevo.
 * A chance sobe com a sorte do jogador naquele momento (cartas, berloque, poção).
 * Valores padrão: 1,5% + 0,5% por ponto de sorte, no máximo 25%.
 * Exemplo: sorte 0 → 1,5% (cerca de 1 a cada 67 gramas); sorte 5 → 4%; sorte 20 → 11,5%.
 */
@EventBusSubscriber(modid = RoguecraftCards.MODID)
public final class CloverDrops {
    private CloverDrops() {
    }

    public static boolean isGrass(BlockState state) {
        return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN);
    }

    /** Chance de cair um trevo, de 0 a 1, para uma sorte. */
    public static double chance(double luck) {
        double chance = Config.CLOVER_BASE_CHANCE.get() + Config.CLOVER_CHANCE_PER_LUCK.get() * Math.max(0, luck);
        return Math.min(Config.CLOVER_MAX_CHANCE.get(), chance);
    }

    @SubscribeEvent
    static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || player.isCreative() || !isGrass(event.getState())) {
            return;
        }
        double luck = player.getAttributeValue(Attributes.LUCK);
        if (player.getRandom().nextDouble() >= chance(luck)) {
            return;
        }
        var pos = event.getPos();
        event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                new ItemStack(ModItems.FOUR_LEAF_CLOVER.get())));
    }
}
