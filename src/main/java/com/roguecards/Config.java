package com.roguecards;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Números de equilíbrio. Ficam por mundo, em saves/(mundo)/serverconfig/roguecards-server.toml,
 * e o servidor manda os valores para os jogadores.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue XP_RATIO = BUILDER
            .comment("Quanto XP azul cai para cada ponto de XP normal. 0.6 = 60%.")
            .defineInRange("xpRatio", 0.6, 0.0, 10.0);

    public static final ModConfigSpec.IntValue MAX_LEVEL = BUILDER
            .comment("Nível azul máximo. 0 = sem máximo (padrão).",
                    "Com um máximo, a barra azul fica cheia ao chegar nele e as esferas azuis param de dar XP.")
            .defineInRange("levelCap", 0, 0, 10000);

    public static final ModConfigSpec.IntValue BASE_XP_PER_LEVEL = BUILDER
            .comment("XP azul para passar do nível 0 para o nível 1.")
            .defineInRange("baseXpPerLevel", 20, 1, 100000);

    public static final ModConfigSpec.IntValue EXTRA_XP_PER_LEVEL = BUILDER
            .comment("XP azul extra que cada nível novo pede. Nível N pede base + N x este valor.")
            .defineInRange("extraXpPerLevel", 5, 0, 100000);

    public static final ModConfigSpec.DoubleValue CLOVER_BASE_CHANCE = BUILDER
            .comment("Trevo de quatro folhas: chance de cair ao quebrar grama, sem sorte. 0.015 = 1,5%.")
            .defineInRange("cloverBaseChance", 0.015, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CLOVER_CHANCE_PER_LUCK = BUILDER
            .comment("Chance extra do trevo por ponto de sorte. 0.005 = +0,5% por ponto.")
            .defineInRange("cloverChancePerLuck", 0.005, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue CLOVER_MAX_CHANCE = BUILDER
            .comment("Chance máxima do trevo, com qualquer sorte. 0.25 = 25%.")
            .defineInRange("cloverMaxChance", 0.25, 0.0, 1.0);

    static {
        BUILDER.comment(
                "Raridade das cartas. Comum sempre pode aparecer, com peso fixo 100.",
                "Cada outra raridade só aparece quando a sorte do jogador chega ao mínimo dela.",
                "Depois disso, o peso = crescimento x (sorte - mínimo + 1).",
                "Exemplo com os valores padrão e sorte 3: comum 100, incomum 30, rara 5 -> incomum 22%, rara 3,7%.")
                .push("rarity");
    }

    public static final ModConfigSpec.DoubleValue MAX_LUCK = BUILDER
            .comment("Sorte máxima usada no sorteio de raridade. Sorte acima disso (de itens ou poções) não muda mais as chances.",
                    "Com os valores padrão, sorte 50 dá: comum 9,9%, incomum 49%, rara 24%, épica 11%, lendária 6,1%.")
            .defineInRange("maxLuck", 50.0, 1.0, 1024.0);

    public static final ModConfigSpec.IntValue PITY_OFFERS = BUILDER
            .comment("Proteção contra azar: depois de quantas ofertas seguidas só com cartas comuns",
                    "(com sorte suficiente para a incomum) a próxima oferta garante pelo menos uma incomum. 0 desliga.")
            .defineInRange("pityOffers", 5, 0, 1000);

    public static final ModConfigSpec.IntValue OFFER_SIZE = BUILDER
            .comment("Quantas cartas aparecem em cada oferta. A carta Mão Cheia soma +1.")
            .defineInRange("offerSize", 3, 1, 6);

    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> DISABLED_CARDS = BUILDER
            .comment("Cartas que nunca aparecem nas ofertas. Use o nome no registro, por exemplo:",
                    "[\"roguecards:storm\", \"roguecards:blood_pact\"]. Cartas de addons também valem.")
            .defineListAllowEmpty("disabledCards", java.util.List.of(), () -> "roguecards:storm", o -> o instanceof String);

    public static final ModConfigSpec.BooleanValue RESET_ON_DEATH = BUILDER
            .comment("Modo hardcore: ao morrer, o jogador perde o nível azul, o XP azul e todas as cartas.",
                    "Desligado (padrão): o jogador mantém tudo ao morrer.")
            .define("resetOnDeath", false);

    public static final ModConfigSpec.BooleanValue XP_BOTTLES_GIVE_BLUE_XP = BUILDER
            .comment("Se o Frasco de Experiência (comprado de aldeões) também solta XP azul.")
            .define("xpBottlesGiveBlueXp", true);

    public static final ModConfigSpec.IntValue REROLL_GUARANTEE_OFFERS = BUILDER
            .comment("A primeira Trocar Cartas aparece com certeza em uma destas primeiras ofertas depois de liberar (nível 5).",
                    "3 = se não sair nas duas primeiras, a terceira oferta já vem com ela. 0 desliga.")
            .defineInRange("rerollGuaranteeOffers", 3, 0, 100);

    public static final ModConfigSpec.DoubleValue COMMON_WEIGHT = BUILDER
            .comment("Peso da carta comum.")
            .defineInRange("commonWeight", 100.0, 1.0, 100000.0);

    public static final ModConfigSpec.DoubleValue UNCOMMON_MIN_LUCK = BUILDER
            .comment("Sorte mínima para a carta incomum aparecer.")
            .defineInRange("uncommonMinLuck", 1.0, 0.0, 1024.0);
    public static final ModConfigSpec.DoubleValue UNCOMMON_GROWTH = BUILDER
            .comment("Quanto o peso da incomum cresce por ponto de sorte.")
            .defineInRange("uncommonGrowth", 10.0, 0.0, 100000.0);

    public static final ModConfigSpec.DoubleValue RARE_MIN_LUCK = BUILDER
            .comment("Sorte mínima para a carta rara aparecer.")
            .defineInRange("rareMinLuck", 3.0, 0.0, 1024.0);
    public static final ModConfigSpec.DoubleValue RARE_GROWTH = BUILDER
            .comment("Quanto o peso da rara cresce por ponto de sorte.")
            .defineInRange("rareGrowth", 5.0, 0.0, 100000.0);

    public static final ModConfigSpec.DoubleValue EPIC_MIN_LUCK = BUILDER
            .comment("Sorte mínima para a carta épica aparecer.")
            .defineInRange("epicMinLuck", 6.0, 0.0, 1024.0);
    public static final ModConfigSpec.DoubleValue EPIC_GROWTH = BUILDER
            .comment("Quanto o peso da épica cresce por ponto de sorte.")
            .defineInRange("epicGrowth", 2.5, 0.0, 100000.0);

    public static final ModConfigSpec.DoubleValue LEGENDARY_MIN_LUCK = BUILDER
            .comment("Sorte mínima para a carta lendária aparecer.")
            .defineInRange("legendaryMinLuck", 10.0, 0.0, 1024.0);
    public static final ModConfigSpec.DoubleValue LEGENDARY_GROWTH = BUILDER
            .comment("Quanto o peso da lendária cresce por ponto de sorte.")
            .defineInRange("legendaryGrowth", 1.5, 0.0, 100000.0);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}
