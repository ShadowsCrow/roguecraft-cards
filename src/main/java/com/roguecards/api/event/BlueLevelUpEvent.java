package com.roguecards.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Disparado no servidor depois que o jogador sobe de nível azul. Só informa; não dá para cancelar. */
public class BlueLevelUpEvent extends Event {
    private final ServerPlayer player;
    private final int oldLevel;
    private final int newLevel;

    public BlueLevelUpEvent(ServerPlayer player, int oldLevel, int newLevel) {
        this.player = player;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }
}
