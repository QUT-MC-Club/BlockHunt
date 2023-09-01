package com.github.voxxin.blockhunt.game.util.ext;

import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.world.World;

public interface EntityEquipmentUpdateS2CPacketExt {
    void blockHunt$apply(ClientPlayPacketListener clientPlayPacketListener, World world);
}
