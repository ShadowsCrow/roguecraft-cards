package com.roguecards.xp;

import com.roguecards.RoguecraftCards;
import com.roguecards.api.card.CardOffer;
import com.roguecards.api.card.CardType;
import com.roguecards.api.card.Rarity;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Conquistas da aba "Roguecraft Cards" (data/roguecards/advancement).
 * Quase todas usam o critério "minecraft:impossible" e são dadas por código, aqui.
 * A do trevo de quatro folhas é só de dados (pegar o item no inventário).
 * Ao entrar no mundo, checkAll dá as que o jogador já merece (saves de antes das conquistas).
 */
public final class RogueAdvancements {
    /** Nome do critério nos arquivos JSON. */
    private static final String CRITERION = "code";
    private static final int[] LEVELS = {10, 25, 50, 100};

    private RogueAdvancements() {
    }

    /** Dá a conquista roguecards:&lt;name&gt;. Sem efeito se ela não existir ou já estiver feita. */
    public static void award(ServerPlayer player, String name) {
        if (player.server == null) {
            return;
        }
        AdvancementHolder holder = player.server.getAdvancements().get(RoguecraftCards.id(name));
        if (holder != null && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
            player.getAdvancements().award(holder, CRITERION);
        }
    }

    /** Nível azul: a raiz (1º nível) e os marcos 10, 25, 50 e 100. */
    public static void checkLevel(ServerPlayer player, int level) {
        if (level >= 1) {
            award(player, "root");
        }
        for (int mark : LEVELS) {
            if (level >= mark) {
                award(player, "blue_level_" + mark);
            }
        }
    }

    /** Carta escolhida: primeira carta, raridade (rara ou melhor, épica ou melhor, lendária) e colecionador. */
    public static void onCardChosen(ServerPlayer player, RogueData data, Rarity rarity) {
        award(player, "first_card");
        checkRarity(player, rarity);
        checkCollector(player, data);
    }

    private static void checkRarity(ServerPlayer player, Rarity rarity) {
        if (rarity.ordinal() >= Rarity.RARE.ordinal()) {
            award(player, "rare_card");
        }
        if (rarity.ordinal() >= Rarity.EPIC.ordinal()) {
            award(player, "epic_card");
        }
        if (rarity == Rarity.LEGENDARY) {
            award(player, "legendary_card");
        }
    }

    /**
     * Colecionador: escolheu cada carta do mod pelo menos uma vez (as 24 cartas próprias;
     * cartas de outros mods não contam e cartas desligadas na config ficam de fora).
     */
    public static boolean hasAllCards(RogueData data) {
        for (CardType type : CardType.builtins().values()) {
            if (!RogueXp.disabledByConfig(type) && data.picks.getOrDefault(type.id(), 0) <= 0) {
                return false;
            }
        }
        return true;
    }

    private static void checkCollector(ServerPlayer player, RogueData data) {
        if (hasAllCards(data)) {
            award(player, "collector");
        }
    }

    /** Ao entrar no mundo: dá o que o progresso salvo já merece. */
    public static void checkAll(ServerPlayer player) {
        RogueData data = RogueXp.get(player);
        checkLevel(player, data.level);
        if (!data.history.isEmpty()) {
            award(player, "first_card");
            for (CardOffer offer : data.history) {
                checkRarity(player, offer.rarityValue());
            }
            checkCollector(player, data);
        }
        if (!data.banned.isEmpty()) {
            award(player, "banish");
        }
    }
}
