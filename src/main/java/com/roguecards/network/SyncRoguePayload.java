package com.roguecards.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.roguecards.RoguecraftCards;
import com.roguecards.api.card.CardOffer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor → jogador: estado do XP azul, da oferta de cartas, dos bônus, das escolhas, banimentos e trocas e a trilha de escolhas. */
public record SyncRoguePayload(int level, int xp, int xpNeeded, int pending, List<CardOffer> offer,
                               Map<String, Double> bonuses, Map<String, Integer> picks, List<String> banned,
                               int rerollsLeft, int banTokens, List<CardOffer> history, int pityLeft) implements CustomPacketPayload {

    public static final Type<SyncRoguePayload> TYPE = new Type<>(RoguecraftCards.id("sync_rogue"));

    public static final StreamCodec<FriendlyByteBuf, SyncRoguePayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncRoguePayload::write, SyncRoguePayload::read);

    private static final StreamCodec<io.netty.buffer.ByteBuf, List<CardOffer>> OFFER_CODEC = CardOffer.STREAM_CODEC.apply(ByteBufCodecs.list());

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(level);
        buf.writeVarInt(xp);
        buf.writeVarInt(xpNeeded);
        buf.writeVarInt(pending);
        OFFER_CODEC.encode(buf, offer);
        buf.writeMap(bonuses, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeDouble);
        buf.writeMap(picks, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
        buf.writeCollection(banned, FriendlyByteBuf::writeUtf);
        buf.writeVarInt(rerollsLeft);
        buf.writeVarInt(banTokens);
        OFFER_CODEC.encode(buf, history);
        buf.writeVarInt(pityLeft + 1);
    }

    private static SyncRoguePayload read(FriendlyByteBuf buf) {
        int level = buf.readVarInt();
        int xp = buf.readVarInt();
        int xpNeeded = buf.readVarInt();
        int pending = buf.readVarInt();
        List<CardOffer> offer = OFFER_CODEC.decode(buf);
        Map<String, Double> bonuses = buf.readMap(HashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readDouble);
        Map<String, Integer> picks = buf.readMap(HashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarInt);
        List<String> banned = buf.readList(FriendlyByteBuf::readUtf);
        int rerollsLeft = buf.readVarInt();
        int banTokens = buf.readVarInt();
        List<CardOffer> history = OFFER_CODEC.decode(buf);
        int pityLeft = buf.readVarInt() - 1;
        return new SyncRoguePayload(level, xp, xpNeeded, pending, offer, bonuses, picks, banned, rerollsLeft, banTokens, history, pityLeft);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
