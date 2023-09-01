package com.github.voxxin.blockhunt.game;

import com.github.voxxin.blockhunt.BlockHunt;
import com.github.voxxin.blockhunt.game.map.BlockHuntMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.game.*;
import xyz.nucleoid.plasmid.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.game.common.config.PlayerConfig;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.block.BlockUseEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BlockHuntWaiting {
    private final GameSpace gameSpace;
    private static BlockHuntMap thisMap;
    private final BlockHuntConfig config;
    private final BlockHuntSpawnLogic spawnLogic;
    private final ServerWorld world;
    private static List<Block> deniedBlockInteractions = new ArrayList<>();

    private BlockHuntWaiting(GameSpace gameSpace, ServerWorld world, BlockHuntMap map, BlockHuntConfig config) {
        this.gameSpace = gameSpace;
        this.thisMap = map;
        this.config = config;
        this.world = world;
        this.spawnLogic = new BlockHuntSpawnLogic(gameSpace, world, map);
    }

    public static GameOpenProcedure open(GameOpenContext<BlockHuntConfig> context) {
        PlayerConfig config = new PlayerConfig(2, 32);
        BlockHuntMap map;

        try {
            map = BlockHuntMap.from(context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        thisMap = map;

        return context.openWithWorld(map.worldConfig(), (game, world) -> {
            BlockHuntWaiting waiting = new BlockHuntWaiting(game.getGameSpace(), world, map, context.config());

            GameWaitingLobby.addTo(game, config);

            deniedBlockInteractions.clear();

            map.noInteractList().forEach((blockPos) -> {
                deniedBlockInteractions.add(world.getBlockState((BlockPos) blockPos).getBlock());
            });

            world.getServer().getBossBarManager().getAll().forEach((bossBar) -> {
                world.getServer().getBossBarManager().remove(bossBar);
            });

            // Game Rules
            game.setRule(GameRuleType.FALL_DAMAGE, ActionResult.FAIL);
            game.setRule(GameRuleType.PICKUP_ITEMS, ActionResult.FAIL);
            game.setRule(GameRuleType.CRAFTING, ActionResult.FAIL);
            game.setRule(GameRuleType.BREAK_BLOCKS, ActionResult.FAIL);
            game.setRule(GameRuleType.FIRE_TICK, ActionResult.FAIL);
            game.setRule(GameRuleType.FLUID_FLOW, ActionResult.FAIL);
            game.setRule(GameRuleType.HUNGER, ActionResult.FAIL);
            game.setRule(GameRuleType.MODIFY_ARMOR, ActionResult.FAIL);
            game.setRule(GameRuleType.PLACE_BLOCKS, ActionResult.FAIL);

            game.listen(GameActivityEvents.REQUEST_START, waiting::requestStart);
            game.listen(GamePlayerEvents.ADD, waiting::addPlayer);
            game.listen(GamePlayerEvents.OFFER, (offer) -> offer.accept(world, map.spawns().containsKey("spawn_everyone") ? map.spawns().get("spawn_everyone") : map.spawns().get("spawn_hider")));
            game.listen(PlayerDeathEvent.EVENT, waiting::onPlayerDeath);

            game.listen(BlockUseEvent.EVENT, waiting::allowInteraction);
        });
    }

    private ActionResult allowInteraction(ServerPlayerEntity serverPlayerEntity, Hand hand, BlockHitResult blockHitResult) {
        if (deniedBlockInteractions.isEmpty()) return ActionResult.SUCCESS;
        if (world == null) return ActionResult.SUCCESS;

        for (Block block : deniedBlockInteractions) {
            if (block == world.getBlockState(blockHitResult.getBlockPos()).getBlock()) {
                return ActionResult.FAIL;
            }
        }

        return ActionResult.SUCCESS;
    }

    private GameResult requestStart() {
        BlockHuntActive.open(this.gameSpace, this.world, thisMap, this.config);
        return GameResult.ok();
    }

    private void addPlayer(ServerPlayerEntity player) {
        player.setHealth(20.0f);
        this.spawnPlayer(player);
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        player.setHealth(20.0f);
        this.spawnPlayer(player);
        return ActionResult.FAIL;
    }

    private void spawnPlayer(ServerPlayerEntity player) {
        if (thisMap.spawns().entrySet().stream().noneMatch((entry) -> entry.getKey().equals("spawn_seeker")) || thisMap.spawns().entrySet().stream().noneMatch((entry) -> entry.getKey().equals("spawn_hider"))) {
            boolean noSeeker = thisMap.spawns().entrySet().stream().noneMatch((entry) -> entry.getKey().equals("spawn_seeker"));
            boolean noHider = thisMap.spawns().entrySet().stream().noneMatch((entry) -> entry.getKey().equals("spawn_hider"));
            if (noHider && noSeeker)
                BlockHunt.LOGGER.fatal("No hider or seeker spawn points were found for this map.");
            else if (noHider) BlockHunt.LOGGER.fatal("No hider spawn point was found for this map.");
            else if (noSeeker) BlockHunt.LOGGER.fatal("No seeker spawn point was found for this map.");

            this.gameSpace.close(GameCloseReason.ERRORED);
            return;
        }

        if (thisMap.spawns().entrySet().stream().noneMatch((entry) -> entry.getKey().equals("spawn_everyone"))) {
            BlockHunt.LOGGER.info("No default spawn point was found for this map.");
        }

        this.spawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        this.spawnLogic.spawnPlayer(player, null);
    }
}