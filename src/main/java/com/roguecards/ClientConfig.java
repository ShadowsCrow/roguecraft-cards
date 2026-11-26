package com.roguecards;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Opções de cada jogador (só a tela dele). Ficam em config/roguecards-client.toml. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_BLUE_BAR = BUILDER
            .comment("Mostrar a barra de XP azul e o nível azul acima da barra de XP normal.")
            .define("showBlueBar", true);

    public static final ModConfigSpec.BooleanValue SHOW_CARD_HINT = BUILDER
            .comment("Mostrar o aviso piscando \"Carta! [K]\" ao lado da barra quando há carta para escolher.")
            .define("showCardHint", true);

    public static final ModConfigSpec.BooleanValue REVEAL_ANIMATION = BUILDER
            .comment("Animar a revelação das cartas: chegam viradas, brilham na cor da raridade e viram uma a uma.",
                    "Desligado: as cartas aparecem já viradas, sem efeitos.")
            .define("revealAnimation", true);

    public static final ModConfigSpec.BooleanValue REVEAL_SOUNDS = BUILDER
            .comment("Tocar os sons da revelação (carta virando e o som de cada raridade).")
            .define("revealSounds", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}
