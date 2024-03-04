package com.github.voxxin.blockhunt.game.mixin;

import com.github.voxxin.blockhunt.BlockHunt;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonNetworkHandler.class)
public class ServerPlayerNetworkHandlerMixin {

    @Inject(at = @At("HEAD"), method = "send", cancellable = true)
    public void sendPacket(Packet<?> packet, PacketCallbacks callbacks, CallbackInfo ci) {
        //noinspection ConstantValue
        if (packet instanceof EntityEquipmentUpdateS2CPacket entityEquipmentUpdateS2CPacket && ((Object) this) instanceof ServerPlayNetworkHandler handler) {
            int packetID = entityEquipmentUpdateS2CPacket.getId();
            if (handler.player.getServerWorld().getPlayers().stream().noneMatch(p -> p.getId() == packetID)) { ci.cancel(); }

            if (BlockHunt.deniedIDs.contains(packetID)) { ci.cancel(); }
        }
    }
}
