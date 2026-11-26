package com.roguecards.client;

import java.util.List;
import java.util.Map;

import com.roguecards.api.card.CardOffer;
import com.roguecards.network.SyncRoguePayload;

import net.minecraft.client.Minecraft;

/** Cópia, no cliente, do que o servidor mandou. A barra e a tela leem daqui. */
public final class ClientRogueData {
    public static int level;
    public static int xp;
    public static int xpNeeded = 1;
    public static int pending;
    /** True quando o jogador chegou ao nível azul máximo (o servidor manda xpNeeded = 0). */
    public static boolean maxed;
    public static List<CardOffer> offer = List.of();
    public static Map<String, Double> bonuses = Map.of();
    public static Map<String, Integer> picks = Map.of();
    public static List<String> banned = List.of();
    public static int rerollsLeft;
    public static int banTokens;
    public static List<CardOffer> history = List.of();
    /** Ofertas até a proteção contra azar garantir uma incomum (-1 = não vale agora). */
    public static int pityLeft = -1;
    /** Aumenta a cada atualização. A tela usa para saber que o clique foi processado. */
    public static int version;

    private ClientRogueData() {
    }

    public static void apply(SyncRoguePayload payload) {
        level = payload.level();
        xp = payload.xp();
        maxed = payload.xpNeeded() <= 0;
        xpNeeded = Math.max(1, payload.xpNeeded());
        pending = payload.pending();
        offer = List.copyOf(payload.offer());
        bonuses = Map.copyOf(payload.bonuses());
        picks = Map.copyOf(payload.picks());
        banned = List.copyOf(payload.banned());
        rerollsLeft = payload.rerollsLeft();
        banTokens = payload.banTokens();
        history = List.copyOf(payload.history());
        pityLeft = payload.pityLeft();
        version++;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CardScreen screen) {
            screen.onSync();
        }
    }

    public static float progress() {
        if (maxed) {
            return 1.0F;
        }
        return Math.min(1.0F, xp / (float) xpNeeded);
    }
}
