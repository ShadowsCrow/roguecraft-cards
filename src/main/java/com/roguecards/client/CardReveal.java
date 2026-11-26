package com.roguecards.client;

import java.util.List;
import java.util.Random;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.roguecards.ClientConfig;
import com.roguecards.ModSounds;
import com.roguecards.RoguecraftCards;
import com.roguecards.api.card.CardOffer;
import com.roguecards.api.card.Rarity;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Revelação animada das cartas na tela de cartas.
 *
 * As cartas chegam viradas para baixo. Antes de virar, o verso brilha na cor da raridade. Depois elas viram
 * uma a uma (giro no eixo vertical: a carta afina, mostra a frente e volta à largura normal).
 * Os efeitos crescem com a raridade:
 * comum = só a virada; incomum = brilho suave; rara = brilho forte e faíscas;
 * épica = brilho pulsando, faíscas e tremor de 1 pixel;
 * lendária = fundo escurece, raios girando atrás da carta, tremor de 2 pixels, 0,5 s a mais antes de virar
 * e um brilho que continua enquanto a tela estiver aberta.
 * Depois de reveladas, cartas raras ou melhores mantêm um brilho sutil na moldura.
 *
 * Ritmo (exemplo com 3 cartas, a primeira oferta da tela): as viradas começam em 0,65 s, 0,85 s e 1,05 s.
 * Se a carta do meio for lendária: 0,65 s, 1,35 s e 1,55 s. As ofertas seguintes (escolhas acumuladas,
 * troca de cartas) viram rápido, a cada 0,1 s. Um clique durante a animação pula para o fim.
 */
final class CardReveal {
    static final ResourceLocation BACK = RoguecraftCards.id("textures/gui/card/back.png");

    /** Tempos da animação, em milissegundos. */
    private record Timing(int arrive, int base, int gap, int antic, int legendaryExtra, int flip) {
    }

    private static final Timing FULL = new Timing(260, 650, 200, 420, 500, 260);
    private static final Timing FAST = new Timing(120, 170, 100, 160, 250, 140);
    private static final long DONE = Long.MIN_VALUE / 4;

    private List<CardOffer> cards = List.of();
    private Timing timing = FULL;
    private long start;
    private boolean animated;
    private boolean skipped;
    private boolean hadReveal;
    private boolean expectBan;
    private int generation;
    private long lastFlipEnd;

    private long[] arriveStart = new long[0];
    private long[] anticStart = new long[0];
    private long[] flipStart = new long[0];
    private boolean[] chargePlayed = new boolean[0];
    private boolean[] flipPlayed = new boolean[0];
    private boolean[] revealPlayed = new boolean[0];
    private SoundInstance charge;

    /** Só para os prints automáticos: tempo fixo da animação (-1 = tempo real) e sem sons. */
    long fixedElapsed = -1;
    boolean muted;

    private int screenW = 640;
    private int screenH = 400;

    /** Avisa que o próximo pacote do servidor é um banimento: só a vaga trocada vira de novo. */
    void expectBan() {
        expectBan = true;
    }

    /** Só para os prints automáticos: a próxima oferta tem a animação completa, como a primeira da tela. */
    void forceFull() {
        hadReveal = false;
    }

    void setBounds(int vw, int vh) {
        screenW = vw;
        screenH = vh;
    }

    private long now() {
        return fixedElapsed >= 0 ? fixedElapsed : Util.getMillis() - start;
    }

    /** Confere se a oferta mudou e, se mudou, começa uma revelação nova. Chamado a cada quadro. */
    void update(List<CardOffer> offer) {
        if (offer.equals(cards)) {
            playDueSounds();
            return;
        }
        List<CardOffer> old = cards;
        cards = List.copyOf(offer);
        generation++;
        int n = cards.size();
        boolean onlyChanged = expectBan && old.size() == n;
        expectBan = false;
        arriveStart = new long[n];
        anticStart = new long[n];
        flipStart = new long[n];
        chargePlayed = new boolean[n];
        flipPlayed = new boolean[n];
        revealPlayed = new boolean[n];
        skipped = false;
        start = Util.getMillis();
        if (n == 0) {
            animated = false;
            return;
        }
        boolean[] animate = new boolean[n];
        for (int i = 0; i < n; i++) {
            animate[i] = !onlyChanged || !old.get(i).equals(cards.get(i));
        }
        if (!ClientConfig.REVEAL_ANIMATION.get()) {
            // Sem animação: as cartas já aparecem viradas; toca só o som da melhor raridade nova.
            animated = false;
            Rarity best = null;
            for (int i = 0; i < n; i++) {
                if (animate[i] && (best == null || cards.get(i).rarityValue().ordinal() > best.ordinal())) {
                    best = cards.get(i).rarityValue();
                }
            }
            if (best != null) {
                playReveal(best);
            }
            return;
        }
        animated = true;
        timing = hadReveal ? FAST : FULL;
        hadReveal = true;
        int k = 0;
        int legendaries = 0;
        lastFlipEnd = 0;
        for (int i = 0; i < n; i++) {
            if (!animate[i]) {
                arriveStart[i] = anticStart[i] = flipStart[i] = DONE;
                chargePlayed[i] = flipPlayed[i] = revealPlayed[i] = true;
                continue;
            }
            boolean legendary = cards.get(i).rarityValue() == Rarity.LEGENDARY;
            if (legendary) {
                legendaries++;
            }
            flipStart[i] = timing.base() + (long) k * timing.gap() + (long) legendaries * timing.legendaryExtra();
            arriveStart[i] = onlyChanged ? 0 : k * 60L;
            long antic = timing.antic() + (legendary ? timing.legendaryExtra() : 0);
            anticStart[i] = Math.max(arriveStart[i] + timing.arrive(), flipStart[i] - antic);
            lastFlipEnd = Math.max(lastFlipEnd, flipStart[i] + timing.flip());
            k++;
        }
        playDueSounds();
    }

    /** True enquanto alguma carta ainda não terminou de virar. */
    boolean busy() {
        return animated && !skipped && now() < lastFlipEnd;
    }

    boolean revealed(int i) {
        return !animated || skipped || i >= flipStart.length || now() >= flipStart[i] + timing.flip() / 2;
    }

    /** Clique durante a animação: tudo vira na hora. Toca só o som da melhor raridade que ainda faltava. */
    void skip() {
        if (!busy()) {
            return;
        }
        Rarity best = null;
        for (int i = 0; i < cards.size(); i++) {
            if (!revealPlayed[i] && (best == null || cards.get(i).rarityValue().ordinal() > best.ordinal())) {
                best = cards.get(i).rarityValue();
            }
            chargePlayed[i] = flipPlayed[i] = revealPlayed[i] = true;
        }
        stopCharge();
        skipped = true;
        if (best != null) {
            playReveal(best);
        }
    }

    void stopCharge() {
        if (charge != null) {
            Minecraft.getInstance().getSoundManager().stop(charge);
            charge = null;
        }
    }

    // ---------- Sons ----------

    private void playDueSounds() {
        if (!animated || skipped) {
            return;
        }
        long t = now();
        for (int i = 0; i < cards.size(); i++) {
            Rarity rarity = cards.get(i).rarityValue();
            if (!chargePlayed[i] && rarity == Rarity.LEGENDARY && t >= anticStart[i]) {
                chargePlayed[i] = true;
                if (timing == FULL) {
                    charge = play(ModSounds.LEGENDARY_CHARGE.get(), 0.9F, 1.0F);
                }
            }
            if (!flipPlayed[i] && t >= flipStart[i]) {
                flipPlayed[i] = true;
                play(ModSounds.CARD_FLIP.get(), timing == FULL ? 0.8F : 0.6F, 0.95F + 0.1F * (i % 3) / 2F);
            }
            if (!revealPlayed[i] && t >= flipStart[i] + timing.flip() / 2) {
                revealPlayed[i] = true;
                // Nas viradas rápidas, só as raras ou melhores têm som próprio (senão vira uma chuva de sinos).
                if (timing == FULL || rarity.ordinal() >= Rarity.RARE.ordinal()) {
                    playReveal(rarity);
                }
            }
        }
    }

    private void playReveal(Rarity rarity) {
        play(ModSounds.REVEAL.get(rarity.ordinal()).get(), 1.0F, 1.0F);
    }

    private SoundInstance play(SoundEvent event, float volume, float pitch) {
        if (muted || !ClientConfig.REVEAL_SOUNDS.get()) {
            return null;
        }
        SoundInstance sound = new SimpleSoundInstance(event.getLocation(), SoundSource.PLAYERS, volume, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        Minecraft.getInstance().getSoundManager().play(sound);
        return sound;
    }

    // ---------- Desenho ----------

    /** Escurecimento do fundo (0 a 1) enquanto uma lendária se prepara para virar e logo depois. */
    float darkness() {
        if (!animated || skipped) {
            return 0;
        }
        long t = now();
        float dark = 0;
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).rarityValue() != Rarity.LEGENDARY || flipStart[i] == DONE) {
                continue;
            }
            long reveal = flipStart[i] + timing.flip() / 2;
            float d;
            if (t < anticStart[i]) {
                d = 0;
            } else if (t < reveal + 500) {
                d = Math.min(1, (t - anticStart[i]) / 250F);
            } else {
                d = Math.max(0, 1 - (t - reveal - 500) / 1000F);
            }
            dark = Math.max(dark, d * 0.6F);
        }
        return dark;
    }

    boolean isLegendary(int i) {
        return i < cards.size() && cards.get(i).rarityValue() == Rarity.LEGENDARY;
    }

    /**
     * Desenha a carta i na fase atual da animação, com os efeitos de trás (brilho, raios) e da frente
     * (clarão, faíscas). Fora da animação desenha a carta normal com o brilho que fica.
     */
    void renderCard(GuiGraphics g, Font font, int i, CardOffer offer, int x, int y, boolean over, boolean banning) {
        if (!ClientConfig.REVEAL_ANIMATION.get()) {
            CardScreen.drawCard(g, font, offer, x, y, over, banning);
            return;
        }
        Rarity rarity = offer.rarityValue();
        int r = rarity.ordinal();
        long t = now();
        boolean active = animated && !skipped && i < flipStart.length && flipStart[i] != DONE;
        long since = active ? t - flipStart[i] : Long.MAX_VALUE / 4;
        int flip = timing.flip();
        long reveal = flip / 2;

        if (active && t < arriveStart[i]) {
            return;
        }
        // Chegada: sobe 24 pixels e aparece.
        float arrive = active ? Math.min(1, (t - arriveStart[i]) / (float) timing.arrive()) : 1;
        float arriveEase = 1 - (1 - arrive) * (1 - arrive) * (1 - arrive);
        int dy = Math.round(24 * (1 - arriveEase));

        // Expectativa: de 0 (começo) a 1 (hora de virar).
        float antic = anticipation(i, since);

        // Tremor: épica 1 pixel (fim da expectativa e logo depois), lendária 2 pixels (toda a expectativa).
        int shake = 0;
        if (active && since < reveal + 200) {
            if (rarity == Rarity.LEGENDARY && antic > 0) {
                shake = 2;
            } else if (rarity == Rarity.EPIC && (antic > 0.5F || since >= 0)) {
                shake = 1;
            }
        }
        int sx = shake == 0 ? 0 : Math.round(shake * (float) Math.sin(t * 0.09 + i * 1.7));
        int sy = shake == 0 ? 0 : Math.round(shake * (float) Math.cos(t * 0.113 + i * 2.3));

        int cx = x + CardScreen.CARD_W / 2;
        int cy = y + CardScreen.CARD_H / 2;
        float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 380.0 + i);
        if (fixedElapsed >= 0) {
            pulse = 0.5F + 0.5F * (float) Math.sin(t / 380.0 + i);
        }

        int glowRadius = 0;
        float glowAlpha = 0;
        if (since < reveal && antic > 0) {
            float strength = new float[] {0.25F, 0.4F, 0.55F, 0.65F, 0.8F}[r];
            glowRadius = Math.round(4 + antic * new int[] {4, 6, 10, 12, 16}[r]);
            glowAlpha = strength * antic * (r >= Rarity.EPIC.ordinal() ? 0.7F + 0.3F * pulse : 1);
        } else if (since >= reveal) {
            long after = since - reveal;
            float burst = Math.max(0, 1 - after / 700F);
            float burstAlpha = new float[] {0, 0.35F, 0.55F, 0.65F, 0.85F}[r] * burst;
            if (r == Rarity.EPIC.ordinal()) {
                burstAlpha *= 0.75F + 0.25F * pulse;
            }
            float keep = new float[] {0, 0, 0.16F, 0.2F, 0.3F}[r] * (0.7F + 0.3F * pulse);
            glowAlpha = Math.max(burstAlpha, keep);
            glowRadius = burstAlpha > keep ? new int[] {0, 8, 12, 14, 18}[r] : new int[] {0, 0, 6, 8, 11}[r];
        }

        // ---- A carta (o brilho fica dentro da mesma transformação: sobe, treme e afina junto) ----
        g.pose().pushPose();
        g.pose().translate(sx, sy + dy, 0);
        boolean front = !active || since >= reveal;
        float scaleX = 1;
        float scaleY = 1;
        if (active && since >= 0 && since < flip) {
            float p = since / (float) flip;
            scaleX = Math.max(0.02F, Math.abs((float) Math.cos(p * Math.PI)));
            scaleY = 1 + 0.06F * (float) Math.sin(p * Math.PI);
        }
        if (scaleX != 1 || scaleY != 1) {
            g.pose().translate(cx, cy, 0);
            g.pose().scale(scaleX, scaleY, 1);
            g.pose().translate(-cx, -cy, 0);
        }
        // A carta sob o mouse desenha a própria aura mais forte (em drawCard); aqui fica só a aura normal.
        boolean hoverAura = front && over && !busy();
        if (glowAlpha > 0.01F && glowRadius > 0 && !hoverAura) {
            halo(g, x, y, rarity.color(), glowAlpha * arriveEase, glowRadius);
        }
        if (front) {
            CardScreen.drawCard(g, font, offer, x, y, over && !busy(), banning);
        } else {
            float alpha = arriveEase;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, alpha);
            g.blit(BACK, x, y, CardScreen.CARD_W, CardScreen.CARD_H, 0, 0, CardScreen.TEX_W, CardScreen.TEX_H, CardScreen.TEX_W, CardScreen.TEX_H);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            if (antic > 0) {
                // O verso acende na cor da raridade (soma de luz, só onde a carta tem pixels).
                float glow = antic * new float[] {0.25F, 0.4F, 0.5F, 0.55F, 0.7F}[r];
                if (r >= Rarity.EPIC.ordinal()) {
                    glow *= 0.7F + 0.3F * pulse;
                }
                additive(g, BACK, x, y, rarity.color(), glow);
            }
        }
        // Clarão logo depois de virar (incomum ou melhor mais forte).
        if (active && since >= reveal && since < reveal + 220) {
            float flash = (1 - (since - reveal) / 220F) * (r == 0 ? 0.3F : 0.75F);
            additive(g, CardScreen.FRAMES[r], x, y, 0xFFFFFF, flash);
        }
        g.pose().popPose();

        // ---- Na frente: faíscas (rara ou melhor) ----
        if (active && since >= reveal && r >= Rarity.RARE.ordinal()) {
            drawSparks(g, i, cx, cy, rarity, since - reveal);
        }
    }

    /** Expectativa da carta i: de 0 (começo) a 1 (hora de virar); continua 1 até a frente aparecer. */
    private float anticipation(int i, long since) {
        boolean active = animated && !skipped && i < flipStart.length && flipStart[i] != DONE;
        if (!active) {
            return 0;
        }
        if (since >= 0) {
            return since < timing.flip() / 2 ? 1 : 0;
        }
        long t = now();
        if (t < anticStart[i]) {
            return 0;
        }
        return Math.min(1, (t - anticStart[i]) / (float) Math.max(1, flipStart[i] - anticStart[i]));
    }

    /**
     * Raios e aura girando atrás da lendária i: crescem na expectativa, giram rápido na revelação
     * e depois ficam fracos e lentos enquanto a tela estiver aberta. Não faz nada para outras raridades.
     */
    void renderRays(GuiGraphics g, int i, int x, int y) {
        if (!ClientConfig.REVEAL_ANIMATION.get() || !isLegendary(i)) {
            return;
        }
        long t = now();
        boolean active = animated && !skipped && i < flipStart.length && flipStart[i] != DONE;
        long since = active ? t - flipStart[i] : Long.MAX_VALUE / 4;
        long reveal = timing.flip() / 2;
        float antic = anticipation(i, since);
        float rays;
        float speed;
        if (since < reveal) {
            rays = antic;
            speed = 0.0012F + antic * 0.002F;
        } else if (since < reveal + 900) {
            rays = 1;
            speed = 0.0032F;
        } else {
            float settle = Math.min(1, (since - reveal - 900) / 800F);
            rays = 1 - settle * 0.7F;
            speed = 0.0032F - settle * 0.0024F;
        }
        long clock = fixedElapsed >= 0 ? t : Util.getMillis();
        float pulse = 0.5F + 0.5F * (float) Math.sin(clock / 380.0 + i);
        int cx = x + CardScreen.CARD_W / 2;
        int cy = y + CardScreen.CARD_H / 2;
        drawRays(g, cx, cy, Rarity.LEGENDARY.color(), rays * (0.55F + 0.15F * pulse), clock * speed);
    }

    /** Desenha uma textura de carta somando luz na cor pedida (só nos pixels visíveis da textura). */
    private static void additive(GuiGraphics g, ResourceLocation tex, int x, int y, int color, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, Math.min(1, alpha));
        g.blit(tex, x, y, CardScreen.CARD_W, CardScreen.CARD_H, 0, 0, CardScreen.TEX_W, CardScreen.TEX_H, CardScreen.TEX_W, CardScreen.TEX_H);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
    }

    /**
     * Brilho em volta do corpo da moldura: camadas cada vez maiores, com cantos cortados (parece arredondado)
     * e mais fracas para fora. Exemplo com raio 12: 12 camadas, cada uma 1 pixel maior que a anterior.
     */
    static void halo(GuiGraphics g, int x, int y, int color, float alpha, int radius) {
        int left = x + 8;
        int top = y + 16;
        int right = x + CardScreen.CARD_W - 8;
        int bottom = y + CardScreen.CARD_H - 16;
        int rgb = color & 0xFFFFFF;
        float layer = Math.min(1, alpha * 2.4F / radius);
        g.drawManaged(() -> {
            for (int k = radius; k >= 1; k--) {
                int c = Math.min(k + 2, 2 + Math.round(k * 0.7F));
                int col = argb(layer, rgb);
                g.fill(left - k + c, top - k, right + k - c, bottom + k, col);
                g.fill(left - k, top - k + c, left - k + c, bottom + k - c, col);
                g.fill(right + k - c, top - k + c, right + k, bottom + k - c, col);
            }
        });
    }

    /** Raios girando atrás da lendária, do centro da carta para fora, sumindo nas pontas. Não passam da borda da tela. */
    private void drawRays(GuiGraphics g, int cx, int cy, int color, float alpha, float angle) {
        if (alpha <= 0.01F) {
            return;
        }
        float maxLen = Math.min(170, Math.min(Math.min(cx, screenW - cx), Math.min(cy, screenH - cy)) - 2);
        if (maxLen < 40) {
            return;
        }
        int rgb = color & 0xFFFFFF;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        int rays = 14;
        for (int k = 0; k < rays; k++) {
            double a = angle + k * (Math.PI * 2 / rays);
            float len = maxLen * (k % 2 == 0 ? 1.0F : 0.72F);
            float half = k % 2 == 0 ? 0.085F : 0.06F;
            int inner = argb(alpha * (k % 2 == 0 ? 0.9F : 0.6F), rgb);
            int outer = argb(0, rgb);
            float x1 = cx + (float) Math.cos(a - half) * len;
            float y1 = cy + (float) Math.sin(a - half) * len;
            float x2 = cx + (float) Math.cos(a + half) * len;
            float y2 = cy + (float) Math.sin(a + half) * len;
            // Triângulo como quadrilátero com dois vértices iguais; as duas ordens para não sumir por estar de costas.
            vc.addVertex(m, cx, cy, 0).setColor(inner);
            vc.addVertex(m, x1, y1, 0).setColor(outer);
            vc.addVertex(m, x2, y2, 0).setColor(outer);
            vc.addVertex(m, cx, cy, 0).setColor(inner);
            vc.addVertex(m, cx, cy, 0).setColor(inner);
            vc.addVertex(m, x2, y2, 0).setColor(outer);
            vc.addVertex(m, x1, y1, 0).setColor(outer);
            vc.addVertex(m, cx, cy, 0).setColor(inner);
        }
        g.flush();
    }

    /** Faíscas que saem da borda da carta logo depois de ela virar. Sempre as mesmas para a mesma oferta. */
    private void drawSparks(GuiGraphics g, int i, int cx, int cy, Rarity rarity, long after) {
        int count = rarity == Rarity.LEGENDARY ? 30 : rarity == Rarity.EPIC ? 18 : 10;
        if (after > 1100) {
            return;
        }
        Random random = new Random(generation * 7919L + i * 104729L);
        int rgb = rarity.color() & 0xFFFFFF;
        g.drawManaged(() -> {
            for (int k = 0; k < count; k++) {
                double angle = random.nextDouble() * Math.PI * 2;
                float speed = 70 + random.nextFloat() * 110;
                float life = 450 + random.nextFloat() * 450;
                float delay = random.nextFloat() * 120;
                boolean big = rarity == Rarity.LEGENDARY && random.nextFloat() < 0.35F;
                boolean white = random.nextFloat() < 0.4F;
                float age = after - delay;
                if (age < 0 || age > life) {
                    continue;
                }
                float sec = age / 1000F;
                float dx = (float) Math.cos(angle);
                float dy = (float) Math.sin(angle);
                // Começa na borda da moldura visível (66 x 100 a partir do centro).
                float edge = Math.min(66 / Math.max(0.01F, Math.abs(dx)), 100 / Math.max(0.01F, Math.abs(dy)));
                float px = cx + dx * (edge + speed * sec);
                float py = cy + dy * (edge + speed * sec) + 70 * sec * sec;
                int size = big ? 3 : 2;
                if (px < 0 || py < 0 || px + size > screenW || py + size > screenH) {
                    continue;
                }
                float a = 1 - age / life;
                int ix = Math.round(px);
                int iy = Math.round(py);
                g.fill(ix, iy, ix + size, iy + size, argb(a, white ? 0xFFFFFF : rgb));
                if (big) {
                    g.fill(ix - 1, iy + 1, ix + size + 1, iy + 2, argb(a * 0.5F, rgb));
                    g.fill(ix + 1, iy - 1, ix + 2, iy + size + 1, argb(a * 0.5F, rgb));
                }
            }
        });
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (a << 24) | rgb;
    }
}
