package com.roguecards.client;

import java.util.ArrayList;
import java.util.List;

import com.roguecards.RoguecraftCards;
import com.roguecards.api.card.CardOffer;
import com.roguecards.api.card.CardType;
import com.roguecards.api.card.Rarity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Galeria de cartas: mostra cada carta em todas as raridades em que ela existe e salva um print de cada uma
 * em screenshots/roguecards_NN_nome.png. Depois fecha o jogo.
 * Só liga quando o jogo é aberto com a opção -Droguecards.gallery=true (gradlew runClient -Pgaleria).
 */
@EventBusSubscriber(modid = RoguecraftCards.MODID, value = Dist.CLIENT)
public class GalleryScreen extends Screen {
    private static boolean started;

    private final List<CardType> cards = new ArrayList<>();
    private int index;
    private int ticksOnCard;

    public GalleryScreen() {
        super(Component.literal("Roguecraft Cards — galeria"));
        cards.addAll(CardType.values());
    }

    /** Abre a galeria assim que a tela inicial aparece, se o jogo foi aberto no modo galeria. */
    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!started && event.getScreen() instanceof TitleScreen && Boolean.getBoolean("roguecards.gallery")) {
            started = true;
            Minecraft.getInstance().setScreen(new GalleryScreen());
        }
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        // Enquanto a tela de carregamento do Minecraft estiver por cima, não tira print.
        if (mc.getOverlay() != null) {
            ticksOnCard = 0;
            return;
        }
        ticksOnCard++;
        // Espera alguns ticks para a carta estar desenhada; o print lê a última imagem desenhada.
        if (ticksOnCard == 6 && index < cards.size()) {
            CardType card = cards.get(index);
            String name = String.format("roguecards_%02d_%s_%s.png", index + 1, card.key().getNamespace(), card.key().getPath());
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> { });
        }
        if (ticksOnCard >= 10) {
            ticksOnCard = 0;
            index++;
            if (index >= cards.size()) {
                RoguecraftCards.LOGGER.info("Galeria: {} prints salvos em screenshots/", cards.size());
                mc.stop();
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xFF0A0F1C);
        if (index >= cards.size()) {
            return;
        }
        CardType card = cards.get(index);
        List<Rarity> rarities = new ArrayList<>();
        for (Rarity r : Rarity.values()) {
            if (card.exists(r)) {
                rarities.add(r);
            }
        }
        int n = rarities.size();
        int rowW = n * CardScreen.CARD_W + (n - 1) * CardScreen.CARD_GAP;
        int neededW = Math.max(rowW + 40, 420);
        int neededH = CardScreen.CARD_H + 90;
        float scale = Math.min(1.0F, Math.min(this.width / (float) neededW, this.height / (float) neededH));
        int vw = Math.round(this.width / scale);
        int vh = Math.round(this.height / scale);

        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0F);
        int top = (vh - CardScreen.CARD_H) / 2 + 10;
        int cx = vw / 2;
        g.drawCenteredString(this.font, card.displayName().copy().withStyle(ChatFormatting.BOLD), cx, top - 44, 0xFFFFFF);
        Component sub = Component.empty()
                .append(card.category().displayName().copy().withColor(card.category().color()))
                .append(Component.literal("  ·  nível " + card.minLevel()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(card.unlimited() ? "  ·  sem limite" : "  ·  limite " + card.limit()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(card.hasCap() ? "  ·  teto " + card.formatAmount(card.cap()) : "").withStyle(ChatFormatting.GRAY));
        g.drawCenteredString(this.font, sub, cx, top - 30, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.literal(card.id()).withStyle(ChatFormatting.DARK_GRAY), cx, top - 18, 0xFFFFFF);
        int left = (vw - rowW) / 2;
        for (int i = 0; i < n; i++) {
            CardScreen.drawCard(g, this.font, CardOffer.of(card, rarities.get(i)), left + i * (CardScreen.CARD_W + CardScreen.CARD_GAP), top, false, false);
        }
        g.pose().popPose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
