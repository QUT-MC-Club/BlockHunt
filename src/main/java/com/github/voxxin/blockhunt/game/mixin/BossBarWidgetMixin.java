package com.github.voxxin.blockhunt.game.mixin;

import com.github.voxxin.blockhunt.game.util.ext.BossBarWidgetExt;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.nucleoid.plasmid.game.common.widget.BossBarWidget;

@Mixin(ServerBossBar.class)
public class BossBarWidgetMixin implements BossBarWidgetExt {

    @Override
    public void blockHunt$addSinglePlayer(ServerPlayerEntity player) {
        ServerBossBar bar = (ServerBossBar) (Object) this;
        bar.clearPlayers();
        bar.addPlayer(player);
    }
}
