package com.juejuecat.taste_fatigue.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import static com.juejuecat.taste_fatigue.TasteFatigue.MODID;

public class TFNetwork {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MODID, "sync_use_duration");

    public record SyncUseDurationMsg(int duration) implements CustomPacketPayload {
        public static final Type<SyncUseDurationMsg> TYPE = new Type<>(ID);

        public static final StreamCodec<FriendlyByteBuf, SyncUseDurationMsg> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.INT,
                        SyncUseDurationMsg::duration,
                        SyncUseDurationMsg::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public static void encode(SyncUseDurationMsg msg, FriendlyByteBuf buf) {
            STREAM_CODEC.encode(buf, msg);
        }

        public static SyncUseDurationMsg decode(FriendlyByteBuf buf) {
            return STREAM_CODEC.decode(buf);
        }

        public static void handle(SyncUseDurationMsg msg, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                var player = ctx.player();
                if (player.isUsingItem()) {
                    ((LivingEntity) player).useItemRemaining = msg.duration;
                }
            });
        }
    }

    public static void sendToPlayer(ServerPlayer player, int duration) {
        PacketDistributor.sendToPlayer(player, new SyncUseDurationMsg(duration));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MODID).versioned("1");
        registrar.playToClient(
                SyncUseDurationMsg.TYPE,
                SyncUseDurationMsg.STREAM_CODEC,
                SyncUseDurationMsg::handle
        );
    }
}
