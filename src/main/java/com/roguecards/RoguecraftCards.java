package com.roguecards;

import java.util.function.Supplier;

import org.slf4j.Logger;

import com.roguecards.item.ModItems;
import com.roguecards.network.ModNetwork;
import com.roguecards.xp.BlueXpOrb;
import com.roguecards.xp.RogueData;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Entrada do mod. Registra o atributo de ganho de XP azul, a esfera azul,
 * os dados do jogador (nível, XP e cartas) e a rede.
 */
@Mod(RoguecraftCards.MODID)
public class RoguecraftCards {
    public static final String MODID = "roguecards";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

    /** Multiplicador do XP azul recebido. 1.0 = normal; a carta Sabedoria soma a ele. */
    public static final DeferredHolder<Attribute, Attribute> XP_GAIN = ATTRIBUTES.register("xp_gain",
            () -> new RangedAttribute("attribute.roguecards.xp_gain", 1.0, 0.0, 1024.0).setSyncable(true));

    public static final DeferredHolder<EntityType<?>, EntityType<BlueXpOrb>> BLUE_XP_ORB = ENTITY_TYPES.register("blue_xp_orb",
            () -> EntityType.Builder.<BlueXpOrb>of(BlueXpOrb::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build("blue_xp_orb"));

    /** Nível azul, XP azul, cartas pendentes e bônus escolhidos. Fica com o jogador quando ele morre. */
    public static final Supplier<AttachmentType<RogueData>> ROGUE_DATA = ATTACHMENT_TYPES.register("rogue_data",
            () -> AttachmentType.builder(() -> new RogueData()).serialize(RogueData.CODEC).copyOnDeath().build());

    /** Registra as cartas do próprio mod no registro público de cartas. */
    public static final net.neoforged.neoforge.registries.DeferredRegister<com.roguecards.api.card.CardType> CARDS =
            net.neoforged.neoforge.registries.DeferredRegister.create(com.roguecards.api.RoguecraftApi.CARD_REGISTRY_KEY, MODID);

    static {
        com.roguecards.api.card.CardType.builtins().forEach((name, card) -> CARDS.register(name, () -> card));
    }

    public RoguecraftCards(IEventBus modBus, ModContainer container) {
        modBus.addListener((net.neoforged.neoforge.registries.NewRegistryEvent event) -> event.register(com.roguecards.api.RoguecraftApi.CARDS));
        CARDS.register(modBus);
        // O berloque da sorte soma ao limite do sorteio de raridade (o mesmo gancho que outros mods podem usar).
        com.roguecards.api.RoguecraftApi.addRarityLuckSource(com.roguecards.item.CharmLuck::equipped);
        ATTRIBUTES.register(modBus);
        ENTITY_TYPES.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
        ModItems.register(modBus);
        ModSounds.register(modBus);
        ModStats.register(modBus);

        modBus.addListener(RoguecraftCards::onAttributes);
        modBus.addListener(ModNetwork::register);

        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    private static void onAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, XP_GAIN);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
