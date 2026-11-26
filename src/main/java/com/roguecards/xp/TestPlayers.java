package com.roguecards.xp;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Jogador simulado para os testes automáticos.
 * Usa o FakePlayer do NeoForge, que ignora pacotes de rede (o Curios tenta mandar dados ao jogador
 * e quebrava com o jogador falso antigo). Ajustes para parecer um jogador de sobrevivência:
 * toma dano, não é criativo, nasce sem a imunidade de 3 segundos e dispara o evento de morte.
 */
final class TestPlayers {
    private TestPlayers() {
    }

    static ServerPlayer create(GameTestHelper h) {
        return create(h, false);
    }

    /**
     * Jogador de teste que guarda conquistas e estatísticas de verdade.
     * O FakePlayer do NeoForge ignora as duas coisas; aqui ele ganha um registro de conquistas próprio
     * (arquivo temporário que nunca é salvo) e soma as estatísticas normalmente.
     */
    static ServerPlayer createTracked(GameTestHelper h) {
        return create(h, true);
    }

    private static ServerPlayer create(GameTestHelper h, boolean tracked) {
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "roguecards-test")) {
            private PlayerAdvancements realAdvancements;

            @Override
            public PlayerAdvancements getAdvancements() {
                if (!tracked) {
                    return super.getAdvancements();
                }
                if (realAdvancements == null) {
                    Path file = Path.of(System.getProperty("java.io.tmpdir"), "roguecards-test-" + getUUID() + ".json");
                    realAdvancements = new PlayerAdvancements(this.server.getFixerUpper(), this.server.getPlayerList(),
                            this.server.getAdvancements(), file, this);
                }
                return realAdvancements;
            }

            @Override
            public void awardStat(Stat<?> stat, int amount) {
                if (tracked) {
                    getStats().increment(this, stat, amount);
                }
            }

            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public void die(DamageSource source) {
                // O FakePlayer não faz nada ao morrer; aqui só disparamos o evento de morte (Segunda Chance usa ele).
                CommonHooks.onLivingDeath(this, source);
            }
        };
        try {
            Field spawnProtection = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawnProtection.setAccessible(true);
            spawnProtection.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Não foi possível tirar a imunidade de nascimento do jogador de teste", e);
        }
        player.setPos(h.absoluteVec(new Vec3(2.5, 1, 2.5)));
        RogueData data = RogueXp.get(player);
        data.reset();
        RogueXp.applyModifiers(player);
        player.setHealth(player.getMaxHealth());
        return player;
    }
}
