package com.roguecards.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.roguecards.RoguecraftCards;
import com.mojang.blaze3d.systems.RenderSystem;
import com.roguecards.api.card.CardOffer;
import com.roguecards.api.card.CardType;
import com.roguecards.api.card.Rarity;
import com.roguecards.network.CardActionPayload;
import com.roguecards.network.ChooseCardPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tela de cartas (tecla K).
 *
 * A tela tem um tamanho próprio, que não depende do "Tamanho da interface" do Minecraft:
 * tudo é desenhado numa tela virtual de pelo menos 640 x 400 e depois reduzido para caber na janela.
 * Assim, cartas, letras e botões diminuem juntos, na mesma proporção.
 *
 * Conteúdo: nível e XP azul, as cartas oferecidas (3, ou 4 com Mão Cheia) com a moldura da raridade,
 * os botões Trocar Cartas e Banir, a chance de cada raridade, os bônus por tipo e a trilha de escolhas.
 */
public class CardScreen extends Screen {
    /** Tamanho mínimo da tela virtual. Se a janela for menor, tudo é reduzido. */
    private static final int VIRTUAL_MIN_W = 640;
    private static final int VIRTUAL_MIN_H = 400;

    /** Textura da moldura (37 x 55 pixels), desenhada em 4x: cada carta tem 148 x 220. */
    static final int TEX_W = 37;
    static final int TEX_H = 55;
    static final int CARD_SCALE = 4;
    static final int CARD_W = TEX_W * CARD_SCALE;
    static final int CARD_H = TEX_H * CARD_SCALE;
    static final int CARD_GAP = 14;
    /**
     * Aura da carta sob o mouse, por raridade (comum → lendária). É a aura que rara, épica e lendária já têm
     * depois de revelar (força 0,16 / 0,2 / 0,3; raio 6 / 8 / 11), só que mais forte e maior.
     */
    private static final float[] HOVER_AURA_ALPHA = {0.3F, 0.35F, 0.4F, 0.5F, 0.65F};
    private static final int[] HOVER_AURA_RADIUS = {6, 7, 9, 11, 14};
    private static final int CARDS_TOP = 50;

    /** Moldura por raridade: cobre, ferro, ouro, diamante, netherita. */
    static final ResourceLocation[] FRAMES = {
            RoguecraftCards.id("textures/gui/card/copper.png"),
            RoguecraftCards.id("textures/gui/card/iron.png"),
            RoguecraftCards.id("textures/gui/card/gold.png"),
            RoguecraftCards.id("textures/gui/card/diamond.png"),
            RoguecraftCards.id("textures/gui/card/netherite.png"),
    };

    /** Botão desenhado pela própria tela, em coordenadas virtuais. */
    private record Btn(int x, int y, int w, int h, Component label, Runnable action, boolean danger) {
        Btn(int x, int y, int w, int h, Component label, Runnable action) {
            this(x, y, w, h, label, action, false);
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Btn> buttons = new ArrayList<>();
    /** Revelação animada das cartas (viradas para baixo, brilho da raridade, virada uma a uma). */
    final CardReveal reveal = new CardReveal();
    /** Dica da trilha sob o mouse; desenhada por último, por cima das cartas. */
    private List<Component> trailTooltip;
    private int tooltipX;
    private int tooltipY;
    private int hovered = -1;
    /** Versão dos dados no momento do clique; enquanto não mudar, ignora cliques novos. */
    private int waitingVersion = -1;
    private boolean choseSomething;
    /** Modo banir: o jogador escolhe uma das cartas da oferta (borda vermelha ao passar o mouse). */
    private boolean banMode;
    /** Carta da oferta esperando confirmação de banimento (-1 = nenhuma). */
    private int confirmBan = -1;

    /** Escala da tela virtual para a tela real (1 = mesmo tamanho; 0,6 = 60%). */
    private float scale = 1.0F;
    private int vw;
    private int vh;

    public CardScreen() {
        super(Component.translatable("screen.roguecards.cards"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Chamado quando chega atualização do servidor. Fecha a tela se acabaram as cartas. */
    public void onSync() {
        waitingVersion = -1;
        if (ClientRogueData.banTokens <= 0 || ClientRogueData.pending <= 0) {
            banMode = false;
        }
        confirmBan = -1;
        if (choseSomething && ClientRogueData.pending <= 0 && this.minecraft != null) {
            this.onClose();
        }
    }

    private boolean busy() {
        return waitingVersion == ClientRogueData.version;
    }

    private void updateScale() {
        int cards = Math.max(3, ClientRogueData.offer.size());
        int neededW = Math.max(VIRTUAL_MIN_W, cards * CARD_W + (cards - 1) * CARD_GAP + 40);
        scale = Math.min(1.0F, Math.min(this.width / (float) neededW, this.height / (float) VIRTUAL_MIN_H));
        vw = Math.round(this.width / scale);
        vh = Math.round(this.height / scale);
    }

    // ---------- Desenho ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        updateScale();
        int mx = (int) (mouseX / scale);
        int my = (int) (mouseY / scale);

        buttons.clear();
        trailTooltip = null;
        reveal.setBounds(vw, vh);
        reveal.update(ClientRogueData.pending > 0 ? ClientRogueData.offer : List.of());
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0F);
        renderMain(g, mx, my);
        if (trailTooltip != null) {
            g.renderComponentTooltip(this.font, trailTooltip, tooltipX, tooltipY);
        }
        if (confirmBan >= 0 && confirmBan < ClientRogueData.offer.size()) {
            renderBanConfirm(g, mx, my);
        } else {
            confirmBan = -1;
        }
        g.pose().popPose();
    }

    private void renderMain(GuiGraphics g, int mx, int my) {
        int center = vw / 2;
        g.drawCenteredString(this.font, Component.translatable("screen.roguecards.level", ClientRogueData.level)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), center, 8, 0xFFFFFF);
        Component xpLine = ClientRogueData.maxed
                ? Component.translatable("screen.roguecards.xp_max").withStyle(ChatFormatting.GOLD)
                : Component.translatable("screen.roguecards.xp", ClientRogueData.xp, ClientRogueData.xpNeeded);
        g.drawCenteredString(this.font, xpLine, center, 20, 0x9FD4FF);

        List<CardOffer> offer = ClientRogueData.offer;
        hovered = -1;
        boolean showCards = ClientRogueData.pending > 0 && !offer.isEmpty();
        if (showCards) {
            Component header = banMode
                    ? Component.translatable("screen.roguecards.ban_pick").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    : Component.translatable("screen.roguecards.choose", ClientRogueData.pending);
            g.drawCenteredString(this.font, header, center, 32, 0xFFFFFF);
        } else {
            Component msg = ClientRogueData.pending > 0 ? Component.translatable("screen.roguecards.no_cards")
                    : Component.translatable("screen.roguecards.none");
            g.drawCenteredString(this.font, msg.copy().withStyle(ChatFormatting.GRAY), center, CARDS_TOP + 40, 0xFFFFFF);
        }

        // Botões, chances e painel antes das cartas: assim o escurecimento da lendária cobre tudo, menos ela.
        int y = CARDS_TOP + CARD_H + 8;
        if (addActionButtons(y)) {
            y += 24;
        }
        for (Btn b : buttons) {
            drawButton(g, b, mx, my);
        }
        renderChances(g, y + 2);
        renderBottom(g, y + 18, mx, my);

        if (showCards) {
            int total = offer.size() * CARD_W + (offer.size() - 1) * CARD_GAP;
            int left = (vw - total) / 2;
            boolean canHover = !reveal.busy();
            for (int i = 0; i < offer.size(); i++) {
                int x = left + i * (CARD_W + CARD_GAP);
                boolean over = canHover && mx >= x && mx < x + CARD_W && my >= CARDS_TOP && my < CARDS_TOP + CARD_H;
                if (over) {
                    hovered = i;
                }
            }
            float dark = reveal.darkness();
            // Primeiro as cartas comuns a épicas; com o fundo escurecendo, a lendária vem depois, por cima.
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 1 && dark > 0) {
                    g.fill(0, 0, vw, vh, Math.round(dark * 255) << 24);
                }
                // Raios da lendária atrás das cartas da mesma camada.
                for (int i = 0; i < offer.size(); i++) {
                    boolean late = dark > 0 && reveal.isLegendary(i);
                    if (late == (pass == 1)) {
                        reveal.renderRays(g, i, left + i * (CARD_W + CARD_GAP), CARDS_TOP);
                    }
                }
                for (int i = 0; i < offer.size(); i++) {
                    boolean late = dark > 0 && reveal.isLegendary(i);
                    if (late != (pass == 1)) {
                        continue;
                    }
                    int x = left + i * (CARD_W + CARD_GAP);
                    reveal.renderCard(g, this.font, i, offer.get(i), x, CARDS_TOP, hovered == i && confirmBan < 0, banMode);
                }
            }
        }
    }

    /** Botões Trocar Cartas e Banir. Retorna true se algum apareceu. */
    private boolean addActionButtons(int y) {
        if (banMode) {
            buttons.add(new Btn(vw / 2 - 70, y, 140, 18, Component.translatable("screen.roguecards.cancel"), () -> banMode = false));
            return true;
        }
        boolean canReroll = ClientRogueData.pending > 0 && ClientRogueData.rerollsLeft > 0;
        boolean canBan = ClientRogueData.pending > 0 && ClientRogueData.banTokens > 0 && !ClientRogueData.offer.isEmpty();
        int count = (canReroll ? 1 : 0) + (canBan ? 1 : 0);
        if (count == 0) {
            return false;
        }
        int bw = 140;
        int x = vw / 2 - (count * bw + (count - 1) * 8) / 2;
        if (canReroll) {
            buttons.add(new Btn(x, y, bw, 18, Component.translatable("screen.roguecards.reroll", ClientRogueData.rerollsLeft), () -> {
                waitingVersion = ClientRogueData.version;
                PacketDistributor.sendToServer(CardActionPayload.reroll());
            }));
            x += bw + 8;
        }
        if (canBan) {
            buttons.add(new Btn(x, y, bw, 18, Component.translatable("screen.roguecards.ban", ClientRogueData.banTokens),
                    () -> banMode = true, true));
        }
        return true;
    }

    private void drawButton(GuiGraphics g, Btn b, int mx, int my) {
        boolean over = b.contains(mx, my);
        int border = b.danger() ? (over ? 0xFFFF6060 : 0xFFA02828) : (over ? 0xFF5FB4FF : 0xFF2E5A8F);
        int fill = b.danger() ? (over ? 0xF0521818 : 0xF0301010) : (over ? 0xF0183052 : 0xF00E1A30);
        g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), border);
        g.fill(b.x() + 1, b.y() + 1, b.x() + b.w() - 1, b.y() + b.h() - 1, fill);
        g.drawCenteredString(this.font, b.label(), b.x() + b.w() / 2, b.y() + (b.h() - 8) / 2, over ? 0xFFFFFF : 0xDDE8FF);
    }

    /** Desenha uma carta (moldura da raridade, ícone, nome, raridade, tipo, efeito e escolhas). Usado também pela galeria. */
    public static void drawCard(GuiGraphics g, net.minecraft.client.gui.Font font, CardOffer offer, int x, int y, boolean over, boolean banning) {
        CardType type = offer.type();
        Rarity rarity = offer.rarityValue();
        int y0 = y + (over ? -5 : 0);

        if (over && banning) {
            g.fill(x - 4, y0 - 4, x + CARD_W + 4, y0 + CARD_H + 4, 0x60FF2020);
        } else if (over) {
            // Sob o mouse: a mesma aura da carta (cor da raridade), mais forte e um pouco maior, pulsando de leve.
            int r = rarity.ordinal();
            float pulse = 0.85F + 0.15F * (float) Math.sin(net.minecraft.Util.getMillis() / 260.0);
            CardReveal.halo(g, x, y0, rarity.color(), HOVER_AURA_ALPHA[r] * pulse, HOVER_AURA_RADIUS[r]);
        }
        g.blit(FRAMES[rarity.ordinal()], x, y0, CARD_W, CARD_H, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
        if (over && banning) {
            // Borda vermelha de 3 pixels: esta é a carta que vai ser banida.
            int red = 0xFFFF3030;
            g.fill(x - 4, y0 - 4, x + CARD_W + 4, y0 - 1, red);
            g.fill(x - 4, y0 + CARD_H + 1, x + CARD_W + 4, y0 + CARD_H + 4, red);
            g.fill(x - 4, y0 - 1, x - 1, y0 + CARD_H + 1, red);
            g.fill(x + CARD_W + 1, y0 - 1, x + CARD_W + 4, y0 + CARD_H + 1, red);
        }

        int cx = x + CARD_W / 2;
        if (type == null) {
            g.drawCenteredString(font, "?", cx, y0 + CARD_H / 2, 0xFFFFFF);
            return;
        }
        // Parte livre da moldura, entre o ornamento de cima (com a gema) e o de baixo, em coordenadas da carta.
        int innerWidth = 28 * CARD_SCALE;
        int freeTop = 46;
        int freeBottom = 162;

        List<FormattedCharSequence> lines = font.split(type.describe(rarity), innerWidth);
        float textScale = 1.0F;
        // Bloco: ícone 32 + 6, nome 12, raridade 11, tipo 9 + 8, descrição.
        int fixedHeight = 32 + 6 + 12 + 11 + 9 + 8;
        if (fixedHeight + lines.size() * 10 > freeBottom - freeTop) {
            textScale = 0.8F;
            lines = font.split(type.describe(rarity), (int) (innerWidth / textScale));
        }
        int blockHeight = fixedHeight + Math.round(lines.size() * 10 * textScale);
        int top = y0 + freeTop + Math.max(0, (freeBottom - freeTop - blockHeight) / 2);

        g.pose().pushPose();
        g.pose().translate(cx - 16, top, 0);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.renderItem(new ItemStack(type.icon()), 0, 0);
        g.pose().popPose();

        int ty = top + 32 + 6;
        // Nome em negrito; se não couber na largura da moldura, encolhe até caber.
        Component name = type.displayName().copy().withStyle(ChatFormatting.BOLD);
        int nameWidth = font.width(name);
        int maxNameWidth = innerWidth - 4;
        if (nameWidth > maxNameWidth) {
            float nameScale = maxNameWidth / (float) nameWidth;
            g.pose().pushPose();
            g.pose().translate(cx, ty + (1 - nameScale) * 4, 0);
            g.pose().scale(nameScale, nameScale, 1.0F);
            g.drawCenteredString(font, name, 0, 0, 0xFFFFFF);
            g.pose().popPose();
        } else {
            g.drawCenteredString(font, name, cx, ty, 0xFFFFFF);
        }
        g.drawCenteredString(font, rarity.displayName(), cx, ty + 12, rarity.color());
        g.drawCenteredString(font, type.category().displayName(), cx, ty + 23, type.category().color());

        int descTop = ty + 23 + 9 + 8;
        g.pose().pushPose();
        g.pose().translate(cx, descTop, 0);
        g.pose().scale(textScale, textScale, 1.0F);
        int ly = 0;
        for (FormattedCharSequence line : lines) {
            g.drawCenteredString(font, line, 0, ly, 0xDDDDDD);
            ly += 10;
        }
        g.pose().popPose();

        int picks = ClientRogueData.picks.getOrDefault(type.id(), 0);
        Component pickLine;
        if (type.stack() == CardType.Stack.UNIQUE) {
            pickLine = Component.translatable("screen.roguecards.unique").withStyle(ChatFormatting.GRAY);
        } else if (type.unlimited()) {
            double shown = shownTotal(type, ClientRogueData.bonuses.getOrDefault(type.id(), 0.0));
            boolean overCap = shown > type.cap() + 1e-9;
            pickLine = Component.translatable("screen.roguecards.total_of", type.formatAmount(shown), type.formatAmount(type.cap()))
                    .withStyle(overCap ? ChatFormatting.GOLD : ChatFormatting.GRAY);
        } else {
            pickLine = Component.translatable("screen.roguecards.picks", picks, type.limit()).withStyle(ChatFormatting.GRAY);
        }
        g.pose().pushPose();
        g.pose().translate(cx, y0 + 170, 0);
        g.pose().scale(0.8F, 0.8F, 1.0F);
        g.drawCenteredString(font, pickLine, 0, 0, 0xFFFFFF);
        g.pose().popPose();
    }

    /** Linha com a sorte atual e a chance de cada raridade que já pode aparecer. */
    private void renderChances(GuiGraphics g, int y) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        double luck = this.minecraft.player.getAttributeValue(Attributes.LUCK);
        double[] chances = Rarity.chances(luck, com.roguecards.api.RoguecraftApi.extraRarityLuck(this.minecraft.player));
        MutableComponent line = Component.translatable("screen.roguecards.luck", formatNumber(luck)).withStyle(ChatFormatting.GREEN);
        Rarity next = null;
        for (Rarity rarity : Rarity.values()) {
            double chance = chances[rarity.ordinal()];
            if (chance <= 0) {
                if (next == null) {
                    next = rarity;
                }
                continue;
            }
            line.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(rarity.displayName().copy().withColor(rarity.color()));
            line.append(Component.literal(" " + formatNumber(chance * 100) + "%").withStyle(ChatFormatting.GRAY));
        }
        if (next != null) {
            line.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.translatable("screen.roguecards.next_rarity", next.displayName(), formatNumber(next.minLuck()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (ClientRogueData.pityLeft > 0 && ClientRogueData.pityLeft <= 3) {
            line.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.translatable("screen.roguecards.pity", ClientRogueData.pityLeft).withStyle(ChatFormatting.GREEN));
        } else if (ClientRogueData.pityLeft == 0) {
            line.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.translatable("screen.roguecards.pity_now").withStyle(ChatFormatting.GREEN));
        }
        g.drawCenteredString(this.font, line, vw / 2, y, 0xFFFFFF);
    }

    // ---------- Painel de baixo (design do Codex CLI: ferro escuro com gemas azuis) ----------

    private static final ResourceLocation PANEL = RoguecraftCards.id("textures/gui/panel/frame.png");
    private static final ResourceLocation DIVIDER = RoguecraftCards.id("textures/gui/panel/divider.png");
    private static final ResourceLocation SLOT = RoguecraftCards.id("textures/gui/panel/slot.png");
    private static final int PANEL_TEX_W = 145;
    private static final int PANEL_TEX_H = 48;
    private static final int PANEL_CORNER = 10;
    private static final int DIVIDER_TEX_W = 21;
    private static final int DIVIDER_TEX_H = 141;
    private static final int SLOT_TEX = 22;
    private static final int SLOT_SIZE = 22;
    private static final int SLOT_GAP = 12;
    private static final int LINE_BLUE = 0xFF2F6FE0;

    /** Painel com os bônus por tipo (esquerda) e a trilha de escolhas (direita), separados por uma divisória. */
    private void renderBottom(GuiGraphics g, int y, int mx, int my) {
        int left = 14;
        int right = vw - 14;
        int bottom = vh - 6;
        if (bottom - y < 60) {
            return;
        }
        nineSlice(g, PANEL, left, y, right - left, bottom - y, PANEL_TEX_W, PANEL_TEX_H, PANEL_CORNER);

        int pad = 16;
        int colWidth = (right - left - pad * 2 - DIVIDER_TEX_W - 24) / 2;
        int colLeft = left + pad;
        int dividerX = colLeft + colWidth + 12;
        int colRight = dividerX + DIVIDER_TEX_W + 12;
        int top = y + 10;
        int innerBottom = bottom - 10;

        drawDivider(g, dividerX, y + 4, bottom - y - 8);
        int contentTop = drawHeader(g, colLeft, top, colWidth, new ItemStack(Items.SHIELD),
                Component.translatable("screen.roguecards.bonuses"));
        renderBonusesByCategory(g, colLeft, contentTop, colWidth, innerBottom);
        int trailTop = drawHeader(g, colRight, top, colWidth, new ItemStack(Items.LEATHER_BOOTS),
                Component.translatable("screen.roguecards.trail", ClientRogueData.history.size()));
        renderTrail(g, colRight, trailTop, colWidth, innerBottom, mx, my);
    }

    /** Cabeçalho de uma coluna: ícone, título e uma linha azul com um losango no meio. Retorna onde o conteúdo começa. */
    private int drawHeader(GuiGraphics g, int x, int y, int width, ItemStack icon, Component title) {
        g.renderItem(icon, x, y);
        g.drawString(this.font, title.copy().withStyle(ChatFormatting.BOLD), x + 20, y + 4, 0xFFFFFF);
        int lineY = y + 19;
        int mid = x + width / 2;
        g.fill(x, lineY, mid - 4, lineY + 1, LINE_BLUE);
        g.fill(mid + 5, lineY, x + width, lineY + 1, LINE_BLUE);
        // Losango de 5 pixels.
        g.fill(mid, lineY - 2, mid + 1, lineY + 3, 0xFF9FD4FF);
        g.fill(mid - 1, lineY - 1, mid + 2, lineY + 2, 0xFF9FD4FF);
        g.fill(mid - 2, lineY, mid + 3, lineY + 1, 0xFF9FD4FF);
        g.fill(mid, lineY, mid + 1, lineY + 1, LINE_BLUE);
        return lineY + 7;
    }

    /** Desenha uma textura em 9 partes: os cantos ficam do tamanho original e as bordas e o miolo esticam. */
    private static void nineSlice(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH, int c) {
        int midW = texW - 2 * c;
        int midH = texH - 2 * c;
        int innerW = w - 2 * c;
        int innerH = h - 2 * c;
        g.blit(tex, x, y, c, c, 0, 0, c, c, texW, texH);
        g.blit(tex, x + w - c, y, c, c, texW - c, 0, c, c, texW, texH);
        g.blit(tex, x, y + h - c, c, c, 0, texH - c, c, c, texW, texH);
        g.blit(tex, x + w - c, y + h - c, c, c, texW - c, texH - c, c, c, texW, texH);
        g.blit(tex, x + c, y, innerW, c, c, 0, midW, c, texW, texH);
        g.blit(tex, x + c, y + h - c, innerW, c, c, texH - c, midW, c, texW, texH);
        g.blit(tex, x, y + c, c, innerH, 0, c, c, midH, texW, texH);
        g.blit(tex, x + w - c, y + c, c, innerH, texW - c, c, c, midH, texW, texH);
        g.blit(tex, x + c, y + c, innerW, innerH, c, c, midW, midH, texW, texH);
    }

    /** Divisória vertical: ponta de cima, barra esticada, gema no meio, barra e ponta de baixo. */
    private static void drawDivider(GuiGraphics g, int x, int y, int h) {
        int w = DIVIDER_TEX_W;
        int capTop = 20;
        int capBottom = 19;
        int gemV = 63;
        int gemH = 15;
        int barV = 24;
        int barH = 30;
        g.blit(DIVIDER, x, y, w, capTop, 0, 0, w, capTop, DIVIDER_TEX_W, DIVIDER_TEX_H);
        g.blit(DIVIDER, x, y + h - capBottom, w, capBottom, 0, DIVIDER_TEX_H - capBottom, w, capBottom, DIVIDER_TEX_W, DIVIDER_TEX_H);
        int gemY = y + h / 2 - gemH / 2;
        int bar1 = gemY - (y + capTop);
        int bar2 = (y + h - capBottom) - (gemY + gemH);
        if (bar1 > 0) {
            g.blit(DIVIDER, x, y + capTop, w, bar1, 0, barV, w, barH, DIVIDER_TEX_W, DIVIDER_TEX_H);
        }
        if (bar2 > 0) {
            g.blit(DIVIDER, x, gemY + gemH, w, bar2, 0, barV, w, barH, DIVIDER_TEX_W, DIVIDER_TEX_H);
        }
        g.blit(DIVIDER, x, gemY, w, gemH, 0, gemV, w, gemH, DIVIDER_TEX_W, DIVIDER_TEX_H);
    }

    /** Uma linha por tipo de carta: marcador na cor do tipo, nome do tipo e os valores. */
    private void renderBonusesByCategory(GuiGraphics g, int x, int y, int width, int bottom) {
        Map<String, Double> bonuses = ClientRogueData.bonuses;
        int nameWidth = 0;
        for (CardType.Category category : CardType.Category.values()) {
            nameWidth = Math.max(nameWidth, this.font.width(category.displayName().copy().withStyle(ChatFormatting.BOLD)));
        }
        int valuesX = x + 12 + nameWidth + 14;
        int valuesWidth = Math.max(40, x + width - valuesX);
        int ly = y;
        boolean any = false;
        for (CardType.Category category : CardType.Category.values()) {
            MutableComponent values = Component.empty();
            boolean first = true;
            for (CardType type : CardType.values()) {
                if (type.category() != category) {
                    continue;
                }
                double cardTotal = bonuses.getOrDefault(type.id(), 0.0);
                double shown = shownTotal(type, cardTotal);
                if (Math.abs(shown) < 1e-9) {
                    continue;
                }
                if (!first) {
                    values.append(Component.literal("   "));
                }
                double extra = type == CardType.BLOOD_PACT ? bonuses.getOrDefault(CardType.BLOOD_PACT_HEALTH_KEY, 0.0) : 0;
                values.append(type.describeTotal(shown, extra));
                if (type.stack() == CardType.Stack.SUM && type.hasCap()) {
                    boolean over = shown > type.cap() + 1e-9;
                    values.append(Component.literal(" / " + type.formatAmount(type.cap()))
                            .withStyle(over ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
                }
                first = false;
            }
            if (first) {
                continue;
            }
            any = true;
            if (ly + 9 > bottom) {
                return;
            }
            // Marcador quadrado na cor do tipo, com contorno escuro.
            g.fill(x, ly, x + 8, ly + 8, 0xFF000000);
            g.fill(x + 1, ly + 1, x + 7, ly + 7, 0xFF000000 | category.color());
            g.drawString(this.font, category.displayName().copy().withStyle(ChatFormatting.BOLD), x + 12, ly, 0xFFFFFF);
            g.drawString(this.font, "-", valuesX - 9, ly, 0xFF6F86A8);
            for (FormattedCharSequence line : this.font.split(values, valuesWidth)) {
                if (ly + 9 > bottom) {
                    return;
                }
                g.drawString(this.font, line, valuesX, ly, 0xFFE6EEFF);
                ly += 10;
            }
            ly += 3;
        }
        if (!any) {
            g.drawString(this.font, Component.translatable("screen.roguecards.no_bonuses").withStyle(ChatFormatting.DARK_GRAY), x, ly, 0xFFFFFF);
        }
    }

    /**
     * Trilha: um quadradinho por carta escolhida, na ordem, com a borda na cor da raridade,
     * o número da escolha no canto e uma seta até o próximo. Se não couber, mostra as mais recentes.
     */
    private void renderTrail(GuiGraphics g, int x, int y, int width, int bottom, int mx, int my) {
        List<CardOffer> history = ClientRogueData.history;
        if (history.isEmpty()) {
            g.drawString(this.font, Component.translatable("screen.roguecards.no_trail").withStyle(ChatFormatting.DARK_GRAY), x, y + 2, 0xFFFFFF);
            return;
        }
        int step = SLOT_SIZE + SLOT_GAP;
        int rowStep = SLOT_SIZE + 6;
        int perRow = Math.max(1, (width + SLOT_GAP) / step);
        int rows = Math.max(1, (bottom - y + 6) / rowStep);
        int capacity = perRow * rows;
        int start = 0;
        int slot = 0;
        if (history.size() > capacity) {
            // A primeira posição mostra "+N" com quantas escolhas antigas ficaram de fora.
            start = history.size() - (capacity - 1);
            slot = 1;
            g.drawCenteredString(this.font, Component.translatable("screen.roguecards.trail_more", start).withStyle(ChatFormatting.GRAY),
                    x + SLOT_SIZE / 2, y + 7, 0xFFFFFF);
        }
        CardOffer hoveredEntry = null;
        int hoveredIndex = -1;
        for (int i = start; i < history.size(); i++, slot++) {
            int sx = x + (slot % perRow) * step;
            int sy = y + (slot / perRow) * rowStep;
            CardOffer entry = history.get(i);
            int color = entry.rarityValue().color();
            RenderSystem.setShaderColor(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, 1.0F);
            g.blit(SLOT, sx, sy, SLOT_SIZE, SLOT_SIZE, 0, 0, SLOT_TEX, SLOT_TEX, SLOT_TEX, SLOT_TEX);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            CardType type = entry.type();
            if (type != null) {
                g.renderItem(new ItemStack(type.icon()), sx + 3, sy + 3);
            }
            // Número da escolha no canto de cima, por cima do ícone.
            g.pose().pushPose();
            g.pose().translate(sx + 3, sy + 2, 200);
            g.pose().scale(0.6F, 0.6F, 1.0F);
            g.drawString(this.font, String.valueOf(i + 1), 0, 0, 0xFFFFFF, true);
            g.pose().popPose();
            // Seta até a próxima carta, se ela estiver na mesma fileira.
            if (i + 1 < history.size() && (slot + 1) % perRow != 0) {
                int ax = sx + SLOT_SIZE + 2;
                int ay = sy + SLOT_SIZE / 2;
                g.fill(ax, ay, ax + 1, ay + 1, 0xFF9FD4FF);
                g.fill(ax + 2, ay, ax + 3, ay + 1, 0xFF9FD4FF);
                g.fill(ax + 4, ay, ax + 7, ay + 1, 0xFFDDEBFF);
                g.fill(ax + 5, ay - 1, ax + 6, ay + 2, 0xFFDDEBFF);
                g.fill(ax + 4, ay - 2, ax + 5, ay + 3, 0xFFDDEBFF);
            }
            if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) {
                hoveredEntry = entry;
                hoveredIndex = i;
            }
        }
        if (hoveredEntry != null && hoveredEntry.type() != null) {
            CardType type = hoveredEntry.type();
            Rarity rarity = hoveredEntry.rarityValue();
            trailTooltip = List.of(
                    Component.translatable("screen.roguecards.trail_pick", hoveredIndex + 1).withStyle(ChatFormatting.GRAY),
                    type.displayName().copy().withStyle(ChatFormatting.BOLD),
                    rarity.displayName().copy().withColor(rarity.color()),
                    type.category().displayName().copy().withColor(type.category().color()),
                    type.describe(rarity));
            tooltipX = mx;
            tooltipY = my;
        }
    }

    /** Janela de confirmação do banimento, por cima de tudo. Só os botões dela respondem ao clique. */
    private void renderBanConfirm(GuiGraphics g, int mx, int my) {
        CardOffer offer = ClientRogueData.offer.get(confirmBan);
        CardType type = offer.type();
        Rarity rarity = offer.rarityValue();
        buttons.clear();
        hovered = -1;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(0, 0, vw, vh, 0xB0000000);
        int w = 300;
        int h = 112;
        int x = (vw - w) / 2;
        int y = CARDS_TOP + CARD_H / 2 - h / 2;
        nineSlice(g, PANEL, x, y, w, h, PANEL_TEX_W, PANEL_TEX_H, PANEL_CORNER);
        int cx = x + w / 2;
        g.drawCenteredString(this.font, Component.translatable("screen.roguecards.ban_confirm_title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                cx, y + 16, 0xFFFFFF);
        Component name = type == null ? Component.literal(offer.card())
                : type.displayName().copy().withStyle(ChatFormatting.BOLD);
        g.drawCenteredString(this.font, Component.translatable("screen.roguecards.ban_confirm", name,
                rarity.displayName().copy().withColor(rarity.color())), cx, y + 34, 0xFFFFFF);
        int ly = y + 50;
        for (FormattedCharSequence line : this.font.split(Component.translatable("screen.roguecards.ban_confirm_help")
                .withStyle(ChatFormatting.GRAY), w - 40)) {
            g.drawCenteredString(this.font, line, cx, ly, 0xFFFFFF);
            ly += 10;
        }
        String cardId = offer.card();
        buttons.add(new Btn(cx - 104, y + h - 30, 100, 18, Component.translatable("screen.roguecards.ban_confirm_yes"), () -> {
            waitingVersion = ClientRogueData.version;
            banMode = false;
            confirmBan = -1;
            reveal.expectBan();
            PacketDistributor.sendToServer(CardActionPayload.ban(cardId));
        }, true));
        buttons.add(new Btn(cx + 4, y + h - 30, 100, 18, Component.translatable("screen.roguecards.cancel"), () -> confirmBan = -1));
        for (Btn b : buttons) {
            drawButton(g, b, mx, my);
        }
        g.pose().popPose();
    }

    /**
     * Total que aparece na tela. Para Sorte e Sabedoria é o valor real do personagem
     * (cartas + berloque + poções), que pode passar do teto das cartas. Exemplo: 50 das cartas + berloque de ferro = 51.
     * Para as outras cartas, é a soma das cartas.
     */
    private static double shownTotal(CardType type, double cardTotal) {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return cardTotal;
        }
        if (type == CardType.LUCK) {
            return player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
        }
        if (type == CardType.WISDOM) {
            return player.getAttributeValue(RoguecraftCards.XP_GAIN) - 1.0;
        }
        return cardTotal;
    }

    private static String formatNumber(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    // ---------- Entrada ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (reveal.busy()) {
            // Clique durante a revelação: pula para o fim. Não escolhe carta nem aperta botão.
            reveal.skip();
            return true;
        }
        double mx = mouseX / scale;
        double my = mouseY / scale;
        for (Btn b : List.copyOf(buttons)) {
            if (b.contains(mx, my)) {
                if (!busy()) {
                    b.action().run();
                    click();
                }
                return true;
            }
        }
        if (confirmBan >= 0) {
            return true;
        }
        if (banMode && hovered >= 0 && !busy()) {
            confirmBan = hovered;
            click();
            return true;
        }
        if (!banMode && hovered >= 0 && ClientRogueData.pending > 0 && !busy()) {
            waitingVersion = ClientRogueData.version;
            choseSomething = true;
            PacketDistributor.sendToServer(new ChooseCardPayload(hovered));
            // Sem clique aqui: o servidor toca o som de carta escolhida (ModSounds.CARD_PICK).
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        reveal.stopCharge();
        super.removed();
    }

    private void click() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Esc fecha primeiro a confirmação, depois sai do modo banir, e só então fecha a tela.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && (confirmBan >= 0 || banMode)) {
            if (confirmBan >= 0) {
                confirmBan = -1;
            } else {
                banMode = false;
            }
            return true;
        }
        if (RoguecraftCardsKeys.OPEN_CARDS.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
