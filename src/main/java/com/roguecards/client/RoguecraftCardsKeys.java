package com.roguecards.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

/** Tecla para abrir a tela de cartas. Padrão: K. Muda em Opções → Controles. */
public final class RoguecraftCardsKeys {
    public static final KeyMapping OPEN_CARDS = new KeyMapping(
            "key.roguecards.open_cards", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.roguecards");

    private RoguecraftCardsKeys() {
    }
}
