package com.roguecards.xp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.roguecards.api.card.CardOffer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Dados do jogador guardados no save: nível azul, XP azul, cartas e o estado das habilidades. */
public class RogueData {
    public int level;
    /** XP azul dentro do nível atual. */
    public int xp;
    /** Quantas cartas o jogador ainda tem para escolher. */
    public int pending;
    /** As cartas oferecidas agora. Fica salva para a oferta não mudar ao sair e voltar. */
    public final List<CardOffer> offer = new ArrayList<>();
    /** Total de cada carta (id → valor somado ou melhor valor). */
    public final Map<String, Double> bonuses = new HashMap<>();
    /** Quantas vezes cada carta foi escolhida (id → vezes). */
    public final Map<String, Integer> picks = new HashMap<>();
    /** Trilha: todas as cartas escolhidas, na ordem (carta + raridade). */
    public final List<CardOffer> history = new ArrayList<>();
    /** Cartas banidas pela carta Banir. */
    public final List<String> banned = new ArrayList<>();
    /** Trocas que ainda restam na escolha atual (renovam a cada escolha nova). */
    public int rerollsLeft;
    /** Proteção contra azar: ofertas seguidas só com cartas comuns, contando só quando a incomum já podia sair. */
    public int commonStreak;
    /** Ofertas desde que a Trocar Cartas liberou sem ela aparecer; -1 quando a garantia já foi cumprida. */
    public int rerollWait;
    /** Momento (tempo do mundo, em ticks) em que a Segunda Chance volta a funcionar. */
    public long secondChanceReadyAt;

    /** Não é salvo: tick da última esfera azul coletada. */
    public int lastPickupTick = -100;
    /** Não é salvo: tick do último dano dado ou recebido (para a Regeneração). */
    public int lastCombatTick = -10000;

    public static final Codec<RogueData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("level", 0).forGetter(d -> d.level),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(d -> d.xp),
            Codec.INT.optionalFieldOf("pending", 0).forGetter(d -> d.pending),
            CardOffer.CODEC.listOf().optionalFieldOf("offer", List.of()).forGetter(d -> d.offer),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("bonuses", Map.of()).forGetter(d -> d.bonuses),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("picks", Map.of()).forGetter(d -> d.picks),
            Codec.STRING.listOf().optionalFieldOf("banned", List.of()).forGetter(d -> d.banned),
            Codec.INT.optionalFieldOf("rerolls_left", 0).forGetter(d -> d.rerollsLeft),
            Codec.LONG.optionalFieldOf("second_chance_ready_at", 0L).forGetter(d -> d.secondChanceReadyAt),
            CardOffer.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(d -> d.history),
            Codec.INT.optionalFieldOf("common_streak", 0).forGetter(d -> d.commonStreak),
            Codec.INT.optionalFieldOf("reroll_wait", 0).forGetter(d -> d.rerollWait)
    ).apply(i, RogueData::new));

    public RogueData() {
    }

    private RogueData(int level, int xp, int pending, List<CardOffer> offer, Map<String, Double> bonuses,
                      Map<String, Integer> picks, List<String> banned, int rerollsLeft, long secondChanceReadyAt,
                      List<CardOffer> history, int commonStreak, int rerollWait) {
        this.level = level;
        this.xp = xp;
        this.pending = pending;
        this.offer.addAll(offer);
        this.bonuses.putAll(bonuses);
        this.picks.putAll(picks);
        this.banned.addAll(banned);
        this.rerollsLeft = rerollsLeft;
        this.secondChanceReadyAt = secondChanceReadyAt;
        this.history.addAll(history);
        this.commonStreak = commonStreak;
        this.rerollWait = rerollWait;
    }

    public void reset() {
        level = 0;
        xp = 0;
        pending = 0;
        offer.clear();
        bonuses.clear();
        picks.clear();
        banned.clear();
        rerollsLeft = 0;
        secondChanceReadyAt = 0;
        history.clear();
        commonStreak = 0;
        rerollWait = 0;
    }
}
