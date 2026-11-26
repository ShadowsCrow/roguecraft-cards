package com.roguecards;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sons do mod. Os arquivos ficam em assets/roguecards/sounds e a lista em assets/roguecards/sounds.json.
 * Origem dos arquivos: ver assets/roguecards/sounds/creditos.txt. Os sons da revelação das cartas
 * são gerados pelo script design/sons/sintetizar.py.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, RoguecraftCards.MODID);

    /** Toca quando o jogador sobe de nível azul. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL_UP = register("level_up");

    /** Toca quando o jogador escolhe uma carta (4 variações sorteadas). */
    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_PICK = register("card_pick");

    /** Carta virando na tela de cartas (3 variações sorteadas). Toca só no cliente. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CARD_FLIP = register("card_flip");

    /** Lendária prestes a virar: som subindo, criando expectativa. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LEGENDARY_CHARGE = register("legendary_charge");

    /** Revelação de uma carta, um som por raridade (comum → lendária), cada um mais rico que o anterior. */
    public static final List<DeferredHolder<SoundEvent, SoundEvent>> REVEAL = List.of(
            register("reveal_common"),
            register("reveal_uncommon"),
            register("reveal_rare"),
            register("reveal_epic"),
            register("reveal_legendary"));

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(RoguecraftCards.id(name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
