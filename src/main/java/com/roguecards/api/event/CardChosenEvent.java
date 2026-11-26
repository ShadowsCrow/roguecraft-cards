package com.roguecards.api.event;

import com.roguecards.api.card.CardType;
import com.roguecards.api.card.Rarity;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Disparado no servidor depois que o jogador escolhe uma carta e o bônus já foi aplicado. */
public class CardChosenEvent extends Event {
    private final ServerPlayer player;
    private final CardType card;
    private final Rarity rarity;
    private final double value;

    public CardChosenEvent(ServerPlayer player, CardType card, Rarity rarity, double value) {
        this.player = player;
        this.card = card;
        this.rarity = rarity;
        this.value = value;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public CardType getCard() {
        return card;
    }

    public Rarity getRarity() {
        return rarity;
    }

    /** Valor da carta nessa raridade (antes de somar ao total). */
    public double getValue() {
        return value;
    }
}
