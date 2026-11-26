package com.roguecards.xp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.roguecards.Config;
import com.roguecards.ModStats;
import com.roguecards.RoguecraftCards;
import com.roguecards.api.card.CardOffer;
import com.roguecards.api.card.CardType;
import com.roguecards.api.card.Rarity;
import com.roguecards.network.SyncRoguePayload;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Regras do XP azul: ganho, nível, ofertas de cartas, trocas, banimentos e bônus. Roda só no servidor. */
@EventBusSubscriber(modid = RoguecraftCards.MODID)
public final class RogueXp {
    private static final ResourceLocation BLOOD_PACT_HEALTH_ID = RoguecraftCards.id("card_blood_pact_health");

    /**
     * XP normal que deve ser ignorado (o XP que um jogador solta ao morrer).
     * O evento de morte marca o valor; as esferas normais criadas logo depois descontam dele.
     */
    private static final ThreadLocal<int[]> SKIP_VANILLA = ThreadLocal.withInitial(() -> new int[1]);

    private RogueXp() {
    }

    public static RogueData get(Player player) {
        return player.getData(RoguecraftCards.ROGUE_DATA);
    }

    /** Total atual de uma carta no jogador (0 se nunca pegou). */
    public static double bonus(Player player, CardType type) {
        return get(player).bonuses.getOrDefault(type.id(), 0.0);
    }

    /** Nível azul máximo; sem máximo quando a config vale 0 (padrão). */
    public static int maxLevel() {
        int cap = Config.MAX_LEVEL.get();
        return cap <= 0 ? Integer.MAX_VALUE : cap;
    }

    /** XP azul para sair do nível informado. Exemplo com os valores padrão: nível 0 → 20, nível 5 → 45. */
    public static int xpNeeded(int level) {
        return Config.BASE_XP_PER_LEVEL.get() + Config.EXTRA_XP_PER_LEVEL.get() * level;
    }

    /** Cartas por oferta: config offerSize (padrão 3), +1 com a carta Mão Cheia. */
    public static int offerSize(Player player) {
        return Config.OFFER_SIZE.get() + (bonus(player, CardType.FULL_HAND) > 0 ? 1 : 0);
    }

    /** True se a carta está na lista disabledCards da config. */
    public static boolean disabledByConfig(CardType type) {
        return Config.DISABLED_CARDS.get().contains(type.id());
    }

    public static int banTokens(RogueData data) {
        return Math.max(0, (int) Math.round(data.bonuses.getOrDefault(CardType.BANISH.id(), 0.0)) - data.banned.size());
    }

    // ---------- Origem do XP azul ----------

    /** Chamado pelo mixin sempre que o jogo cria XP normal no mundo (mob, minério, fornalha, troca, pesca, procriação). */
    /** Ligado pelo mixin do Frasco de Experiência enquanto ele solta XP. */
    public static final ThreadLocal<boolean[]> FROM_XP_BOTTLE = ThreadLocal.withInitial(() -> new boolean[1]);

    public static void onVanillaOrb(ServerLevel level, double x, double y, double z, int value) {
        if (FROM_XP_BOTTLE.get()[0] && !Config.XP_BOTTLES_GIVE_BLUE_XP.get()) {
            return;
        }
        int[] skip = SKIP_VANILLA.get();
        if (skip[0] > 0) {
            skip[0] -= value;
            return;
        }
        int amount = randomRound(value * Config.XP_RATIO.get(), level.getRandom());
        if (amount > 0) {
            BlueXpOrb.award(level, new Vec3(x, y, z), amount);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Player && !event.isCanceled()) {
            SKIP_VANILLA.get()[0] = event.getDroppedExperience();
        }
    }

    public static final ResourceLocation SOURCE_ORB = RoguecraftCards.id("orb");
    public static final ResourceLocation SOURCE_HARVEST = RoguecraftCards.id("harvest");

    /** XP azul de esfera: passa pela Sabedoria e pelo evento BlueXpGainEvent. */
    public static void gainScaled(ServerPlayer player, double amount) {
        gainScaled(player, amount, SOURCE_ORB);
    }

    /** XP azul que passa pelo multiplicador da Sabedoria e pelo evento BlueXpGainEvent (outros mods podem mudar ou cancelar). */
    public static void gainScaled(ServerPlayer player, double amount, ResourceLocation source) {
        double mult = player.getAttributeValue(RoguecraftCards.XP_GAIN);
        com.roguecards.api.event.BlueXpGainEvent event = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new com.roguecards.api.event.BlueXpGainEvent(player, amount * mult, source));
        if (event.isCanceled()) {
            return;
        }
        addXp(player, randomRound(event.getAmount(), player.getRandom()));
    }

    // ---------- Nível e ofertas ----------

    public static void addXp(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return;
        }
        RogueData data = get(player);
        int oldLevel = data.level;
        int maxLevel = maxLevel();
        if (data.level >= maxLevel) {
            // No nível máximo a barra fica cheia e o XP azul não acumula mais.
            data.xp = 0;
            return;
        }
        data.xp += amount;
        int gained = 0;
        while (data.level < maxLevel && data.xp >= xpNeeded(data.level)) {
            data.xp -= xpNeeded(data.level);
            data.level++;
            data.pending++;
            gained++;
        }
        if (data.level >= maxLevel) {
            data.xp = 0;
        }
        if (gained > 0) {
            if (data.offer.isEmpty()) {
                newChoice(player, data);
            }
            player.awardStat(ModStats.BLUE_LEVELS.get(), gained);
            RogueAdvancements.checkLevel(player, data.level);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new com.roguecards.api.event.BlueLevelUpEvent(player, oldLevel, data.level));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    com.roguecards.ModSounds.LEVEL_UP.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable("message.roguecards.level_up", data.level)
                    .withStyle(ChatFormatting.AQUA), true);
        }
        sync(player);
    }

    /** True se a carta pode entrar numa oferta agora (nível, limite, teto e banimento). */
    public static boolean canOffer(RogueData data, CardType type) {
        if (data.level < type.minLevel() || data.banned.contains(type.id()) || disabledByConfig(type)) {
            return false;
        }
        int picks = data.picks.getOrDefault(type.id(), 0);
        if (type.tiered()) {
            // Trocar Cartas e Banir: cada cópia nova espera um número de escolhas desde a cópia anterior.
            CardType.Tier next = type.tier(picks);
            if (next == null || choicesSinceLastCopy(data, type) < next.choices()) {
                return false;
            }
        }
        if (picks >= type.limit()) {
            return false;
        }
        return !type.atCap(data.bonuses.getOrDefault(type.id(), 0.0), picks > 0);
    }

    /**
     * Escolhas de carta feitas desde a última cópia desta carta, ou desde o começo se ainda não tem nenhuma.
     * Exemplo: pegou a 1ª Trocar Cartas na 8ª escolha e já fez 30 escolhas no total → 22.
     */
    public static int choicesSinceLastCopy(RogueData data, CardType type) {
        int last = -1;
        for (int i = data.history.size() - 1; i >= 0; i--) {
            if (type.id().equals(data.history.get(i).card())) {
                last = i;
                break;
            }
        }
        return data.history.size() - (last + 1);
    }

    /**
     * Sorteia a oferta. Para cada vaga: sorteia a raridade pela sorte; escolhe uma carta que existe
     * nessa raridade e ainda pode aparecer; se nenhuma servir, desce uma raridade. As cartas não se repetem.
     */
    public static void generateOffer(ServerPlayer player, RogueData data) {
        data.offer.clear();
        double luck = player.getAttributeValue(Attributes.LUCK);
        double charmLuck = com.roguecards.api.RoguecraftApi.extraRarityLuck(player);
        RandomSource random = player.getRandom();
        List<CardType> used = new ArrayList<>();
        int size = offerSize(player);
        boolean uncommonUnlocked = luck >= Rarity.UNCOMMON.minLuck();
        int pity = Config.PITY_OFFERS.get();
        boolean guarantee = uncommonUnlocked && pity > 0 && data.commonStreak >= pity;
        for (int i = 0; i < size; i++) {
            Rarity rolled = Rarity.roll(random, luck, charmLuck);
            if (guarantee && i == 0 && rolled == Rarity.COMMON) {
                // Proteção contra azar: a primeira carta desta oferta é pelo menos incomum.
                rolled = Rarity.UNCOMMON;
            }
            CardOffer card = pickCard(data, rolled, used, random);
            if (card != null) {
                used.add(card.type());
                data.offer.add(card);
            }
        }
        guaranteeFirstReroll(data);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new com.roguecards.api.event.CardOfferEvent(player, data.offer));
        boolean onlyCommon = data.offer.stream().allMatch(o -> o.rarityValue() == Rarity.COMMON);
        data.commonStreak = (uncommonUnlocked && onlyCommon) ? data.commonStreak + 1 : 0;
    }

    /**
     * A primeira Trocar Cartas aparece com certeza numa das primeiras ofertas depois de liberar.
     * Exemplo com o padrão 3: se ela não saiu na 1ª nem na 2ª oferta, a 3ª oferta já vem com ela
     * (no lugar da última carta que não for Sorte).
     */
    private static void guaranteeFirstReroll(RogueData data) {
        int limit = Config.REROLL_GUARANTEE_OFFERS.get();
        CardType reroll = CardType.REROLL;
        if (limit <= 0 || data.rerollWait < 0 || data.picks.getOrDefault(reroll.id(), 0) > 0 || !canOffer(data, reroll)) {
            return;
        }
        if (data.offer.stream().anyMatch(o -> reroll.id().equals(o.card()))) {
            data.rerollWait = -1;
            return;
        }
        data.rerollWait++;
        if (data.rerollWait >= limit && !data.offer.isEmpty()) {
            int slot = data.offer.size() - 1;
            while (slot > 0 && CardType.LUCK.id().equals(data.offer.get(slot).card())) {
                slot--;
            }
            data.offer.set(slot, CardOffer.of(reroll, reroll.tier(0).rarity()));
            data.rerollWait = -1;
        }
    }

    /**
     * Escolhe uma carta da raridade sorteada que ainda pode aparecer e não está em "used".
     * Se nenhuma servir, desce uma raridade. Retorna null se nenhuma carta servir em raridade nenhuma.
     */
    private static CardOffer pickCard(RogueData data, Rarity rolled, List<CardType> used, RandomSource random) {
        for (int r = rolled.ordinal(); r >= 0; r--) {
            Rarity rarity = Rarity.byIndex(r);
            List<CardType> pool = new ArrayList<>();
            for (CardType type : CardType.values()) {
                if (type.exists(rarity) && !used.contains(type) && canOffer(data, type) && tierMatches(data, type, rarity)) {
                    pool.add(type);
                }
            }
            if (!pool.isEmpty()) {
                return CardOffer.of(pool.get(random.nextInt(pool.size())), rarity);
            }
        }
        return null;
    }

    /** Cartas com níveis por escolha só aparecem na raridade da próxima cópia. As outras aparecem em qualquer raridade. */
    private static boolean tierMatches(RogueData data, CardType type, Rarity rarity) {
        if (!type.tiered()) {
            return true;
        }
        CardType.Tier next = type.tier(data.picks.getOrDefault(type.id(), 0));
        return next != null && next.rarity() == rarity;
    }

    /** Ofertas que faltam para a proteção garantir uma incomum, ou -1 se ela não vale agora. */
    public static int pityLeft(ServerPlayer player, RogueData data) {
        int pity = Config.PITY_OFFERS.get();
        if (pity <= 0 || player.getAttributeValue(Attributes.LUCK) < Rarity.UNCOMMON.minLuck()) {
            return -1;
        }
        return Math.max(0, pity - data.commonStreak);
    }

    /** O jogador clicou numa carta. O servidor confere tudo antes de aplicar. */
    public static void choose(ServerPlayer player, int index) {
        RogueData data = get(player);
        if (data.pending <= 0 || index < 0 || index >= data.offer.size()) {
            sync(player);
            return;
        }
        CardOffer chosen = data.offer.get(index);
        CardType type = chosen.type();
        if (type != null) {
            Rarity rarity = chosen.rarityValue();
            double value = type.value(rarity);
            int picks = data.picks.getOrDefault(type.id(), 0);
            double current = data.bonuses.getOrDefault(type.id(), 0.0);
            data.bonuses.put(type.id(), type.combine(current, picks > 0, value));
            data.picks.put(type.id(), picks + 1);
            data.history.add(chosen);

            if (type == CardType.BLOOD_PACT) {
                data.bonuses.merge(CardType.BLOOD_PACT_HEALTH_KEY, CardType.bloodPactHealthLoss(rarity), Double::sum);
            }
            applyModifiers(player);
            if (type == CardType.VITALITY) {
                player.heal((float) value);
            }
            type.onChosen(player, rarity, value);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new com.roguecards.api.event.CardChosenEvent(player, type, rarity, value));
            player.awardStat(ModStats.CARDS_CHOSEN.get());
            if (rarity == Rarity.LEGENDARY) {
                player.awardStat(ModStats.LEGENDARY_CARDS.get());
            }
            RogueAdvancements.onCardChosen(player, data, rarity);
        }
        data.pending--;
        data.offer.clear();
        if (data.pending > 0) {
            newChoice(player, data);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                com.roguecards.ModSounds.CARD_PICK.get(), SoundSource.PLAYERS, 1.0F, 0.95F + player.getRandom().nextFloat() * 0.1F);
        sync(player);
    }

    /**
     * Começa uma escolha nova: sorteia a oferta e renova as trocas da carta Trocar Cartas.
     * Exemplo: com Trocar Cartas em 3 e 5 níveis acumulados, cada uma das 5 escolhas começa com 3 trocas.
     */
    public static void newChoice(ServerPlayer player, RogueData data) {
        generateOffer(player, data);
        data.rerollsLeft = (int) Math.round(bonus(player, CardType.REROLL));
    }

    /** Carta Trocar Cartas: troca a oferta atual por uma nova. */
    public static void reroll(ServerPlayer player) {
        RogueData data = get(player);
        if (data.pending > 0 && data.rerollsLeft > 0) {
            data.rerollsLeft--;
            generateOffer(player, data);
            RogueAdvancements.award(player, "reroll");
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        sync(player);
    }

    /**
     * Carta Banir: tira para sempre uma carta que está na oferta atual.
     * Só essa vaga é sorteada de novo; as outras cartas da oferta continuam.
     */
    public static void ban(ServerPlayer player, String cardId) {
        RogueData data = get(player);
        CardType type = CardType.byId(cardId);
        int slot = -1;
        for (int i = 0; i < data.offer.size(); i++) {
            if (data.offer.get(i).card().equals(cardId)) {
                slot = i;
            }
        }
        if (type != null && type != CardType.BANISH && slot >= 0 && data.pending > 0
                && banTokens(data) > 0 && !data.banned.contains(type.id())) {
            data.banned.add(type.id());
            RogueAdvancements.award(player, "banish");
            List<CardType> used = new ArrayList<>();
            for (int i = 0; i < data.offer.size(); i++) {
                if (i != slot && data.offer.get(i).type() != null) {
                    used.add(data.offer.get(i).type());
                }
            }
            double luck = player.getAttributeValue(Attributes.LUCK);
            Rarity rolled = Rarity.roll(player.getRandom(), luck, com.roguecards.api.RoguecraftApi.extraRarityLuck(player));
            CardOffer replacement = pickCard(data, rolled, used, player.getRandom());
            if (replacement != null) {
                data.offer.set(slot, replacement);
            } else {
                data.offer.remove(slot);
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        sync(player);
    }

    /** Põe no jogador um modificador por carta de atributo, mais o Pacto de Sangue. Pode rodar várias vezes. */
    public static void applyModifiers(ServerPlayer player) {
        RogueData data = get(player);
        for (CardType type : CardType.values()) {
            if (type.isAttributeCard()) {
                setModifier(player, type.attribute(), type.modifierId(), data.bonuses.getOrDefault(type.id(), 0.0), type.operation());
            }
        }
        setModifier(player, Attributes.ATTACK_DAMAGE, CardType.BLOOD_PACT.modifierId(),
                data.bonuses.getOrDefault(CardType.BLOOD_PACT.id(), 0.0), AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.MAX_HEALTH, BLOOD_PACT_HEALTH_ID,
                -data.bonuses.getOrDefault(CardType.BLOOD_PACT_HEALTH_KEY, 0.0), AttributeModifier.Operation.ADD_VALUE);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void setModifier(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                    ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (amount == 0) {
            instance.removeModifier(id);
        } else {
            instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, op));
        }
    }

    public static void sync(ServerPlayer player) {
        // Sem o mod do outro lado (ou jogador falso de teste), não há para quem mandar.
        // Jogador simulado (máquinas de outros mods, testes) também não tem para quem mandar.
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer
                || player.connection == null || !player.connection.hasChannel(SyncRoguePayload.TYPE.id())) {
            return;
        }
        RogueData data = get(player);
        PacketDistributor.sendToPlayer(player, new SyncRoguePayload(
                data.level, data.xp, data.level >= maxLevel() ? 0 : xpNeeded(data.level), data.pending,
                List.copyOf(data.offer), new HashMap<>(data.bonuses), new HashMap<>(data.picks),
                List.copyOf(data.banned), data.rerollsLeft, banTokens(data), List.copyOf(data.history),
                pityLeft(player, data)));
    }

    // ---------- Eventos do jogador ----------

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyModifiers(player);
            RogueAdvancements.checkAll(player);
            sync(player);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (Config.RESET_ON_DEATH.get() && !event.isEndConquered()) {
                // Modo hardcore: morreu, perde o nível azul e as cartas.
                get(player).reset();
                player.displayClientMessage(Component.translatable("message.roguecards.hardcore_reset")
                        .withStyle(ChatFormatting.RED), false);
            }
            applyModifiers(player);
            player.setHealth(player.getMaxHealth());
            sync(player);
        }
    }

    @SubscribeEvent
    static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    // ---------- Comandos de teste ----------

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("roguecards")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("xp")
                        .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    addXp(player, IntegerArgumentType.getInteger(ctx, "quantidade"));
                                    return 1;
                                })
                                .then(Commands.argument("jogador", EntityArgument.player())
                                        .executes(ctx -> {
                                            addXp(EntityArgument.getPlayer(ctx, "jogador"), IntegerArgumentType.getInteger(ctx, "quantidade"));
                                            return 1;
                                        }))))
                .then(Commands.literal("nivel")
                        .then(Commands.argument("niveis", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int levels = IntegerArgumentType.getInteger(ctx, "niveis");
                                    RogueData data = get(player);
                                    int total = 0;
                                    for (int i = 0; i < levels; i++) {
                                        total += xpNeeded(data.level + i);
                                    }
                                    addXp(player, total - data.xp);
                                    return 1;
                                })))
                .then(Commands.literal("oferta")
                        // Teste: troca a oferta atual por cartas da raridade pedida (se não houver carta nessa raridade, desce uma).
                        .then(Commands.argument("raridade", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(RARITY_WORDS, builder))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int index = RARITY_WORDS.indexOf(StringArgumentType.getString(ctx, "raridade"));
                                    if (index < 0) {
                                        ctx.getSource().sendFailure(Component.literal("Raridades: " + String.join(", ", RARITY_WORDS)));
                                        return 0;
                                    }
                                    forceOffer(player, Rarity.byIndex(index));
                                    return 1;
                                })))
                .then(Commands.literal("reset")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            get(player).reset();
                            applyModifiers(player);
                            sync(player);
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.roguecards.reset"), false);
                            return 1;
                        })));
    }

    /** Palavras do comando /roguecards oferta, na ordem das raridades. */
    private static final List<String> RARITY_WORDS = List.of("comum", "incomum", "rara", "epica", "lendaria");

    /**
     * Só para testes (/roguecards oferta lendaria): garante uma escolha pendente e sorteia a oferta
     * com todas as vagas na raridade pedida. Serve para ver a revelação de cada raridade.
     */
    public static void forceOffer(ServerPlayer player, Rarity rarity) {
        RogueData data = get(player);
        if (data.pending <= 0) {
            data.pending = 1;
        }
        data.offer.clear();
        List<CardType> used = new ArrayList<>();
        for (int i = 0; i < offerSize(player); i++) {
            CardOffer card = pickCard(data, rarity, used, player.getRandom());
            if (card != null) {
                used.add(card.type());
                data.offer.add(card);
            }
        }
        data.rerollsLeft = (int) Math.round(bonus(player, CardType.REROLL));
        sync(player);
    }

    // ---------- Utilidades ----------

    /** Arredonda com sorteio: 2,3 vira 3 com 30% de chance e 2 com 70%. Assim frações não se perdem. */
    static int randomRound(double value, RandomSource random) {
        int whole = (int) Math.floor(value);
        return whole + (random.nextDouble() < value - whole ? 1 : 0);
    }
}
