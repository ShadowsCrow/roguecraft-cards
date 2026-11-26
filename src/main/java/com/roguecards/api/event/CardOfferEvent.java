package com.roguecards.api.event;

import java.util.List;

import com.roguecards.api.card.CardOffer;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Disparado no servidor depois de sortear uma oferta de cartas (escolha nova ou troca).
 * A lista pode ser alterada: trocar, tirar ou acrescentar cartas.
 */
public class CardOfferEvent extends Event {
    private final ServerPlayer player;
    private final List<CardOffer> offer;

    public CardOfferEvent(ServerPlayer player, List<CardOffer> offer) {
        this.player = player;
        this.offer = offer;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** A oferta (lista alterável). */
    public List<CardOffer> getOffer() {
        return offer;
    }
}
