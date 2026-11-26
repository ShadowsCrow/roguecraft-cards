package com.roguecards.network;

import com.roguecards.RoguecraftCards;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Jogador → servidor: trocar a oferta (Trocar Cartas) ou banir uma carta (Banir).
 * A escolha de carta continua no ChooseCardPayload.
 */
public record CardActionPayload(int action, String card) implements CustomPacketPayload {
    public static final int REROLL = 0;
    public static final int BAN = 1;

    public static final Type<CardActionPayload> TYPE = new Type<>(RoguecraftCards.id("card_action"));

    public static final StreamCodec<ByteBuf, CardActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CardActionPayload::action,
            ByteBufCodecs.STRING_UTF8, CardActionPayload::card,
            CardActionPayload::new);

    public static CardActionPayload reroll() {
        return new CardActionPayload(REROLL, "");
    }

    public static CardActionPayload ban(String card) {
        return new CardActionPayload(BAN, card);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
