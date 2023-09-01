package com.github.voxxin.blockhunt.game.util;

import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class BlockHuntTitle {
    public static void sendTitle(ServerPlayerEntity player, Text title, Text subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        if (fadeInTicks == 0) fadeOutTicks = 2 * 20;
        if (stayTicks == 0) stayTicks = 3 * 20;
        if (fadeOutTicks == 0) fadeOutTicks = 2 * 20;

        ServerPlayNetworkHandler networkHandler = player.networkHandler;
        networkHandler.sendPacket(new TitleFadeS2CPacket(fadeInTicks, stayTicks, fadeOutTicks));
        networkHandler.sendPacket(new TitleS2CPacket(title));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
    }
}
