package com.roguecards.item;

import com.roguecards.RoguecraftCards;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Itens do mod: o trevo de quatro folhas e os 4 berloques da sorte, mais a aba do modo criativo. */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RoguecraftCards.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RoguecraftCards.MODID);

    /** Cai ao quebrar grama, com chance que sobe com a sorte. Ingrediente dos berloques. */
    public static final DeferredItem<Item> FOUR_LEAF_CLOVER = ITEMS.registerSimpleItem("four_leaf_clover",
            new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<LuckCharmItem> IRON_LUCK_CHARM = ITEMS.registerItem("iron_luck_charm",
            p -> new LuckCharmItem(p, 1), new Item.Properties().stacksTo(1));
    public static final DeferredItem<LuckCharmItem> GOLD_LUCK_CHARM = ITEMS.registerItem("gold_luck_charm",
            p -> new LuckCharmItem(p, 2), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<LuckCharmItem> DIAMOND_LUCK_CHARM = ITEMS.registerItem("diamond_luck_charm",
            p -> new LuckCharmItem(p, 3), new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<LuckCharmItem> NETHERITE_LUCK_CHARM = ITEMS.registerItem("netherite_luck_charm",
            p -> new LuckCharmItem(p, 5), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.roguecards"))
            .icon(() -> new ItemStack(FOUR_LEAF_CLOVER.get()))
            .displayItems((params, output) -> {
                output.accept(FOUR_LEAF_CLOVER.get());
                output.accept(IRON_LUCK_CHARM.get());
                output.accept(GOLD_LUCK_CHARM.get());
                output.accept(DIAMOND_LUCK_CHARM.get());
                output.accept(NETHERITE_LUCK_CHARM.get());
            })
            .build());

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
