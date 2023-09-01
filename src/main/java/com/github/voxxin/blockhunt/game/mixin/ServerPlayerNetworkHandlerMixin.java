package com.github.voxxin.blockhunt.game.mixin;

import com.github.voxxin.blockhunt.BlockHunt;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayerNetworkHandlerMixin {

    @Shadow @Final private MinecraftServer server;

    @Shadow public ServerPlayerEntity player;

    @Inject(at = @At("HEAD"), method = "sendPacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V", cancellable = true)
    public void sendPacket(Packet<?> packet, PacketCallbacks callbacks, CallbackInfo ci) {
        if (packet instanceof EntityEquipmentUpdateS2CPacket entityEquipmentUpdateS2CPacket) {
            int packetID = entityEquipmentUpdateS2CPacket.getId();
            if (this.player.getServerWorld().getPlayers().stream().noneMatch(p -> p.getId() == packetID)) { ci.cancel(); }

            if (BlockHunt.deniedIDs.contains(packetID)) { ci.cancel(); }
        }
    }
}
