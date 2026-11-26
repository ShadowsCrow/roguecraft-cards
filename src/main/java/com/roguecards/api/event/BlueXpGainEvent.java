package com.roguecards.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Disparado no servidor antes de o jogador ganhar XP azul (já com a Sabedoria aplicada).
 * Dá para mudar o valor ({@link #setAmount}) ou cancelar.
 * Origens do próprio mod: roguecards:orb (esfera azul), roguecards:harvest (carta Colheita).
 */
public class BlueXpGainEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final ResourceLocation source;
    private double amount;

    public BlueXpGainEvent(ServerPlayer player, double amount, ResourceLocation source) {
        this.player = player;
        this.amount = amount;
        this.source = source;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceLocation getSource() {
        return source;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = Math.max(0, amount);
    }
}
