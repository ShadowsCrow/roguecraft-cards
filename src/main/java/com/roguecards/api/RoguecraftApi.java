package com.roguecards.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleFunction;

import com.roguecards.RoguecraftCards;
import com.roguecards.api.card.CardType;
import com.roguecards.api.event.BlueXpGainEvent;
import com.roguecards.xp.RogueXp;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * Porta de entrada da API do Roguecraft Cards para outros mods.
 *
 * O que um mod de compatibilidade pode fazer:
 * - Registrar cartas novas: DeferredRegister.create(RoguecraftApi.CARD_REGISTRY_KEY, "seumod") e CardType.builder().
 * - Criar tipos de carta novos: CardType.Category.register(id, cor).
 * - Ler o nível azul e o total de uma carta de um jogador: {@link #level}, {@link #bonus}.
 * - Dar XP azul: {@link #giveBlueXp} (passa pela Sabedoria e pelo evento {@link BlueXpGainEvent}).
 * - Somar sorte extra ao limite do sorteio de raridade (como o berloque faz): {@link #addRarityLuckSource}.
 * - Ouvir os eventos em com.roguecards.api.event (no NeoForge.EVENT_BUS).
 */
public final class RoguecraftApi {
    public static final String MODID = "roguecards";

    /** Registro das cartas. */
    public static final ResourceKey<Registry<CardType>> CARD_REGISTRY_KEY =
            ResourceKey.createRegistryKey(RoguecraftCards.id("cards"));

    /** As cartas registradas (do mod e de outros mods). */
    public static final Registry<CardType> CARDS = new RegistryBuilder<>(CARD_REGISTRY_KEY).sync(true).create();

    private static final List<ToDoubleFunction<Player>> RARITY_LUCK_SOURCES = new CopyOnWriteArrayList<>();

    private RoguecraftApi() {
    }

    /** Nível azul do jogador. */
    public static int level(Player player) {
        return RogueXp.get(player).level;
    }

    /** Total de uma carta no jogador (0 se nunca pegou). Exemplo: bonus(player, CardType.LUCK) → 12. */
    public static double bonus(Player player, CardType card) {
        return RogueXp.bonus(player, card);
    }

    /** Quantas vezes o jogador escolheu esta carta. */
    public static int picks(Player player, CardType card) {
        return RogueXp.get(player).picks.getOrDefault(card.id(), 0);
    }

    /**
     * Dá XP azul ao jogador. O valor passa pelo multiplicador da Sabedoria e pelo evento BlueXpGainEvent
     * (com a origem informada). Exemplo: giveBlueXp(player, 5, id("cobblemon_capture")).
     */
    public static void giveBlueXp(ServerPlayer player, double amount, net.minecraft.resources.ResourceLocation source) {
        RogueXp.gainScaled(player, amount, source);
    }

    /**
     * Soma uma fonte de sorte ao limite do sorteio de raridade (padrão 50).
     * Exemplo: o berloque da sorte de netherita soma 5, então o sorteio passa a usar até sorte 55.
     */
    public static void addRarityLuckSource(ToDoubleFunction<Player> source) {
        RARITY_LUCK_SOURCES.add(source);
    }

    /** Soma de todas as fontes de sorte extra para o limite do sorteio (uso interno e informativo). */
    public static double extraRarityLuck(Player player) {
        double total = 0;
        for (ToDoubleFunction<Player> source : RARITY_LUCK_SOURCES) {
            total += Math.max(0, source.applyAsDouble(player));
        }
        return total;
    }
}
