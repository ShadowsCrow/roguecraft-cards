package com.roguecards.api.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Uma carta oferecida ao jogador: o tipo (pelo id) e a raridade (pelo índice). */
public record CardOffer(String card, int rarity) {
    public static final Codec<CardOffer> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("card").forGetter(CardOffer::card),
            Codec.INT.fieldOf("rarity").forGetter(CardOffer::rarity)
    ).apply(i, CardOffer::new));

    public static final StreamCodec<ByteBuf, CardOffer> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CardOffer::card,
            ByteBufCodecs.VAR_INT, CardOffer::rarity,
            CardOffer::new);

    public static CardOffer of(CardType type, Rarity rarity) {
        return new CardOffer(type.id(), rarity.ordinal());
    }

    /** Pode ser nulo se a carta salva não existir mais nesta versão do mod. */
    public CardType type() {
        return CardType.byId(card);
    }

    public Rarity rarityValue() {
        return Rarity.byIndex(rarity);
    }
}
