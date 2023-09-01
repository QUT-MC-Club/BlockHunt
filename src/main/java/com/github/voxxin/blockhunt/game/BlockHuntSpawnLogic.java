package com.github.voxxin.blockhunt.game;

import com.github.voxxin.blockhunt.BlockHunt;
import com.github.voxxin.blockhunt.game.map.BlockHuntMap;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.GameSpace;

public class BlockHuntSpawnLogic {
    private final GameSpace gameSpace;
    private final BlockHuntMap map;
    private final ServerWorld world;

    public BlockHuntSpawnLogic(GameSpace gameSpace, ServerWorld world, BlockHuntMap map) {
        this.gameSpace = gameSpace;
        this.map = map;
        this.world = world;
    }

    public void resetPlayer(ServerPlayerEntity player, GameMode gameMode) {
        player.changeGameMode(gameMode);
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0.0f;
    }

    public void spawnPlayer(ServerPlayerEntity player, BlockHuntPlayer participant) {

        var spawnPos = map.spawns().containsKey("spawn_everyone") ? (Vec3d) map.spawns().get("spawn_everyone") : (Vec3d) map.spawns().get("spawn_hider");

        if (participant == null) {
            player.teleport(this.world, spawnPos.x, spawnPos.y, spawnPos.z, 0.0F, 0.0F);
            return;
        }

        var spawnHides = (Vec3d) map.spawns().get("spawn_hider");
        var spawnSeekers = (Vec3d) map.spawns().get("spawn_seeker");

        Team team = participant.getTeam();

        switch (team.getName()) {
            case "seekers" -> {
                spawnPos = spawnSeekers;
            }
            case "hiders" -> {
                spawnPos = spawnHides;
            }
            default -> {
                BlockHunt.LOGGER.error("Cannot spawn player! Team is not defined!");
                return;
            }
        }

        float radius = 4.5f;
        int x = (int) (spawnPos.getX() + MathHelper.nextFloat(player.getRandom(), -radius, radius));
        int z = (int) (spawnPos.getZ() + MathHelper.nextFloat(player.getRandom(), -radius, radius));


        while (!this.world.getBlockState(new BlockPos((int) x, (int) spawnPos.y, (int) z)).isAir() && !this.world.getBlockState(new BlockPos((int) x, (int) spawnPos.y+1, (int) z)).isAir()) {
            x = (int) (spawnPos.getX() + MathHelper.nextFloat(player.getRandom(), -radius, radius));
            z = (int) (spawnPos.getZ() + MathHelper.nextFloat(player.getRandom(), -radius, radius));
        }

        float xPos = x + 0.5f;
        float zPos = z + 0.5f;

        float newRadius = 0.25f;
        xPos = xPos + MathHelper.nextFloat(player.getRandom(), -newRadius, newRadius);
        zPos = zPos + MathHelper.nextFloat(player.getRandom(), -newRadius, newRadius);

        player.teleport(this.world, xPos, spawnPos.y, zPos, 0.0F, 0.0F);
    }
}
