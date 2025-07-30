package com.wanquan5201.potionmodserver;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PotionModServer implements DedicatedServerModInitializer {
    public static final String MOD_ID = "potion-mod-server";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // 网络包定义（与客户端模组完全一致）
    public record ApplyEffectPayload(Identifier effectId, int duration, int amplifier) implements CustomPayload {
        public static final Id<ApplyEffectPayload> ID = new Id<>(Identifier.of("potion-mod", "apply_effect"));
        public static final PacketCodec<RegistryByteBuf, ApplyEffectPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeIdentifier(payload.effectId);
                    buf.writeInt(payload.duration);
                    buf.writeInt(payload.amplifier);
                },
                buf -> new ApplyEffectPayload(buf.readIdentifier(), buf.readInt(), buf.readInt())
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ClearEffectsPayload() implements CustomPayload {
        public static final Id<ClearEffectsPayload> ID = new Id<>(Identifier.of("potion-mod", "clear_effects"));
        public static final PacketCodec<RegistryByteBuf, ClearEffectsPayload> CODEC = PacketCodec.unit(new ClearEffectsPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ClearSingleEffectPayload(Identifier effectId) implements CustomPayload {
        public static final Id<ClearSingleEffectPayload> ID = new Id<>(Identifier.of("potion-mod", "clear_single_effect"));
        public static final PacketCodec<RegistryByteBuf, ClearSingleEffectPayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeIdentifier(payload.effectId),
                buf -> new ClearSingleEffectPayload(buf.readIdentifier())
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    @Override
    public void onInitializeServer() {
        LOGGER.info("Potion Mod Server initialized!");

        // 注册网络包类型
        PayloadTypeRegistry.playC2S().register(ApplyEffectPayload.ID, ApplyEffectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClearEffectsPayload.ID, ClearEffectsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClearSingleEffectPayload.ID, ClearSingleEffectPayload.CODEC);

        // 注册网络包处理器
        registerNetworkHandlers();
    }

    private void registerNetworkHandlers() {
        // 清除所有效果
        ServerPlayNetworking.registerGlobalReceiver(ClearEffectsPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    if (player.isCreative()) {
                        player.clearStatusEffects();
                        player.sendMessage(
                                Text.translatable("message.potion_mod.all_effects_cleared"),
                                false
                        );
                    } else {
                        sendCreativeOnlyError(player);
                    }
                });

        // 应用效果
        ServerPlayNetworking.registerGlobalReceiver(ApplyEffectPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    if (player.isCreative()) {
                        Registries.STATUS_EFFECT.getEntry(payload.effectId).ifPresent(entry -> {
                            // 使用 RegistryEntry 而不是 StatusEffect
                            StatusEffectInstance effectInstance = new StatusEffectInstance(
                                    entry,  // 这里是关键修复
                                    payload.duration,
                                    payload.amplifier,
                                    false,
                                    false,
                                    true
                            );
                            player.addStatusEffect(effectInstance);

                            // 获取效果名称用于消息
                            StatusEffect effect = entry.value();
                            player.sendMessage(
                                    Text.translatable("message.potion_mod.effect_applied",
                                            effect.getName().getString(),
                                            payload.amplifier + 1,
                                            payload.duration / 20
                                    ),
                                    false
                            );
                        });
                    } else {
                        sendCreativeOnlyError(player);
                    }
                });

        // 清除单个效果
        ServerPlayNetworking.registerGlobalReceiver(ClearSingleEffectPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    if (player.isCreative()) {
                        Registries.STATUS_EFFECT.getEntry(payload.effectId).ifPresent(entry -> {
                            // 使用 RegistryEntry 而不是 StatusEffect
                            player.removeStatusEffect(entry);  // 这里是关键修复

                            // 获取效果名称用于消息
                            StatusEffect effect = entry.value();
                            player.sendMessage(
                                    Text.translatable("message.potion_mod.effect_cleared",
                                            effect.getName().getString()
                                    ),
                                    false
                            );
                        });
                    } else {
                        sendCreativeOnlyError(player);
                    }
                });
    }

    private void sendCreativeOnlyError(ServerPlayerEntity player) {
        player.sendMessage(
                Text.translatable("message.potion_mod.creative_only"),
                false
        );
    }
}