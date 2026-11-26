package com.roguecards.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.roguecards.RoguecraftCards;
import com.roguecards.api.card.CardOffer;
import com.roguecards.api.card.CardType;
import com.roguecards.api.card.Rarity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Prints automáticos da revelação das cartas (só para desenvolvimento).
 * Liga quando o jogo abre com -Droguecards.revealDemo=true (gradlew runClient -Prevelacao):
 * na tela inicial, abre a tela de cartas com ofertas de teste, para a animação em momentos escolhidos,
 * salva um print de cada momento em screenshots/revelacao_NN_nome.png e fecha o jogo.
 * Oferta 1: incomum, rara e épica. Oferta 2: rara, lendária e comum.
 */
@EventBusSubscriber(modid = RoguecraftCards.MODID, value = Dist.CLIENT)
public class RevealDemoScreen extends CardScreen {
    private static boolean started;

    private record Shot(int offer, long time, String name) {
    }

    /** Momentos de cada print, em milissegundos desde que a oferta apareceu (ver os tempos em CardReveal). */
    private static final List<Shot> SHOTS = List.of(
            new Shot(0, 120, "chegando"),
            new Shot(0, 600, "antes_de_virar"),
            new Shot(0, 920, "virando"),
            new Shot(0, 1260, "epica_revelada"),
            new Shot(0, 3200, "depois"),
            new Shot(1, 1150, "lendaria_expectativa"),
            new Shot(1, 1440, "lendaria_virando"),
            new Shot(1, 1650, "lendaria_revelada"),
            new Shot(1, 4500, "lendaria_depois"));

    private final List<List<CardOffer>> offers = new ArrayList<>();
    private int shot;
    private int ticks;
    private int currentOffer = -1;

    private RevealDemoScreen() {
        offers.add(List.of(offer(Rarity.UNCOMMON, 0), offer(Rarity.RARE, 1), offer(Rarity.EPIC, 2)));
        offers.add(List.of(offer(Rarity.RARE, 3), offer(Rarity.LEGENDARY, 0), offer(Rarity.COMMON, 1)));
        reveal.muted = true;
    }

    /** N-ésima carta (sem repetir na oferta) que existe nesta raridade. */
    private static CardOffer offer(Rarity rarity, int skip) {
        List<CardType> list = new ArrayList<>();
        for (CardType type : CardType.values()) {
            if (type.exists(rarity) && type.key() != null && RoguecraftCards.MODID.equals(type.key().getNamespace())) {
                list.add(type);
            }
        }
        return CardOffer.of(list.get(Math.min(skip, list.size() - 1)), rarity);
    }

    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!started && event.getScreen() instanceof TitleScreen && Boolean.getBoolean("roguecards.revealDemo")) {
            started = true;
            ClientRogueData.level = 12;
            ClientRogueData.xp = 34;
            ClientRogueData.xpNeeded = 80;
            ClientRogueData.pending = 2;
            ClientRogueData.bonuses = Map.of();
            ClientRogueData.picks = Map.of();
            ClientRogueData.history = List.of();
            Minecraft.getInstance().setScreen(new RevealDemoScreen());
        }
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || shot >= SHOTS.size()) {
            ticks = 0;
            return;
        }
        Shot s = SHOTS.get(shot);
        if (ticks == 0) {
            if (currentOffer != s.offer()) {
                currentOffer = s.offer();
                reveal.forceFull();
                ClientRogueData.offer = offers.get(currentOffer);
            }
            reveal.fixedElapsed = s.time();
        }
        ticks++;
        // Espera alguns quadros com o tempo parado; o print lê a última imagem desenhada.
        if (ticks == 8) {
            String name = String.format("revelacao_%02d_%s.png", shot + 1, s.name());
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> { });
        }
        if (ticks >= 12) {
            ticks = 0;
            shot++;
            if (shot >= SHOTS.size()) {
                RoguecraftCards.LOGGER.info("Revelação: {} prints salvos em screenshots/", SHOTS.size());
                mc.stop();
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
