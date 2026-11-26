package com.roguecards;

import com.roguecards.client.BlueXpOrbRenderer;
import com.roguecards.client.CardScreen;
import com.roguecards.client.RoguecraftCardsKeys;
import com.roguecards.client.RogueHud;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Parte do mod que só existe no jogo (não no servidor dedicado): desenho, barra, tela e tecla. */
@Mod(value = RoguecraftCards.MODID, dist = Dist.CLIENT)
public class RoguecraftCardsClient {
    public RoguecraftCardsClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(RoguecraftCardsClient::onRenderers);
        modBus.addListener(RoguecraftCardsClient::onGuiLayers);
        modBus.addListener(RoguecraftCardsClient::onKeys);
        NeoForge.EVENT_BUS.addListener(RoguecraftCardsClient::onClientTick);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RoguecraftCards.BLUE_XP_ORB.get(), BlueXpOrbRenderer::new);
    }

    private static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR, RoguecraftCards.id("blue_xp_bar"), RogueHud::render);
    }

    private static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(RoguecraftCardsKeys.OPEN_CARDS);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (RoguecraftCardsKeys.OPEN_CARDS.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new CardScreen());
            }
        }
    }
}
