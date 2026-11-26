package com.roguecards.xp;

import com.roguecards.ModStats;
import com.roguecards.RoguecraftCards;
import com.roguecards.api.card.CardOffer;
import com.roguecards.api.card.CardType;
import com.roguecards.api.card.Rarity;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Testes das conquistas (aba Roguecraft Cards) e das estatísticas do mod. */
@GameTestHolder(RoguecraftCards.MODID)
@PrefixGameTestTemplate(false)
public class ProgressGameTests {

    private static boolean done(GameTestHelper h, ServerPlayer player, String name) {
        AdvancementHolder holder = player.server.getAdvancements().get(RoguecraftCards.id(name));
        h.assertTrue(holder != null, "A conquista roguecards:" + name + " não foi carregada");
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static int stat(ServerPlayer player, ResourceLocation id) {
        return player.getStats().getValue(Stats.CUSTOM.get(id));
    }

    /** 1º nível azul e uma carta lendária: raiz, primeira carta, rara, épica e lendária; estatísticas somam 1. */
    @GameTest(template = "floor")
    public static void legendaryPickGivesAdvancementsAndStats(GameTestHelper h) {
        ServerPlayer player = TestPlayers.createTracked(h);
        RogueData data = RogueXp.get(player);
        RogueXp.addXp(player, RogueXp.xpNeeded(0));
        h.assertTrue(data.level == 1 && data.pending == 1, "Devia subir para o nível azul 1 com 1 carta para escolher");
        h.assertTrue(done(h, player, "root"), "O 1º nível azul devia dar a conquista raiz");
        h.assertTrue(stat(player, ModStats.BLUE_LEVELS.get()) == 1, "Estatística blue_levels devia ser 1");
        h.assertFalse(done(h, player, "first_card"), "Primeira carta só depois de escolher");

        data.offer.clear();
        data.offer.add(CardOffer.of(CardType.VITALITY, Rarity.LEGENDARY));
        RogueXp.choose(player, 0);
        for (String name : new String[] {"first_card", "rare_card", "epic_card", "legendary_card"}) {
            h.assertTrue(done(h, player, name), "Carta lendária devia dar a conquista " + name);
        }
        h.assertFalse(done(h, player, "collector"), "Colecionador não devia vir com 1 carta");
        h.assertFalse(done(h, player, "blue_level_10"), "Nível azul 10 não devia vir no nível 1");
        h.assertTrue(stat(player, ModStats.CARDS_CHOSEN.get()) == 1, "Estatística cards_chosen devia ser 1");
        h.assertTrue(stat(player, ModStats.LEGENDARY_CARDS.get()) == 1, "Estatística legendary_cards devia ser 1");
        h.succeed();
    }

    /** Carta comum só dá a primeira carta; colecionador quando falta só uma; ao entrar, os marcos de nível chegam. */
    @GameTest(template = "floor")
    public static void collectorAndLevelMarks(GameTestHelper h) {
        ServerPlayer player = TestPlayers.createTracked(h);
        RogueData data = RogueXp.get(player);
        data.level = 30;
        for (CardType type : CardType.builtins().values()) {
            if (type != CardType.VITALITY) {
                data.picks.put(type.id(), 1);
            }
        }
        data.pending = 1;
        data.offer.add(CardOffer.of(CardType.VITALITY, Rarity.COMMON));
        RogueXp.choose(player, 0);
        h.assertTrue(done(h, player, "first_card") && done(h, player, "collector"), "Com todas as cartas devia dar Colecionador");
        h.assertFalse(done(h, player, "rare_card"), "Carta comum não dá a conquista de rara");
        h.assertTrue(stat(player, ModStats.LEGENDARY_CARDS.get()) == 0, "Comum não soma em legendary_cards");

        RogueAdvancements.checkAll(player);
        h.assertTrue(done(h, player, "root") && done(h, player, "blue_level_10") && done(h, player, "blue_level_25"),
                "Nível 30 salvo devia dar raiz, nível 10 e nível 25 ao entrar");
        h.assertFalse(done(h, player, "blue_level_50"), "Nível 50 não devia vir no nível 30");
        h.succeed();
    }
}
