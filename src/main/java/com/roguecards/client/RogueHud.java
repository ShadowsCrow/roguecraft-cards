package com.roguecards.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Barra azul fina logo acima da barra de XP normal.
 * O nível azul fica à esquerda da barra; o aviso de carta pendente fica à direita.
 * Corações e fome sobem 4 pixels para dar espaço.
 */
public final class RogueHud {
    public static final int BAR_WIDTH = 182;
    public static final int BAR_HEIGHT = 3;

    private static final int COLOR_BACK = 0xFF0A1730;
    private static final int COLOR_BORDER = 0xFF000000;
    private static final int COLOR_FILL = 0xFF2E8BFF;
    private static final int COLOR_SHINE = 0xFF9FD4FF;
    private static final int COLOR_TEXT = 0x55AAFF;

    private RogueHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || !mc.gameMode.hasExperience() || mc.player.jumpableVehicle() != null
                || !com.roguecards.ClientConfig.SHOW_BLUE_BAR.get()) {
            return;
        }
        Font font = mc.font;
        int x = g.guiWidth() / 2 - BAR_WIDTH / 2;
        int y = g.guiHeight() - 29 - BAR_HEIGHT - 1;

        // Barra: contorno, fundo, preenchimento e uma linha de brilho em cima.
        g.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, COLOR_BORDER);
        g.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, COLOR_BACK);
        int filled = Mth.floor(ClientRogueData.progress() * BAR_WIDTH);
        if (filled > 0) {
            g.fill(x, y, x + filled, y + BAR_HEIGHT, COLOR_FILL);
            g.fill(x, y, x + filled, y + 1, COLOR_SHINE);
        }

        // Nível azul à esquerda da barra.
        String level = ClientRogueData.maxed ? ClientRogueData.level + " MÁX" : String.valueOf(ClientRogueData.level);
        int ty = y + BAR_HEIGHT / 2 - 4;
        drawOutlined(g, font, level, x - font.width(level) - 4, ty, COLOR_TEXT);

        // Aviso de carta pendente à direita, piscando.
        if (ClientRogueData.pending > 0 && com.roguecards.ClientConfig.SHOW_CARD_HINT.get()) {
            boolean bright = (mc.player.tickCount / 10) % 2 == 0;
            Component hint = Component.translatable("hud.roguecards.card_ready", RoguecraftCardsKeys.OPEN_CARDS.getTranslatedKeyMessage())
                    .withStyle(bright ? ChatFormatting.AQUA : ChatFormatting.DARK_AQUA);
            g.drawString(font, hint, x + BAR_WIDTH + 4, ty, 0xFFFFFF, true);
        }

        mc.gui.leftHeight += BAR_HEIGHT + 1;
        mc.gui.rightHeight += BAR_HEIGHT + 1;
    }

    private static void drawOutlined(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x + 1, y, 0, false);
        g.drawString(font, text, x - 1, y, 0, false);
        g.drawString(font, text, x, y + 1, 0, false);
        g.drawString(font, text, x, y - 1, 0, false);
        g.drawString(font, text, x, y, color, false);
    }
}
