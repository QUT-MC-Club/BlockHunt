package com.github.voxxin.blockhunt.game;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.player.PlayerSet;

import java.util.Set;

public class BlockHuntStageManager {
    private long closeTime = -1;
    public long finishTime = -1;
    public long startTime = -1;
    public long seekersRelease = -1;

    public boolean seekersReleased = false;
    private final Object2ObjectMap<ServerPlayerEntity, FrozenPlayer> frozen;
    private boolean setSpectator = false;

    public int[] gameTime = new int[2];

    public BlockHuntStageManager() {
        this.frozen = new Object2ObjectOpenHashMap<>();
    }

    public void onOpen(long time, BlockHuntConfig config, long seekerRelease) {
        this.startTime = timeCalc(time, 0, 3);
        this.seekersRelease = seekerRelease + this.startTime;
        this.finishTime = timeCalc(this.startTime, 20, 0);
    }

    public IdleTickResult tick(long time, GameSpace space) {
        PlayerSet players = space.getPlayers();

        // Game has finished. Wait a few seconds before finally closing the game.
        if (this.closeTime > 0) {
            if (time >= this.closeTime) {
                return IdleTickResult.GAME_CLOSED;
            }
            return IdleTickResult.TICK_FINISHED;
        }

        // Game hasn't started yet. Display a countdown before it begins.
        if (this.startTime > time && space.getPlayers().size() >= 2) {
            this.tickStartWaiting(time, space);
            return IdleTickResult.TICK_FINISHED;
        }

        // Game has started. Ticking tine before releasing seekers.
        if (this.seekersRelease > time) {
            seekersReleased = false;
        } else if (!seekersReleased) {
            players.showTitle(Text.literal("")
                            .append(Text.translatable("bossbar.blockhunt.seekers_release"))
                    , 50);
            seekersReleased = true;
        }

        // Game has just finished. Transition to the waiting-before-close state.
        if (time > this.finishTime || space.getPlayers().isEmpty()) {
            if (!this.setSpectator) {
                this.setSpectator = true;
                for (ServerPlayerEntity player : space.getPlayers()) {
                    player.changeGameMode(GameMode.SPECTATOR);
                }
            }

            this.closeTime = time + (5 * 20);

            return IdleTickResult.GAME_FINISHED;
        }

        return IdleTickResult.CONTINUE_TICK;
    }

    private void tickStartWaiting(long time, GameSpace space) {
        float sec_f = (this.startTime - time) / 20.0f;

        if (sec_f > 1) {
            for (ServerPlayerEntity player : space.getPlayers()) {
                if (player.isSpectator()) {
                    continue;
                }

                FrozenPlayer state = this.frozen.computeIfAbsent(player, p -> new FrozenPlayer());

                if (state.lastPos == null) {
                    state.lastPos = player.getPos();
                }

                double destX = state.lastPos.x;
                double destY = state.lastPos.y;
                double destZ = state.lastPos.z;

                // Set X and Y as relative so it will send 0 change when we pass yaw (yaw - yaw = 0) and pitch
                Set<PositionFlag> flags = ImmutableSet.of(PositionFlag.X_ROT, PositionFlag.Y_ROT);

                // Teleport without changing the pitch and yaw
                player.networkHandler.requestTeleport(destX, destY, destZ, player.getYaw(), player.getPitch(), flags);
            }
        }

        int sec = (int) Math.floor(sec_f) - 1;

        if ((this.startTime - time) % 20 == 0) {
            PlayerSet players = space.getPlayers();

            if (sec > 0) {
                players.showTitle(Text.of(Integer.toString(sec)), 20);
                players.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), SoundCategory.PLAYERS, 1.0F, 1.0F);
            } else {
                players.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BANJO.value(), SoundCategory.PLAYERS, 1.0F, 2.0F);
            }
        }
    }

    private long timeCalc(long initTime, int mins, int secs) {
        int tickAmount = 20;
        int seconds = secs * tickAmount;
        int minutes = mins * 60 * tickAmount;

        gameTime = new int[] {minutes, seconds};

        return initTime + seconds + minutes;
    }

    public static class FrozenPlayer {
        public Vec3d lastPos;
    }

    public enum IdleTickResult {
        CONTINUE_TICK,
        TICK_FINISHED,
        GAME_FINISHED,
        GAME_CLOSED,
    }
}
