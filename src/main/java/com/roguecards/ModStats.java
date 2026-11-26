package com.roguecards;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Estatísticas do mod (aparecem em Estatísticas → Geral e servem para o placar).
 * Exemplo: /scoreboard objectives add cartas minecraft.custom:roguecards.cards_chosen
 */
public final class ModStats {
    public static final DeferredRegister<ResourceLocation> STATS = DeferredRegister.create(Registries.CUSTOM_STAT, RoguecraftCards.MODID);

    /** Cartas escolhidas (todas as raridades). */
    public static final DeferredHolder<ResourceLocation, ResourceLocation> CARDS_CHOSEN = register("cards_chosen");
    /** Níveis azuis ganhos. */
    public static final DeferredHolder<ResourceLocation, ResourceLocation> BLUE_LEVELS = register("blue_levels");
    /** Cartas lendárias escolhidas. */
    public static final DeferredHolder<ResourceLocation, ResourceLocation> LEGENDARY_CARDS = register("legendary_cards");

    private ModStats() {
    }

    private static DeferredHolder<ResourceLocation, ResourceLocation> register(String name) {
        ResourceLocation id = RoguecraftCards.id(name);
        return STATS.register(name, () -> id);
    }

    public static void register(IEventBus modBus) {
        STATS.register(modBus);
        modBus.addListener(ModStats::onSetup);
    }

    /** Cria as estatísticas com o formato de número simples (como o jogo faz com as dele). */
    private static void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Stats.CUSTOM.get(CARDS_CHOSEN.get(), StatFormatter.DEFAULT);
            Stats.CUSTOM.get(BLUE_LEVELS.get(), StatFormatter.DEFAULT);
            Stats.CUSTOM.get(LEGENDARY_CARDS.get(), StatFormatter.DEFAULT);
        });
    }
}
