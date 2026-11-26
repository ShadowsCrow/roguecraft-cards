package com.roguecards.network;

import com.roguecards.RoguecraftCards;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Jogador → servidor: escolhi a carta de posição index (0, 1 ou 2). */
public record ChooseCardPayload(int index) implements CustomPacketPayload {

    public static final Type<ChooseCardPayload> TYPE = new Type<>(RoguecraftCards.id("choose_card"));

    public static final StreamCodec<ByteBuf, ChooseCardPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChooseCardPayload::index,
            ChooseCardPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
