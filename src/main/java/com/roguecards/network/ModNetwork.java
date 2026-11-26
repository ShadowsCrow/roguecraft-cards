package com.roguecards.network;

import com.roguecards.client.ClientRogueData;
import com.roguecards.xp.RogueXp;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("4");
        // O corpo do lambda só roda no cliente, então a classe do cliente não carrega no servidor.
        registrar.playToClient(SyncRoguePayload.TYPE, SyncRoguePayload.STREAM_CODEC,
                (payload, context) -> ClientRogueData.apply(payload));
        registrar.playToServer(ChooseCardPayload.TYPE, ChooseCardPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        RogueXp.choose(player, payload.index());
                    }
                });
        registrar.playToServer(CardActionPayload.TYPE, CardActionPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (payload.action() == CardActionPayload.REROLL) {
                            RogueXp.reroll(player);
                        } else if (payload.action() == CardActionPayload.BAN) {
                            RogueXp.ban(player, payload.card());
                        }
                    }
                });
    }
}
