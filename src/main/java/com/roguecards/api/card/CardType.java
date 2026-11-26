package com.roguecards.api.card;

import static java.lang.Double.NaN;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.roguecards.RoguecraftCards;
import com.roguecards.api.RoguecraftApi;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Uma carta. Parte da API pública: outros mods criam cartas com {@link #builder()} e registram no
 * registro {@link RoguecraftApi#CARD_REGISTRY_KEY} (DeferredRegister.create(RoguecraftApi.CARD_REGISTRY_KEY, "seumod")).
 *
 * Cada carta tem: tipo (categoria), nível azul mínimo, valor por raridade (NaN = não existe nessa raridade,
 * na ordem comum, incomum, rara, épica, lendária), limite de escolhas, teto do efeito somado,
 * modo ao repetir e, opcionalmente, um atributo do jogador que recebe o valor.
 *
 * As 24 cartas do próprio mod estão aqui como constantes (VITALITY, LUCK...) e usam o mesmo registro.
 * A tabela completa, com explicação, está em docs/cartas.html.
 */
public class CardType {
    /** Carta sem limite de vezes: para só quando o total chega ao teto. */
    public static final int UNLIMITED = Integer.MAX_VALUE;
    public static final String BLOOD_PACT_HEALTH_KEY = "blood_pact_health";

    /** Cartas do próprio mod, na ordem de registro (nome no registro → carta). */
    private static final Map<String, CardType> BUILTIN = new LinkedHashMap<>();

    // ---------- Atributos (nível 1) ----------
    public static final CardType VITALITY = builtin("vitality", builder().category(Category.ATTRIBUTE).values(2, 3, 4, 6, 10)
            .limit(10).cap(40).format(Format.HEARTS).icon(() -> Items.GLISTERING_MELON_SLICE)
            .attribute(() -> Attributes.MAX_HEALTH, Operation.ADD_VALUE));
    public static final CardType STRENGTH = builtin("strength", builder().category(Category.ATTRIBUTE).values(0.5, 1, 1.5, 2.5, 4)
            .limit(10).cap(15).icon(() -> Items.IRON_SWORD).attribute(() -> Attributes.ATTACK_DAMAGE, Operation.ADD_VALUE));
    public static final CardType TOUGHNESS = builtin("toughness", builder().category(Category.ATTRIBUTE).values(1, 1.5, 2, 3, 5)
            .limit(10).cap(15).icon(() -> Items.IRON_CHESTPLATE).attribute(() -> Attributes.ARMOR, Operation.ADD_VALUE));
    public static final CardType AGILITY = builtin("agility", builder().category(Category.ATTRIBUTE).values(0.03, 0.05, 0.07, 0.10, 0.15)
            .limit(8).cap(0.5).format(Format.PERCENT).icon(() -> Items.SUGAR)
            .attribute(() -> Attributes.MOVEMENT_SPEED, Operation.ADD_MULTIPLIED_BASE));
    public static final CardType LUCK = builtin("luck", builder().category(Category.ATTRIBUTE).values(3, 4, 6, 9, 14)
            .limit(UNLIMITED).cap(50).icon(() -> Items.RABBIT_FOOT).attribute(() -> Attributes.LUCK, Operation.ADD_VALUE));
    public static final CardType WISDOM = builtin("wisdom", builder().category(Category.ATTRIBUTE).values(0.05, 0.10, 0.15, 0.25, 0.40)
            .limit(UNLIMITED).cap(2.0).format(Format.PERCENT).icon(() -> Items.EXPERIENCE_BOTTLE)
            .attribute(() -> RoguecraftCards.XP_GAIN, Operation.ADD_VALUE));
    public static final CardType MINING = builtin("mining", builder().category(Category.ATTRIBUTE).values(0.05, 0.10, 0.15, 0.25, 0.40)
            .limit(10).cap(2.0).format(Format.PERCENT).icon(() -> Items.IRON_PICKAXE)
            .attribute(() -> Attributes.BLOCK_BREAK_SPEED, Operation.ADD_MULTIPLIED_BASE));

    // ---------- Combate ----------
    public static final CardType THORNS = builtin("thorns", builder().category(Category.COMBAT).minLevel(20)
            .values(NaN, 0.10, 0.15, 0.25, 0.40).limit(5).cap(0.6).format(Format.PERCENT).icon(() -> Items.CACTUS));
    public static final CardType CRITICAL = builtin("critical", builder().category(Category.COMBAT).minLevel(20)
            .values(NaN, 0.05, 0.08, 0.12, 0.18).limit(6).cap(0.4).format(Format.PERCENT).icon(() -> Items.GOLDEN_SWORD));
    public static final CardType VAMPIRISM = builtin("vampirism", builder().category(Category.COMBAT).minLevel(25)
            .values(NaN, NaN, 0.03, 0.05, 0.08).limit(5).cap(0.2).format(Format.PERCENT).icon(() -> Items.FERMENTED_SPIDER_EYE));
    public static final CardType EXECUTION = builtin("execution", builder().category(Category.COMBAT).minLevel(30)
            .values(NaN, NaN, 0.15, 0.25, 0.40).limit(4).cap(1.0).format(Format.PERCENT).icon(() -> Items.NETHERITE_AXE));
    public static final CardType STORM = builtin("storm", builder().category(Category.COMBAT).minLevel(40)
            .values(NaN, NaN, NaN, 0.05, 0.10).limit(3).cap(0.2).format(Format.PERCENT).icon(() -> Items.LIGHTNING_ROD));
    /** Valor = dano extra. A perda de vida fica em BLOOD_PACT_HEALTH_KEY: épica −2 corações, lendária −1. */
    public static final CardType BLOOD_PACT = builtin("blood_pact", builder().category(Category.COMBAT).minLevel(40)
            .values(NaN, NaN, NaN, 3, 6).limit(2).format(Format.BLOOD_PACT).icon(() -> Items.NETHER_WART));

    // ---------- Sobrevivência ----------
    public static final CardType FEATHER_FALL = builtin("feather_fall", builder().category(Category.SURVIVAL).minLevel(10)
            .values(0.15, 0.25, 0.35, 0.50, 0.75).limit(4).cap(1.0).format(Format.PERCENT).icon(() -> Items.FEATHER));
    public static final CardType SATIETY = builtin("satiety", builder().category(Category.SURVIVAL).minLevel(10)
            .values(0.10, 0.15, 0.25, NaN, NaN).limit(4).cap(0.6).format(Format.PERCENT).icon(() -> Items.COOKED_BEEF));
    /** Segundos entre curas. Menor é melhor. */
    public static final CardType REGENERATION = builtin("regeneration", builder().category(Category.SURVIVAL).minLevel(30)
            .values(NaN, NaN, 10, 6, 3).limit(3).cap(2).stack(Stack.BEST_LOWER).icon(() -> Items.GOLDEN_APPLE));
    /** Minutos de espera. Menor é melhor. */
    public static final CardType SECOND_CHANCE = builtin("second_chance", builder().category(Category.SURVIVAL).minLevel(45)
            .values(NaN, NaN, NaN, 15, 8).limit(2).cap(5).stack(Stack.BEST_LOWER).icon(() -> Items.TOTEM_OF_UNDYING));

    // ---------- Exploração ----------
    public static final CardType MAGNET = builtin("magnet", builder().category(Category.EXPLORATION).minLevel(12)
            .values(NaN, 4, 6, 8, NaN).limit(4).cap(12).icon(() -> Items.LODESTONE));
    public static final CardType MOLE = builtin("mole", builder().category(Category.EXPLORATION).minLevel(18)
            .values(NaN, NaN, 1, NaN, NaN).limit(1).stack(Stack.UNIQUE).icon(() -> Items.GLOW_BERRIES));
    public static final CardType PROSPECTOR = builtin("prospector", builder().category(Category.EXPLORATION).minLevel(50)
            .values(NaN, NaN, NaN, 0.05, 0.10).limit(3).cap(0.25).format(Format.PERCENT).icon(() -> Items.RAW_GOLD));

    // ---------- Cartas que mexem nas cartas ----------
    /**
     * Cada cópia vem mais rara e espera mais escolhas: 1ª incomum (a partir da 5ª escolha de carta),
     * 2ª rara (20 escolhas depois de pegar a 1ª), 3ª épica (40 escolhas depois de pegar a 2ª).
     */
    public static final CardType REROLL = builtin("reroll", builder().category(Category.CARDS).minLevel(5)
            .values(NaN, 1, 1, 1, NaN).limit(3).cap(3).icon(() -> Items.PAPER)
            .tiers(new Tier(Rarity.UNCOMMON, 5), new Tier(Rarity.RARE, 20), new Tier(Rarity.EPIC, 40)));
    public static final CardType HARVEST = builtin("harvest", builder().category(Category.CARDS).minLevel(15)
            .values(NaN, 1, 2, 3, NaN).limit(4).cap(8).icon(() -> Items.BONE));
    /**
     * Cada cópia vem mais rara e espera mais escolhas: 1º raro (a partir da 10ª escolha de carta),
     * 2º épico (25 escolhas depois de pegar o 1º), 3º lendário (45 escolhas depois de pegar o 2º).
     */
    public static final CardType BANISH = builtin("banish", builder().category(Category.CARDS).minLevel(10)
            .values(NaN, NaN, 1, 1, 1).limit(3).cap(3).format(Format.NONE).icon(() -> Items.BARRIER)
            .tiers(new Tier(Rarity.RARE, 10), new Tier(Rarity.EPIC, 25), new Tier(Rarity.LEGENDARY, 45)));
    public static final CardType FULL_HAND = builtin("full_hand", builder().category(Category.CARDS).minLevel(60)
            .values(NaN, NaN, NaN, NaN, 1).limit(1).stack(Stack.UNIQUE).format(Format.NONE).icon(() -> Items.BOOK));

    // ---------- Tipos auxiliares ----------

    /** Tipo de carta (atributo, combate...). Outros mods podem criar tipos novos com {@link #register}. */
    public static final class Category {
        private static final List<Category> ALL = new ArrayList<>();

        public static final Category ATTRIBUTE = register(RoguecraftCards.id("attribute"), 0x7FB8FF);
        public static final Category COMBAT = register(RoguecraftCards.id("combat"), 0xFF7A7A);
        public static final Category SURVIVAL = register(RoguecraftCards.id("survival"), 0x6FE0A8);
        public static final Category EXPLORATION = register(RoguecraftCards.id("exploration"), 0xF2C94C);
        public static final Category CARDS = register(RoguecraftCards.id("cards"), 0xC79BFF);

        private final ResourceLocation id;
        private final int color;

        private Category(ResourceLocation id, int color) {
            this.id = id;
            this.color = color;
        }

        /** Cria um tipo novo. Texto: "category.&lt;namespace&gt;.&lt;nome&gt;" no arquivo de idioma do seu mod. */
        public static synchronized Category register(ResourceLocation id, int color) {
            for (Category c : ALL) {
                if (c.id.equals(id)) {
                    return c;
                }
            }
            Category category = new Category(id, color);
            ALL.add(category);
            return category;
        }

        /** Todos os tipos, na ordem de criação. */
        public static List<Category> values() {
            return Collections.unmodifiableList(ALL);
        }

        public ResourceLocation id() {
            return id;
        }

        public int color() {
            return color;
        }

        public Component displayName() {
            return Component.translatable("category." + id.getNamespace() + "." + id.getPath());
        }
    }

    /** O que acontece ao pegar a mesma carta de novo. */
    public enum Stack {
        /** Soma os valores, até o teto. */
        SUM,
        /** Valor em que menor é melhor (segundos, minutos): fica o melhor e ainda melhora 20%, até o teto. */
        BEST_LOWER,
        /** Só pode ser pega uma vez. */
        UNIQUE
    }

    /** Como o valor aparece no texto: número, porcentagem (0,05 → "5%"), corações (2 → "1") ou nada. */
    public enum Format { NUMBER, PERCENT, HEARTS, BLOOD_PACT, NONE }

    /**
     * Degrau de uma carta com cópias espaçadas: a raridade da próxima cópia e quantas escolhas de carta
     * ela espera. A 1ª cópia conta as escolhas desde o começo; as outras, desde que a cópia anterior foi pega.
     */
    public record Tier(Rarity rarity, int choices) {
    }

    // ---------- Construção ----------

    public static Builder builder() {
        return new Builder();
    }

    private static CardType builtin(String name, Builder builder) {
        CardType card = builder.build();
        BUILTIN.put(name, card);
        return card;
    }

    /** Cartas do próprio mod, para o registro (uso interno). */
    public static Map<String, CardType> builtins() {
        return Collections.unmodifiableMap(BUILTIN);
    }

    /**
     * Monta uma carta. Exemplo: builder().category(Category.COMBAT).minLevel(5).values(NaN, 0.05, 0.08, 0.12, 0.18)
     * .limit(6).cap(0.4).format(Format.PERCENT).icon(() -> Items.GOLDEN_SWORD).build().
     */
    public static class Builder {
        private Category category = Category.ATTRIBUTE;
        private int minLevel = 1;
        private double[] values = {NaN, NaN, NaN, NaN, NaN};
        private int limit = 1;
        private double cap = NaN;
        private Stack stack = Stack.SUM;
        private Format format = Format.NUMBER;
        private Supplier<Item> icon = () -> Items.PAPER;
        private Supplier<Holder<Attribute>> attribute;
        private Operation operation;
        private Tier[] tiers = new Tier[0];

        public Builder category(Category category) {
            this.category = category;
            return this;
        }

        public Builder minLevel(int minLevel) {
            this.minLevel = minLevel;
            return this;
        }

        /** Valor por raridade: comum, incomum, rara, épica, lendária. Use Double.NaN onde a carta não existe. */
        public Builder values(double common, double uncommon, double rare, double epic, double legendary) {
            this.values = new double[] {common, uncommon, rare, epic, legendary};
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder cap(double cap) {
            this.cap = cap;
            return this;
        }

        public Builder stack(Stack stack) {
            this.stack = stack;
            return this;
        }

        public Builder format(Format format) {
            this.format = format;
            return this;
        }

        public Builder icon(Supplier<Item> icon) {
            this.icon = icon;
            return this;
        }

        /** A soma da carta vira um modificador neste atributo do jogador (ex.: Attributes.LUCK, ADD_VALUE). */
        public Builder attribute(Supplier<Holder<Attribute>> attribute, Operation operation) {
            this.attribute = attribute;
            this.operation = operation;
            return this;
        }

        /** Cada cópia nova vem nesta raridade e só a partir deste nível (1ª, 2ª, 3ª...). */
        public Builder tiers(Tier... tiers) {
            this.tiers = tiers;
            return this;
        }

        public CardType build() {
            return new CardType(this);
        }
    }

    private final Category category;
    private final int minLevel;
    private final double[] values;
    private final int limit;
    private final double cap;
    private final Stack stack;
    private final Format format;
    private final Supplier<Item> icon;
    private final Supplier<Holder<Attribute>> attribute;
    private final Operation operation;
    private final Tier[] tiers;
    private ResourceLocation key;

    protected CardType(Builder b) {
        this.category = b.category;
        this.minLevel = b.minLevel;
        this.values = b.values;
        this.limit = b.limit;
        this.cap = b.cap;
        this.stack = b.stack;
        this.format = b.format;
        this.icon = b.icon;
        this.attribute = b.attribute;
        this.operation = b.operation;
        this.tiers = b.tiers;
    }

    /**
     * Chamado no servidor quando o jogador escolhe esta carta, depois que o total já foi somado.
     * Sobrescreva para efeitos que acontecem na hora da escolha.
     */
    public void onChosen(ServerPlayer player, Rarity rarity, double value) {
    }

    // ---------- Identidade ----------

    /** Nome no registro, por exemplo roguecards:luck. */
    public ResourceLocation key() {
        if (key == null) {
            key = RoguecraftApi.CARDS.getKey(this);
        }
        return key;
    }

    /** Nome no registro como texto, usado nos dados salvos. Exemplo: "roguecards:luck". */
    public String id() {
        ResourceLocation k = key();
        return k == null ? "unregistered" : k.toString();
    }

    private String langKey() {
        ResourceLocation k = key();
        return k == null ? "card.unregistered" : "card." + k.getNamespace() + "." + k.getPath();
    }

    public ResourceLocation modifierId() {
        ResourceLocation k = key();
        return ResourceLocation.fromNamespaceAndPath(k.getNamespace(), "card_" + k.getPath());
    }

    /** Todas as cartas registradas (do mod e de outros mods), na ordem de registro. */
    public static List<CardType> values() {
        return RoguecraftApi.CARDS.stream().toList();
    }

    /** Procura uma carta pelo nome. Sem namespace, usa "roguecards". Null se não existir. */
    public static CardType byId(String id) {
        ResourceLocation rl = id.contains(":") ? ResourceLocation.tryParse(id) : RoguecraftCards.id(id);
        return rl == null ? null : RoguecraftApi.CARDS.get(rl);
    }

    // ---------- Regras ----------

    public Category category() {
        return category;
    }

    public int minLevel() {
        return minLevel;
    }

    public int limit() {
        return limit;
    }

    public boolean unlimited() {
        return limit == UNLIMITED;
    }

    public Stack stack() {
        return stack;
    }

    /** True para as cartas em que cada cópia vem numa raridade e num nível mais altos. */
    public boolean tiered() {
        return tiers.length > 0;
    }

    /** Raridade e nível da próxima cópia, dado quantas o jogador já pegou. Null se já pegou todas. */
    public Tier tier(int picks) {
        return picks >= 0 && picks < tiers.length ? tiers[picks] : null;
    }

    public boolean exists(Rarity rarity) {
        return !Double.isNaN(values[rarity.ordinal()]);
    }

    /** Menor raridade em que a carta existe. */
    public Rarity minRarity() {
        for (Rarity r : Rarity.values()) {
            if (exists(r)) {
                return r;
            }
        }
        return Rarity.COMMON;
    }

    public double value(Rarity rarity) {
        double v = values[rarity.ordinal()];
        return Double.isNaN(v) ? 0 : v;
    }

    public boolean hasCap() {
        return !Double.isNaN(cap);
    }

    public double cap() {
        return cap;
    }

    /** Carta de atributo direto (vira um modificador de atributo no jogador). */
    public boolean isAttributeCard() {
        return attribute != null;
    }

    public Holder<Attribute> attribute() {
        return attribute == null ? null : attribute.get();
    }

    public Operation operation() {
        return operation;
    }

    public Item icon() {
        return icon.get();
    }

    /** Novo total depois de pegar a carta com este valor. */
    public double combine(double current, boolean hadBefore, double value) {
        return switch (stack) {
            case SUM -> hasCap() ? Math.min(cap, current + value) : current + value;
            case BEST_LOWER -> hadBefore ? Math.max(hasCap() ? cap : 0, Math.min(current, value) * 0.8) : value;
            case UNIQUE -> value;
        };
    }

    /** True quando o total já chegou ao teto (a carta para de aparecer). */
    public boolean atCap(double current, boolean hadBefore) {
        if (!hasCap() || !hadBefore) {
            return false;
        }
        return stack == Stack.BEST_LOWER ? current <= cap + 1e-9 : current >= cap - 1e-9;
    }

    // ---------- Textos ----------

    /** Nome da carta. Texto: "card.&lt;namespace&gt;.&lt;nome&gt;" no arquivo de idioma. */
    public Component displayName() {
        return Component.translatable(langKey());
    }

    /** Texto do efeito ("card.&lt;ns&gt;.&lt;nome&gt;.desc"), por exemplo "+1,5 coração de vida máxima". */
    public Component describe(Rarity rarity) {
        double value = value(rarity);
        if (format == Format.BLOOD_PACT) {
            return Component.translatable(langKey() + ".desc", format(value), format(bloodPactHealthLoss(rarity) / 2));
        }
        return Component.translatable(langKey() + ".desc", format(value));
    }

    /** Texto do total acumulado ("card.&lt;ns&gt;.&lt;nome&gt;.total"). */
    public Component describeTotal(double total, double extra) {
        if (format == Format.BLOOD_PACT) {
            return Component.translatable(langKey() + ".total", format(total), format(extra / 2));
        }
        return Component.translatable(langKey() + ".total", format(total));
    }

    /** Vida perdida (em pontos; 2 = 1 coração) por um Pacto de Sangue desta raridade. */
    public static double bloodPactHealthLoss(Rarity rarity) {
        return rarity == Rarity.LEGENDARY ? 2 : 4;
    }

    /** Valor formatado como a carta mostra (porcentagem, corações ou número). Exemplo: Sabedoria 0,3 → "30%". */
    public String formatAmount(double v) {
        return format(v);
    }

    private String format(double v) {
        return switch (format) {
            case PERCENT -> trim(v * 100) + "%";
            case HEARTS -> trim(v / 2);
            default -> trim(v);
        };
    }

    public static String trim(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    @Override
    public String toString() {
        return id();
    }
}
