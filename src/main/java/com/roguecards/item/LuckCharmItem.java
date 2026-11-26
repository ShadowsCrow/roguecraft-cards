package com.roguecards.item;

import java.util.List;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Berloque da Sorte: vai no espaço de amuleto do Curios e dá sorte enquanto está equipado.
 * Ferro +1, ouro +2, diamante +3, netherita +5.
 * A sorte do berloque também aumenta o limite do sorteio de raridade (ver CharmLuck).
 */
public class LuckCharmItem extends Item implements ICurioItem {
    private final double luck;

    public LuckCharmItem(Properties properties, double luck) {
        super(properties);
        this.luck = luck;
    }

    public double luck() {
        return luck;
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create();
        modifiers.put(Attributes.LUCK, new AttributeModifier(id, luck, AttributeModifier.Operation.ADD_VALUE));
        return modifiers;
    }

    /** Clicar com o botão direito segurando o berloque equipa ele direto no espaço de amuleto. */
    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.roguecards.luck_charm").withStyle(ChatFormatting.GRAY));
    }
}
