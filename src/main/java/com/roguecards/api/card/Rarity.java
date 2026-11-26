package com.roguecards.api.card;

import java.util.function.Supplier;

import com.roguecards.Config;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * Raridade de uma carta.
 * Comum sempre pode aparecer. As outras só aparecem a partir de uma sorte mínima,
 * e ficam mais frequentes a cada ponto de sorte acima do mínimo.
 * Valores padrão (mínimo de sorte → crescimento por ponto):
 * incomum 1 → 10, rara 3 → 5, épica 6 → 3, lendária 10 → 2.
 * Exemplo com sorte 0: 100% comum. Com sorte 10: comum 39%, incomum 39%, rara 16%, épica 6%, lendária 0,8%.
 */
public enum Rarity {
    COMMON("common", 0xFFFFFF, () -> 0.0, () -> 0.0),
    UNCOMMON("uncommon", 0x55FF55, Config.UNCOMMON_MIN_LUCK::get, Config.UNCOMMON_GROWTH::get),
    RARE("rare", 0x55AAFF, Config.RARE_MIN_LUCK::get, Config.RARE_GROWTH::get),
    EPIC("epic", 0xC060FF, Config.EPIC_MIN_LUCK::get, Config.EPIC_GROWTH::get),
    LEGENDARY("legendary", 0xFFAA00, Config.LEGENDARY_MIN_LUCK::get, Config.LEGENDARY_GROWTH::get);

    private final String id;
    private final int color;
    private final Supplier<Double> minLuck;
    private final Supplier<Double> growth;

    Rarity(String id, int color, Supplier<Double> minLuck, Supplier<Double> growth) {
        this.id = id;
        this.color = color;
        this.minLuck = minLuck;
        this.growth = growth;
    }

    public int color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("rarity.roguecards." + id);
    }

    /** Sorte mínima para esta raridade aparecer (0 para comum). */
    public double minLuck() {
        return minLuck.get();
    }

    /** Peso no sorteio. Zero quando a sorte está abaixo do mínimo. */
    public double weight(double luck) {
        if (this == COMMON) {
            return Config.COMMON_WEIGHT.get();
        }
        double min = minLuck.get();
        if (luck < min) {
            return 0;
        }
        return growth.get() * (luck - min + 1.0);
    }

    /** Chance de cada raridade, de 0 a 1, na ordem comum → lendária. */
    public static double[] chances(double luck) {
        return chances(luck, 0);
    }

    /**
     * Chance de cada raridade com um limite extra de sorte (a sorte dos berloques equipados).
     * O sorteio usa no máximo a sorte do limite (padrão 50) mais esse extra.
     * Exemplo: sorte 55 com berloque de netherita (+5) → usa 55; sem o berloque → usa 50.
     */
    public static double[] chances(double luck, double extraCap) {
        luck = Math.min(luck, Config.MAX_LUCK.get() + Math.max(0, extraCap));
        Rarity[] values = values();
        double[] result = new double[values.length];
        double total = 0;
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i].weight(luck);
            total += result[i];
        }
        for (int i = 0; i < values.length; i++) {
            result[i] = total > 0 ? result[i] / total : (i == 0 ? 1 : 0);
        }
        return result;
    }

    public static Rarity byIndex(int index) {
        Rarity[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    public static Rarity roll(RandomSource random, double luck) {
        return roll(random, luck, 0);
    }

    public static Rarity roll(RandomSource random, double luck, double extraCap) {
        double[] chances = chances(luck, extraCap);
        double pick = random.nextDouble();
        for (int i = 0; i < chances.length; i++) {
            pick -= chances[i];
            if (pick < 0) {
                return values()[i];
            }
        }
        return COMMON;
    }
}
