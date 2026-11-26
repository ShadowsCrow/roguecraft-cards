package com.roguecards.xp;

import com.roguecards.RoguecraftCards;
import com.roguecards.api.card.CardOffer;
import com.roguecards.api.card.CardType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Testes das regras das cartas (nível, limite, teto, banir, trocar, 4 cartas)
 * e dos efeitos das habilidades. Rodam com: gradlew runGameTestServer.
 */
@GameTestHolder(RoguecraftCards.MODID)
@PrefixGameTestTemplate(false)
public class CardGameTests {
    private static final int ROLLS = 300;
    /** Ticks de imunidade de um jogador recém-nascido (60) mais uma folga. */
    private static final int SPAWN_PROTECTION = 65;

    /** Jogador de teste em modo sobrevivência (ver TestPlayers). */
    private static ServerPlayer survivalPlayer(GameTestHelper h) {
        return TestPlayers.create(h);
    }

    private static void give(ServerPlayer player, CardType type, double value) {
        RogueData data = RogueXp.get(player);
        data.bonuses.put(type.id(), value);
        data.picks.put(type.id(), 1);
        RogueXp.applyModifiers(player);
    }

    // ---------- Regras da oferta ----------

    /** Nos níveis 1 a 4 só aparecem cartas de atributo. */
    @GameTest(template = "floor")
    public static void earlyLevelsOnlyAttributes(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        RogueData data = RogueXp.get(player);
        data.level = 4;
        for (int i = 0; i < ROLLS; i++) {
            RogueXp.generateOffer(player, data);
            h.assertTrue(data.offer.size() == 3, "Oferta devia ter 3 cartas");
            for (CardOffer o : data.offer) {
                h.assertTrue(o.type().category() == CardType.Category.ATTRIBUTE, "No nível 4 apareceu " + o.card());
            }
        }
        h.succeed();
    }

    /** Carta que bateu o limite ou o teto para de aparecer; carta banida também. */
    @GameTest(template = "floor")
    public static void limitCapAndBanRemoveCards(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        RogueData data = RogueXp.get(player);
        data.level = 30;
        data.picks.put(CardType.VITALITY.id(), CardType.VITALITY.limit());
        data.bonuses.put(CardType.THORNS.id(), 0.6);
        data.picks.put(CardType.THORNS.id(), 2);
        data.bonuses.put(CardType.BANISH.id(), 1.0);
        data.picks.put(CardType.BANISH.id(), 1);
        data.pending = 1;
        data.offer.clear();
        data.offer.add(CardOffer.of(CardType.LUCK, com.roguecards.api.card.Rarity.COMMON));
        data.offer.add(CardOffer.of(CardType.STRENGTH, com.roguecards.api.card.Rarity.COMMON));
        data.offer.add(CardOffer.of(CardType.AGILITY, com.roguecards.api.card.Rarity.COMMON));
        // Banir uma carta que não está na oferta não vale.
        RogueXp.ban(player, CardType.MINING.id());
        h.assertTrue(data.banned.isEmpty() && RogueXp.banTokens(data) == 1, "Só dá para banir carta que está na oferta");
        RogueXp.ban(player, CardType.STRENGTH.id());
        h.assertTrue(data.banned.contains(CardType.STRENGTH.id()), "Força devia estar banida");
        h.assertTrue(RogueXp.banTokens(data) == 0, "O banimento devia ter sido gasto");
        h.assertTrue(data.offer.size() == 3 && "roguecards:luck".equals(data.offer.get(0).card()) && "roguecards:agility".equals(data.offer.get(2).card())
                && !"roguecards:strength".equals(data.offer.get(1).card()), "Só a vaga da carta banida devia trocar");
        for (int i = 0; i < ROLLS; i++) {
            RogueXp.generateOffer(player, data);
            for (CardOffer o : data.offer) {
                h.assertTrue(!o.card().equals("roguecards:vitality"), "Vitalidade no limite apareceu");
                h.assertTrue(!o.card().equals("roguecards:thorns"), "Espinhos no teto apareceu");
                h.assertTrue(!o.card().equals("roguecards:strength"), "Força banida apareceu");
            }
        }
        h.succeed();
    }

    /** Teto: somar além do teto para no teto. Exemplo: Crítico 0,35 + 0,18 = 0,40 (teto). */
    @GameTest(template = "floor")
    public static void capClampsSum(GameTestHelper h) {
        h.assertTrue(Math.abs(CardType.CRITICAL.combine(0.35, true, 0.18) - 0.40) < 1e-9, "Teto do Crítico devia segurar em 40%");
        // Melhora: Regeneração 10 s depois 6 s → fica 6 x 0,8 = 4,8 s.
        h.assertTrue(Math.abs(CardType.REGENERATION.combine(10, true, 6) - 4.8) < 1e-9, "Regeneração devia ir para 4,8 s");
        h.assertTrue(Math.abs(CardType.REGENERATION.combine(2.2, true, 3) - 2.0) < 1e-9, "Regeneração não pode passar de 2 s");
        h.succeed();
    }

    /** Trocar Cartas troca a oferta e gasta uma troca; Mão Cheia deixa a oferta com 4 cartas. */
    @GameTest(template = "floor")
    public static void rerollAndFullHand(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        RogueData data = RogueXp.get(player);
        data.level = 30;
        data.pending = 1;
        data.rerollsLeft = 1;
        RogueXp.reroll(player);
        h.assertTrue(data.rerollsLeft == 0 && data.offer.size() == 3, "Troca devia gastar 1 e manter 3 cartas");
        give(player, CardType.FULL_HAND, 1);
        RogueXp.generateOffer(player, data);
        h.assertTrue(data.offer.size() == 4, "Com Mão Cheia a oferta devia ter 4 cartas, tem " + data.offer.size());
        h.succeed();
    }

    /** Trocar Cartas 3 com 5 níveis acumulados: cada escolha começa com 3 trocas, mesmo depois de gastar trocas na anterior. */
    @GameTest(template = "floor")
    public static void rerollsRenewEveryPick(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        RogueData data = RogueXp.get(player);
        give(player, CardType.REROLL, 3);
        data.level = 30;
        data.pending = 5;
        RogueXp.newChoice(player, data);
        h.assertTrue(data.rerollsLeft == 3, "A primeira escolha devia ter 3 trocas");
        RogueXp.reroll(player);
        RogueXp.reroll(player);
        h.assertTrue(data.rerollsLeft == 1, "Depois de 2 trocas devia sobrar 1");
        RogueXp.choose(player, 0);
        h.assertTrue(data.pending == 4 && data.rerollsLeft == 3, "A escolha seguinte devia começar com 3 trocas de novo; tem " + data.rerollsLeft);
        h.succeed();
    }

    /** Sorte não tem limite de vezes: com 30 escolhas pequenas ainda pode aparecer, até o teto 50. */
    @GameTest(template = "floor")
    public static void luckHasNoPickLimit(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        RogueData data = RogueXp.get(player);
        data.level = 1;
        data.picks.put(CardType.LUCK.id(), 30);
        data.bonuses.put(CardType.LUCK.id(), 15.0);
        h.assertTrue(RogueXp.canOffer(data, CardType.LUCK), "Sorte com 30 escolhas e total 15 devia continuar aparecendo");
        data.bonuses.put(CardType.LUCK.id(), 50.0);
        h.assertTrue(!RogueXp.canOffer(data, CardType.LUCK), "Sorte no teto 50 devia parar de aparecer");
        // O sorteio trava em sorte 50: sorte 80 tem as mesmas chances.
        double[] at50 = com.roguecards.api.card.Rarity.chances(50);
        double[] at80 = com.roguecards.api.card.Rarity.chances(80);
        for (int i = 0; i < at50.length; i++) {
            h.assertTrue(Math.abs(at50[i] - at80[i]) < 1e-12, "Sorte acima de 50 não devia mudar as chances");
        }
        h.succeed();
    }

    /** Proteção contra azar: depois de 5 ofertas só com comuns (com sorte 1), a próxima tem pelo menos uma incomum. */
    @GameTest(template = "floor")
    public static void pityGuaranteesUncommon(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        RogueData data = RogueXp.get(player);
        give(player, CardType.LUCK, 1);
        data.level = 1;
        for (int i = 0; i < 200; i++) {
            data.commonStreak = 5;
            RogueXp.generateOffer(player, data);
            h.assertTrue(data.offer.stream().anyMatch(o -> o.rarityValue() != com.roguecards.api.card.Rarity.COMMON),
                    "Com a proteção ativa, a oferta devia ter pelo menos uma incomum");
            h.assertTrue(data.commonStreak == 0, "Depois de sair uma incomum, o contador devia zerar");
        }
        h.succeed();
    }

    /** Trevo: 1,5% sem sorte, 4% com sorte 5, e nunca passa de 25%. Berloque passa do limite do sorteio. */
    @GameTest(template = "floor")
    public static void cloverChanceAndCharmCap(GameTestHelper h) {
        h.assertTrue(Math.abs(com.roguecards.item.CloverDrops.chance(0) - 0.015) < 1e-9, "Trevo sem sorte devia ser 1,5%");
        h.assertTrue(Math.abs(com.roguecards.item.CloverDrops.chance(5) - 0.04) < 1e-9, "Trevo com sorte 5 devia ser 4%");
        h.assertTrue(Math.abs(com.roguecards.item.CloverDrops.chance(100) - 0.25) < 1e-9, "Trevo nunca passa de 25%");
        double[] noCharm = com.roguecards.api.card.Rarity.chances(55, 0);
        double[] withCharm = com.roguecards.api.card.Rarity.chances(55, 5);
        h.assertTrue(withCharm[4] > noCharm[4], "Com berloque de +5, sorte 55 devia dar mais lendárias que o limite de 50");
        h.succeed();
    }

    /** Trocar Cartas: com 1 já pega, a 2ª só aparece como rara e 20 escolhas depois da 1ª. */
    @GameTest(template = "floor")
    public static void rerollTiersByPick(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        RogueData data = RogueXp.get(player);
        give(player, CardType.LUCK, 50);
        data.bonuses.put(CardType.REROLL.id(), 1.0);
        data.picks.put(CardType.REROLL.id(), 1);
        data.level = 60;
        fillerChoices(data, 3);
        data.history.add(CardOffer.of(CardType.REROLL, com.roguecards.api.card.Rarity.UNCOMMON));
        fillerChoices(data, 19);
        for (int i = 0; i < ROLLS; i++) {
            RogueXp.generateOffer(player, data);
            h.assertTrue(data.offer.stream().noneMatch(o -> o.card().equals("roguecards:reroll")),
                    "19 escolhas depois da 1ª troca, a 2ª não devia aparecer");
        }
        fillerChoices(data, 1);
        boolean seen = false;
        for (int i = 0; i < ROLLS * 4; i++) {
            RogueXp.generateOffer(player, data);
            for (CardOffer o : data.offer) {
                if (o.card().equals("roguecards:reroll")) {
                    seen = true;
                    h.assertTrue(o.rarityValue() == com.roguecards.api.card.Rarity.RARE, "A 2ª troca devia vir rara, veio " + o.rarityValue());
                }
            }
        }
        h.assertTrue(seen, "20 escolhas depois da 1ª, com sorte alta, a 2ª troca devia aparecer pelo menos uma vez");
        h.succeed();
    }

    /** Põe n escolhas comuns de Vitalidade no histórico, só para contar escolhas. */
    private static void fillerChoices(RogueData data, int n) {
        for (int i = 0; i < n; i++) {
            data.history.add(CardOffer.of(CardType.VITALITY, com.roguecards.api.card.Rarity.COMMON));
        }
    }

    /** Sem nível máximo por padrão: do 59, bastante XP passa do 60. Custo do nível N = 20 + 5 x N. */
    @GameTest(template = "floor")
    public static void noLevelCapByDefault(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        RogueData data = RogueXp.get(player);
        h.assertTrue(RogueXp.xpNeeded(0) == 20 && RogueXp.xpNeeded(32) == 180, "Custo devia ser 20 + 5 por nível");
        data.level = 59;
        RogueXp.addXp(player, 100000);
        h.assertTrue(data.level > 60, "Sem máximo, devia passar do nível 60; está em " + data.level);
        h.succeed();
    }

    /** Trocar Cartas: no nível 5, depois de 5 escolhas, mesmo sem sorte, a 1ª troca aparece em até 3 ofertas. */
    @GameTest(template = "floor")
    public static void firstRerollGuaranteed(GameTestHelper h) {
        for (int run = 0; run < 50; run++) {
            ServerPlayer player = survivalPlayer(h);
            RogueData data = RogueXp.get(player);
            data.level = 5;
            fillerChoices(data, 5);
            boolean seen = false;
            for (int i = 0; i < 3 && !seen; i++) {
                RogueXp.generateOffer(player, data);
                seen = data.offer.stream().anyMatch(o -> o.card().equals("roguecards:reroll"));
            }
            h.assertTrue(seen, "A primeira Trocar Cartas devia aparecer em até 3 ofertas");
        }
        h.succeed();
    }

    /** Configs: carta desligada não aparece; offerSize muda o tamanho da oferta. */
    @GameTest(template = "floor")
    public static void configDisabledCardsAndOfferSize(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        RogueData data = RogueXp.get(player);
        data.level = 1;
        var disabledBefore = com.roguecards.Config.DISABLED_CARDS.get();
        int sizeBefore = com.roguecards.Config.OFFER_SIZE.get();
        try {
            com.roguecards.Config.DISABLED_CARDS.set(java.util.List.of(CardType.LUCK.id()));
            com.roguecards.Config.OFFER_SIZE.set(5);
            for (int i = 0; i < ROLLS; i++) {
                RogueXp.generateOffer(player, data);
                h.assertTrue(data.offer.size() == 5, "offerSize 5 devia dar 5 cartas, deu " + data.offer.size());
                h.assertTrue(data.offer.stream().noneMatch(o -> o.card().equals(CardType.LUCK.id())), "Sorte desligada apareceu");
            }
        } finally {
            com.roguecards.Config.DISABLED_CARDS.set(disabledBefore);
            com.roguecards.Config.OFFER_SIZE.set(sizeBefore);
        }
        h.succeed();
    }

    /** Pacto de Sangue soma dano e tira vida máxima. */
    @GameTest(template = "floor")
    public static void bloodPactTradesHealthForDamage(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        RogueData data = RogueXp.get(player);
        data.level = 30;
        data.pending = 1;
        data.offer.clear();
        data.offer.add(CardOffer.of(CardType.BLOOD_PACT, com.roguecards.api.card.Rarity.EPIC));
        float maxBefore = player.getMaxHealth();
        double dmgBefore = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        RogueXp.choose(player, 0);
        h.assertTrue(Math.abs(player.getMaxHealth() - (maxBefore - 4)) < 1e-6, "Pacto épico devia tirar 2 corações");
        h.assertTrue(Math.abs(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) - (dmgBefore + 3)) < 1e-6,
                "Pacto épico devia dar +3 de dano");
        h.assertTrue(data.history.size() == 1 && "roguecards:blood_pact".equals(data.history.get(0).card()),
                "A trilha devia registrar o Pacto de Sangue");
        h.succeed();
    }

    // ---------- Habilidades ----------

    /** Crítico 100%: um golpe de 2 vira 4 (o zumbi fica com cerca de 16 de vida). */
    @GameTest(template = "floor")
    public static void criticalDoublesDamage(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        give(player, CardType.CRITICAL, 1.0);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, 1, 1, 1);
        zombie.hurt(h.getLevel().damageSources().playerAttack(player), 2.0F);
        h.assertTrue(zombie.getHealth() < 17.0F, "Crítico devia dobrar o dano; vida do zumbi: " + zombie.getHealth());
        h.succeed();
    }

    /** Vampirismo 50%: bater cura o jogador. */
    @GameTest(template = "floor")
    public static void vampirismHeals(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        give(player, CardType.VAMPIRISM, 0.5);
        player.setHealth(10.0F);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, 1, 1, 1);
        zombie.hurt(h.getLevel().damageSources().playerAttack(player), 6.0F);
        h.assertTrue(player.getHealth() > 11.0F, "Vampirismo devia curar; vida: " + player.getHealth());
        h.succeed();
    }

    /** Espinhos 50%: apanhar de um zumbi machuca o zumbi. */
    @GameTest(template = "floor")
    public static void thornsReflects(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        give(player, CardType.THORNS, 0.5);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, 1, 1, 1);
        // Jogador recém-nascido fica imune a dano por 3 s; o teste espera esse tempo.
        h.runAfterDelay(SPAWN_PROTECTION, () -> {
            float before = zombie.getHealth();
            player.hurt(h.getLevel().damageSources().mobAttack(zombie), 6.0F);
            h.assertTrue(player.getHealth() < player.getMaxHealth(), "O jogador devia ter levado dano");
            h.assertTrue(zombie.getHealth() < before, "Espinhos devia machucar o zumbi");
            h.succeed();
        });
    }

    /** Queda Leve 50%: uma queda de 8 tira só 4. Com 100%, não tira nada. */
    @GameTest(template = "floor")
    public static void featherFallReducesFall(GameTestHelper h) {
        ServerPlayer half = survivalPlayer(h);
        ServerPlayer full = survivalPlayer(h);
        give(half, CardType.FEATHER_FALL, 0.5);
        give(full, CardType.FEATHER_FALL, 1.0);
        h.runAfterDelay(SPAWN_PROTECTION, () -> {
            half.hurt(h.getLevel().damageSources().fall(), 8.0F);
            full.hurt(h.getLevel().damageSources().fall(), 8.0F);
            h.assertTrue(Math.abs(half.getHealth() - (half.getMaxHealth() - 4.0F)) < 0.01F, "Queda Leve 50% devia tirar 4; vida: " + half.getHealth());
            h.assertTrue(full.getHealth() == full.getMaxHealth(), "Queda Leve 100% devia anular a queda");
            h.succeed();
        });
    }

    /** Segunda Chance: dano mortal deixa o jogador vivo com metade da vida, e depois espera para funcionar de novo. */
    @GameTest(template = "floor")
    public static void secondChanceSavesOnce(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        give(player, CardType.SECOND_CHANCE, 15);
        h.runAfterDelay(SPAWN_PROTECTION, () -> {
            player.hurt(h.getLevel().damageSources().generic(), 1000.0F);
            h.assertTrue(player.isAlive() && player.getHealth() >= player.getMaxHealth() / 2 - 0.01F,
                    "Segunda Chance devia salvar com metade da vida; vida: " + player.getHealth());
            h.assertTrue(RogueXp.get(player).secondChanceReadyAt > h.getLevel().getGameTime(), "Segunda Chance devia entrar em espera");
            h.succeed();
        });
    }

    /** Colheita +3: matar um zumbi dá 3 de XP azul direto. */
    @GameTest(template = "floor")
    public static void harvestGivesXp(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        give(player, CardType.HARVEST, 3);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, 1, 1, 1);
        zombie.hurt(h.getLevel().damageSources().playerAttack(player), 1000.0F);
        h.assertTrue(RogueXp.get(player).xp == 3, "Colheita devia dar 3 de XP azul, deu " + RogueXp.get(player).xp);
        h.succeed();
    }

    /** Saciedade 50%: o gasto de fome cai pela metade. */
    @GameTest(template = "floor")
    public static void satietyHalvesExhaustion(GameTestHelper h) {
        ServerPlayer player = survivalPlayer(h);
        give(player, CardType.SATIETY, 0.5);
        float before = player.getFoodData().getExhaustionLevel();
        player.causeFoodExhaustion(2.0F);
        float spent = player.getFoodData().getExhaustionLevel() - before;
        h.assertTrue(Math.abs(spent - 1.0F) < 1e-4, "Saciedade 50% devia gastar 1 de 2; gastou " + spent);
        h.succeed();
    }
}
