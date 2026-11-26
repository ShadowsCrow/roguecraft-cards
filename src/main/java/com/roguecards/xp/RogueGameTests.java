package com.roguecards.xp;

import java.util.HashSet;
import java.util.Set;

import com.roguecards.RoguecraftCards;
import com.roguecards.api.card.CardOffer;
import com.roguecards.api.card.CardType;
import com.roguecards.api.card.Rarity;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Testes automáticos que rodam num servidor sem janela: gradlew runGameTestServer.
 * Usam a estrutura roguecards:floor (chão de pedra 5x5).
 */
@GameTestHolder(RoguecraftCards.MODID)
@PrefixGameTestTemplate(false)
public class RogueGameTests {

    private static AABB area(GameTestHelper h) {
        return new AABB(h.absoluteVec(new Vec3(-2, -1, -2)), h.absoluteVec(new Vec3(7, 6, 7)));
    }

    /** Matar um zumbi solta XP normal e também esferas azuis. */
    @GameTest(template = "floor")
    public static void zombieDropsBlueOrbs(GameTestHelper h) {
        ServerPlayer player = TestPlayers.create(h);
        player.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
        Zombie zombie = h.spawn(EntityType.ZOMBIE, 1, 1, 1);
        zombie.hurt(h.getLevel().damageSources().playerAttack(player), 1000F);
        h.succeedWhen(() -> {
            h.assertTrue(!h.getLevel().getEntitiesOfClass(ExperienceOrb.class, area(h)).isEmpty(), "Zumbi não soltou XP normal");
            h.assertTrue(!h.getLevel().getEntitiesOfClass(BlueXpOrb.class, area(h)).isEmpty(), "Zumbi não soltou esfera azul");
        });
    }

    /** XP normal criado direto (como fornalha e minério fazem) também solta azul. 100 de XP normal → cerca de 60 azul. */
    @GameTest(template = "floor")
    public static void vanillaAwardSpawnsBlueAmount(GameTestHelper h) {
        ExperienceOrb.award(h.getLevel(), h.absoluteVec(new Vec3(2.5, 1, 2.5)), 100);
        int total = h.getLevel().getEntitiesOfClass(BlueXpOrb.class, area(h)).stream().mapToInt(BlueXpOrb::getValue).sum();
        h.assertTrue(total >= 55 && total <= 65, "XP azul esperado ~60, veio " + total);
        h.succeed();
    }

    /** Encostar numa esfera azul dá XP azul. */
    @GameTest(template = "floor")
    public static void pickupGivesBlueXp(GameTestHelper h) {
        ServerPlayer player = TestPlayers.create(h);
        RogueData data = RogueXp.get(player);
        data.reset();
        BlueXpOrb orb = new BlueXpOrb(h.getLevel(), player.getX(), player.getY(), player.getZ(), 7);
        h.getLevel().addFreshEntity(orb);
        orb.playerTouch(player);
        h.assertTrue(data.xp == 7, "Esperava 7 de XP azul, veio " + data.xp);
        h.assertTrue(orb.isRemoved(), "A esfera devia sumir depois de coletada");
        h.succeed();
    }

    /** Subir de nível oferece 3 cartas diferentes; escolher uma aplica o bônus no atributo. */
    @GameTest(template = "floor")
    public static void levelUpAndChooseCard(GameTestHelper h) {
        ServerPlayer player = TestPlayers.create(h);
        RogueData data = RogueXp.get(player);
        data.reset();
        RogueXp.applyModifiers(player);

        RogueXp.addXp(player, RogueXp.xpNeeded(0) + 3);
        h.assertTrue(data.level == 1, "Nível devia ser 1, é " + data.level);
        h.assertTrue(data.xp == 3, "Sobra de XP devia ser 3, é " + data.xp);
        h.assertTrue(data.pending == 1, "Devia ter 1 carta pendente");
        h.assertTrue(data.offer.size() == 3, "Devia oferecer 3 cartas, ofereceu " + data.offer.size());
        Set<String> types = new HashSet<>();
        data.offer.forEach(o -> types.add(o.card()));
        h.assertTrue(types.size() == 3, "As 3 cartas deviam ser de tipos diferentes");

        CardOffer chosen = data.offer.get(0);
        CardType type = chosen.type();
        double expected = type.value(chosen.rarityValue());
        RogueXp.choose(player, 0);
        h.assertTrue(data.pending == 0 && data.offer.isEmpty(), "Depois de escolher, não devia sobrar carta");
        AttributeInstance attr = player.getAttribute(type.attribute());
        h.assertTrue(attr != null && attr.getModifier(type.modifierId()) != null, "Bônus da carta não foi aplicado em " + type.id());
        h.assertTrue(Math.abs(attr.getModifier(type.modifierId()).amount() - expected) < 1e-9, "Valor do bônus errado");

        // Escolha inválida (sem carta pendente) não muda nada.
        RogueXp.choose(player, 1);
        h.assertTrue(data.bonuses.size() == 1, "Escolha sem carta pendente não devia valer");
        h.succeed();
    }

    /** Dois níveis de uma vez: 2 cartas pendentes, e uma oferta nova aparece depois da primeira escolha. */
    @GameTest(template = "floor")
    public static void twoLevelsGiveTwoChoices(GameTestHelper h) {
        ServerPlayer player = TestPlayers.create(h);
        RogueData data = RogueXp.get(player);
        data.reset();
        RogueXp.addXp(player, RogueXp.xpNeeded(0) + RogueXp.xpNeeded(1));
        h.assertTrue(data.level == 2 && data.pending == 2, "Devia estar no nível 2 com 2 cartas");
        RogueXp.choose(player, 1);
        h.assertTrue(data.pending == 1 && data.offer.size() == 3, "Devia vir uma oferta nova de 3 cartas");
        h.succeed();
    }

    /** Sem sorte, só sai comum. Cada raridade só aparece a partir da sua sorte mínima. */
    @GameTest(template = "floor")
    public static void rarityNeedsMinimumLuck(GameTestHelper h) {
        RandomSource random = RandomSource.create(42);
        int[] noLuck = count(random, 0);
        int[] luck1 = count(random, 1);
        int[] luck3 = count(random, 3);
        int[] luck10 = count(random, 10);
        h.assertTrue(noLuck[0] == ROLLS, "Sorte 0 devia dar só comum: " + java.util.Arrays.toString(noLuck));
        h.assertTrue(luck1[1] > 0 && luck1[2] == 0, "Sorte 1 devia dar incomum e nenhuma rara: " + java.util.Arrays.toString(luck1));
        h.assertTrue(luck3[2] > 0 && luck3[3] == 0, "Sorte 3 devia dar rara e nenhuma épica: " + java.util.Arrays.toString(luck3));
        h.assertTrue(luck10[4] > 0, "Sorte 10 devia dar lendária: " + java.util.Arrays.toString(luck10));
        RoguecraftCards.LOGGER.info("Raridades em {} sorteios (comum, incomum, rara, épica, lendária): sorte 0 {}, sorte 1 {}, sorte 3 {}, sorte 10 {}",
                ROLLS, java.util.Arrays.toString(noLuck), java.util.Arrays.toString(luck1),
                java.util.Arrays.toString(luck3), java.util.Arrays.toString(luck10));
        h.succeed();
    }

    private static final int ROLLS = 20000;

    private static int[] count(RandomSource random, double luck) {
        int[] counts = new int[Rarity.values().length];
        for (int i = 0; i < ROLLS; i++) {
            counts[Rarity.roll(random, luck).ordinal()]++;
        }
        return counts;
    }
}
