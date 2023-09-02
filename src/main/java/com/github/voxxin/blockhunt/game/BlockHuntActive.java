package com.github.voxxin.blockhunt.game;

import com.github.voxxin.blockhunt.BlockHunt;
import com.github.voxxin.blockhunt.game.map.BlockHuntMap;
import com.github.voxxin.blockhunt.game.util.BlockHuntAnimation;
import com.github.voxxin.blockhunt.game.util.BlockHuntBlock;
import com.github.voxxin.blockhunt.game.util.BlockHuntBossBar;
import com.github.voxxin.blockhunt.game.util.ext.EntityEquipmentUpdateS2CPacketExt;
import com.github.voxxin.blockhunt.game.util.ext.WorldExt;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.event.GameEvents;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.util.PlayerRef;
import xyz.nucleoid.stimuli.event.block.BlockBreakEvent;
import xyz.nucleoid.stimuli.event.block.BlockPunchEvent;
import xyz.nucleoid.stimuli.event.block.BlockUseEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;
import xyz.nucleoid.stimuli.event.player.PlayerInventoryActionEvent;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.github.voxxin.blockhunt.BlockHunt.deniedIDs;

public class BlockHuntActive {
    private final BlockHuntConfig config;

    public final GameSpace gameSpace;
    private final BlockHuntMap gameMap;
    private final Object2ObjectMap<PlayerRef, BlockHuntPlayer> participants;
    private final BlockHuntSpawnLogic spawnLogic;
    private final BlockHuntStageManager stageManager;
    private final boolean ignoreWinState;
    private final GlobalWidgets widgets;
    private BlockHuntSidebar sidebar;
    private final ServerWorld world;

    private static Team seekersTeam = null;
    private static Team hidersTeam = null;
    private static Team spectatorTeam = null;

    private int realSeconds = 0;

    private int lastHiddenReset = 20;

    private ArrayList<BlockHuntBossBar> bossBars = new ArrayList<>();

    private static final ArrayList<Block> deniedBlockInteractions = new ArrayList<>();

    private static final ArrayList<Block> allowedMapDisguises = new ArrayList<>();
    private BlockHuntActive(GameSpace gameSpace, ServerWorld world, BlockHuntMap map, GlobalWidgets widgets, BlockHuntConfig config, Set<PlayerRef> participants) {
        this.gameSpace = gameSpace;
        this.config = config;
        this.gameMap = map;
        this.spawnLogic = new BlockHuntSpawnLogic(gameSpace, world, map);
        this.participants = new Object2ObjectOpenHashMap<>();
        this.world = world;

        for (PlayerRef player : participants) {
            this.participants.put(player, new BlockHuntPlayer(world, player));
        }

        this.stageManager = new BlockHuntStageManager();
        this.ignoreWinState = this.participants.size() <= 1;
        this.widgets = widgets;
    }

    public static void open(GameSpace gameSpace, ServerWorld world, BlockHuntMap map, BlockHuntConfig config) {
        gameSpace.setActivity(game -> {
            Set<PlayerRef> participants = gameSpace.getPlayers().stream()
                    .map(PlayerRef::of)
                    .collect(Collectors.toSet());
            GlobalWidgets widgets = GlobalWidgets.addTo(game);
            BlockHuntActive active = new BlockHuntActive(gameSpace, world, map, widgets, config, participants);

            allowedMapDisguises.clear();
            deniedBlockInteractions.clear();

            map.noInteractList().forEach((blockPos) -> {
                deniedBlockInteractions.add(world.getBlockState((BlockPos) blockPos).getBlock());
            });

            map.allowedDisguises().forEach((blockPos) -> {
                allowedMapDisguises.add(world.getBlockState((BlockPos) blockPos).getBlock());
            });

            if (allowedMapDisguises.size() == 0) {
                BlockHunt.LOGGER.fatal("No allowed disguises found! Please setup a 'map_disguises' region for your map.");
                gameSpace.close(GameCloseReason.ERRORED);
            }

            // Create teams

            if (world.getScoreboard().getTeam("seekers") != null) world.getScoreboard().removeTeam(world.getScoreboard().getTeam("seekers"));
            if (world.getScoreboard().getTeam("hiders") != null) world.getScoreboard().removeTeam(world.getScoreboard().getTeam("hiders"));
            if (world.getScoreboard().getTeam("specs") != null) world.getScoreboard().removeTeam(world.getScoreboard().getTeam("specs"));

            seekersTeam = world.getScoreboard().addTeam("seekers");
            hidersTeam = world.getScoreboard().addTeam("hiders");
            spectatorTeam = world.getScoreboard().addTeam("specs");

            world.getScoreboard().getTeam(seekersTeam.getName()).setCollisionRule(AbstractTeam.CollisionRule.NEVER);

            world.getScoreboard().getTeam(hidersTeam.getName()).setCollisionRule(AbstractTeam.CollisionRule.NEVER);
            world.getScoreboard().getTeam(hidersTeam.getName()).setShowFriendlyInvisibles(false);
            world.getScoreboard().getTeam(hidersTeam.getName()).setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);

            // Game Rules
            game.setRule(GameRuleType.FALL_DAMAGE, ActionResult.FAIL);
            game.setRule(GameRuleType.PICKUP_ITEMS, ActionResult.FAIL);
            game.setRule(GameRuleType.THROW_ITEMS, ActionResult.FAIL);
            game.setRule(GameRuleType.CRAFTING, ActionResult.FAIL);
            game.setRule(GameRuleType.FIRE_TICK, ActionResult.FAIL);
            game.setRule(GameRuleType.FLUID_FLOW, ActionResult.FAIL);
            game.setRule(GameRuleType.HUNGER, ActionResult.FAIL);
            game.setRule(GameRuleType.MODIFY_ARMOR, ActionResult.FAIL);
            //game.setRule(GameRuleType.PLACE_BLOCKS, ActionResult.FAIL);


            // Unique Gamerules

            game.listen(GameActivityEvents.ENABLE, active::onOpen);

            game.listen(GamePlayerEvents.OFFER, (offer) -> offer.accept(world, Vec3d.ZERO));
            game.listen(GamePlayerEvents.ADD, active::addPlayer);
            game.listen(GamePlayerEvents.REMOVE, active::removePlayer);

            game.listen(GameActivityEvents.TICK, active::tick);

            game.listen(PlayerDamageEvent.EVENT, active::onPlayerDamage);
            game.listen(PlayerDeathEvent.EVENT, active::onPlayerDeath);

            game.listen(BlockUseEvent.EVENT, active::allowInteraction);
            game.listen(BlockPunchEvent.EVENT, active::blockAttack);
        });
    }

    private void onOpen() {
        //Sets default start time if present
        if (this.gameMap.animations().stream().anyMatch(animation -> animation.animationName.getPath().equals("seeker"))) {
            BlockHuntAnimation seekerAnimation = this.gameMap.animations().stream().filter(animation -> animation.animationName.getPath().equals("seeker")).findFirst().orElseGet(null);
            seekerAnimation.settings.setWorld(world);
            seekerAnimation.settings.tick();
            if (seekerAnimation.settings.startTime == 0) {
                BlockHunt.LOGGER.info("No start time set for seeker released animation. Defaulting to 1 minute.");
                int mins = 1;
                this.stageManager.onOpen(this.world.getTime(), this.config, (mins * 60) * 20);
            } else this.stageManager.onOpen(this.world.getTime(), this.config, (long) seekerAnimation.settings.startTime);
        } else {
            BlockHunt.LOGGER.fatal("No seeker released animation found. Please add one to your map.");
            gameSpace.close(GameCloseReason.ERRORED);
        }
        BlockHunt.deniedIDs.clear();

        PlayerSet players = this.gameSpace.getPlayers();

        ServerPlayerEntity firstSeeker = players.stream().findAny().orElse(null);
        assert firstSeeker != null;
        this.participants.get(PlayerRef.of(firstSeeker)).setTeam(seekersTeam);
        players.stream().iterator().forEachRemaining((ServerPlayerEntity player) -> {
            if (player != firstSeeker) {
                BlockHuntPlayer blockHuntPlayer = this.participants.get(PlayerRef.of(player));
                blockHuntPlayer.setTeam(hidersTeam);

                Block block = allowedMapDisguises.get(new Random().nextInt(allowedMapDisguises.size()));
                BlockHuntBlock blockEntity = getEntityFromBlock(block);
                world.spawnEntity(blockEntity);
                blockHuntPlayer.setDisguise(blockEntity, block);
                blockHuntPlayer.updateTimeBar(null, widgets);
                deniedIDs.add(player.getId());
            }
        });

        for (PlayerRef ref : this.participants.keySet()) {
            ref.ifOnline(this.world, this::spawnParticipant);
        }

        this.sidebar = new BlockHuntSidebar(widgets, config.mapConfig().id(), this.world, this.stageManager);
    }

    private void onClose() {
    }

    private ActionResult allowInteraction(ServerPlayerEntity serverPlayerEntity, Hand hand, BlockHitResult blockHitResult) {
        if (world == null) return ActionResult.CONSUME;

        Block clickedBlock = world.getBlockState(blockHitResult.getBlockPos()).getBlock();
        BlockHuntPlayer player = this.participants.get(PlayerRef.of(serverPlayerEntity));

        boolean isSameBlock = player.prevBlockhitResult != null && player.prevBlockhitResult.equals(blockHitResult.getBlockPos());
        player.prevBlockhitResult = blockHitResult.getBlockPos();

        for (Block block : allowedMapDisguises) {

            if (block == clickedBlock && serverPlayerEntity.isTeamPlayer(hidersTeam) && player.getDisguiseB() == clickedBlock && !isSameBlock) {
                serverPlayerEntity.sendMessage(
                        Text.translatable("event.blockhunt.already_block", clickedBlock.getName().formatted(Formatting.AQUA))
                                .formatted(Formatting.GREEN),
                        false);

                return ActionResult.SUCCESS;
            } else if (block == clickedBlock && serverPlayerEntity.isTeamPlayer(hidersTeam) && player.getDisguiseB() != clickedBlock && !isSameBlock) {
                player.setDisguise(clickedBlock);
                player.updateTimeBar(true, null);

                serverPlayerEntity.sendMessage(
                        Text.translatable("event.blockhunt.block_changed", clickedBlock.getName().formatted(Formatting.AQUA))
                                .formatted(Formatting.GREEN),
                        false);

                return ActionResult.SUCCESS;
            }
        }

        for (Block block : deniedBlockInteractions) {

            if (block == clickedBlock) {
                return ActionResult.FAIL;
            }
        }

        return ActionResult.PASS;
    }

    private ActionResult blockAttack(ServerPlayerEntity serverPlayerEntity, Direction direction, BlockPos blockPos) {
        BlockPos blockPosHit = ((BlockHitResult) serverPlayerEntity.raycast(5, 0, false)).getBlockPos();
        BlockHuntPlayer player = this.participants.get(PlayerRef.of(serverPlayerEntity));

        participants.keySet().stream().iterator().forEachRemaining((ref) -> {
            ServerPlayerEntity serverPlayer = ref.getEntity(world);
            BlockHuntPlayer blockHuntPlayer = participants.get(ref);

            if (blockHuntPlayer.getPositionHidden() != null && blockHuntPlayer.getPositionHidden().equals(blockPosHit) && player.getTeam() == seekersTeam) {
                serverPlayerEntity.attack(serverPlayer);
            }
        });

        return ActionResult.FAIL;
    }

    private void addPlayer(ServerPlayerEntity player) {
        if (!this.participants.containsKey(PlayerRef.of(player))) {
            this.spawnSpectator(player);
        }
    }

    private void removePlayer(ServerPlayerEntity player) {
        if (player.interactionManager.getGameMode() == GameMode.SPECTATOR || player.interactionManager.getGameMode() == GameMode.SURVIVAL) player.changeGameMode(GameMode.ADVENTURE);
        BlockHuntPlayer blockHuntPlayer = this.participants.get(PlayerRef.of(player));
        blockHuntPlayer.resetDisguise();
        blockHuntPlayer.removeTimeBar();
        blockHuntPlayer.setTeam(spectatorTeam);
        blockHuntPlayer.setHidden(false);
        blockHuntPlayer.lastPosition = null;
        if (blockHuntPlayer.getTimeBar() != null) this.world.getServer().getBossBarManager().remove(blockHuntPlayer.getTimeBar());
        blockHuntPlayer.removeTimeBar();
        this.participants.remove(PlayerRef.of(player));
        if (deniedIDs.contains(player.getId())) {
            deniedIDs.remove((Integer) player.getId());
        }
    }

    private ActionResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (source.getSource() == null) return ActionResult.FAIL;

        if (source.getSource().isPlayer()) {
            if (!player.isTeammate(source.getAttacker())) {
                BlockHuntPlayer thisPlayer = this.participants.get(PlayerRef.of(player));
                if (thisPlayer.isHidden()) {
                    ((WorldExt) this.world).blockHunt$setBlockState(thisPlayer.getPositionHidden(), Blocks.AIR.getDefaultState());
                    thisPlayer.updateTimeBar(true, null);
                    thisPlayer.setHidden(false);
                }
                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.FAIL;
    }

    private ActionResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        if (source.getSource().isPlayer()) {
            BlockHuntPlayer thisPlayer = this.participants.get(PlayerRef.of(player));
            BlockHuntPlayer thisAttacker = this.participants.get(PlayerRef.of((ServerPlayerEntity) source.getAttacker()));

            thisPlayer.playerDeath();
            player.changeGameMode(GameMode.SPECTATOR);

            if (player.isTeamPlayer(hidersTeam)) {
                this.broadcastMessage(
                        Text.literal("")
                                .append(
                                        Text.literal(" ! "
                                        ).formatted(Formatting.RED, Formatting.BOLD)
                                )
                                .append(
                                        Text.translatable("event.blockhunt.hider_found",
                                                        source.getAttacker().getName().copy().formatted(Formatting.RED),
                                                        player.getName().copy().formatted(Formatting.YELLOW))
                                                .formatted(Formatting.WHITE)
                                )
                );


                this.participants.get(PlayerRef.of(player)).setTeam(seekersTeam);
                this.participants.get(PlayerRef.of(player)).removeTimeBar();
                this.participants.get(PlayerRef.of(player)).resetDisguise();
                this.participants.get(PlayerRef.of(player)).setHidden(false);
                this.participants.get(PlayerRef.of(player)).playerDeath();
                deniedIDs.remove(player.getId());
                ((ServerPlayerEntity) source.getAttacker()).setHealth(20);
                return ActionResult.FAIL;
            } else if (player.isTeamPlayer(seekersTeam)) {
                this.broadcastMessage(
                        Text.literal("")
                                .append(
                                        Text.literal(" ! "
                                        ).formatted(Formatting.RED, Formatting.BOLD)
                                )
                                .append(
                                        Text.translatable("event.blockhunt.seeker_killed",
                                                player.getName().copy().formatted(Formatting.YELLOW),
                                                source.getAttacker().getName().copy().formatted(Formatting.RED)
                                        ).formatted(Formatting.WHITE)
                                )
                );
            }
        }
        return ActionResult.FAIL;
    }

    private void spawnParticipant(ServerPlayerEntity player) {
        BlockHuntPlayer participant = this.participants.get(PlayerRef.of(player));

        if (participant.getTeam() == null) {
            participant.setTeam(hidersTeam);
        }
        this.spawnLogic.resetPlayer(player, GameMode.SURVIVAL);
        participant.loadPlayerInventory();
        this.spawnLogic.spawnPlayer(player, participant);
    }



    private void spawnSpectator(ServerPlayerEntity player) {
        this.spawnLogic.resetPlayer(player, GameMode.SPECTATOR);
        this.participants.get(PlayerRef.of(player)).setTeam(spectatorTeam);
        this.participants.get(PlayerRef.of(player)).removeTimeBar();
        this.participants.get(PlayerRef.of(player)).resetDisguise();
        this.participants.get(PlayerRef.of(player)).setHidden(false);
        this.spawnLogic.spawnPlayer(player, this.participants.get(PlayerRef.of(player)));
    }

    private void tick() {
        long time = this.world.getTime();
        BlockHuntStageManager.IdleTickResult result = this.stageManager.tick(time, gameSpace);

        switch (result) {
            case CONTINUE_TICK:
                break;
            case TICK_FINISHED:
                return;
            case GAME_FINISHED:
                this.broadcastWin(this.checkWinResult());
                return;
            case GAME_CLOSED:
                this.gameSpace.close(GameCloseReason.FINISHED);
                return;
        }

        if (gameSpace.getPlayers().size() == 1) this.gameSpace.close(GameCloseReason.CANCELED);

        this.participants.keySet().forEach(ref -> {
            if (!ref.isOnline(this.world)) {
                BlockHuntPlayer blockHuntPlayer = this.participants.get(ref);
                blockHuntPlayer.resetDisguise();
                blockHuntPlayer.removeTimeBar();
                blockHuntPlayer.setTeam(spectatorTeam);
                blockHuntPlayer.setHidden(false);
                blockHuntPlayer.lastPosition = null;
                this.participants.remove(ref);
            }
        });

        for (BlockHuntAnimation animation : this.gameMap.animations()) {
            animation.setWorld(this.world);
            animation.tick(this.stageManager.startTime, time);
        }

        this.participants.keySet().forEach(ref -> ref.ifOnline(this.world, player -> {
            BlockHuntPlayer blockHuntPlayer = this.participants.get(ref);
            PlayerSet players = this.gameSpace.getPlayers();

            if (blockHuntPlayer.getDisguiseE() != null && !blockHuntPlayer.isHidden()) {
                blockHuntPlayer.getDisguiseE().setPosition(player.getPos().subtract(0.5, 0, 0.5));
            } else if (blockHuntPlayer.getDisguiseE() != null && blockHuntPlayer.isHidden()) blockHuntPlayer.getDisguiseE().setPosition(new Vec3d(0, 0,0));

            if (blockHuntPlayer.lastPosition != null && blockHuntPlayer.getTeam() == hidersTeam) {
                Vec3i roundedPos = new Vec3i((int) Math.floor(player.getPos().x), (int) Math.floor(player.getPos().y), (int) Math.floor(player.getPos().z));
                Vec3i roundedOldPos = new Vec3i((int) Math.floor(blockHuntPlayer.lastPosition.x), (int) Math.floor(blockHuntPlayer.lastPosition.y), (int) Math.floor(blockHuntPlayer.lastPosition.z));
                if ((!roundedPos.equals(roundedOldPos) && blockHuntPlayer.getTimeUntilHidden() != 1F) || (!world.getBlockState(BlockPos.ofFloored(blockHuntPlayer.lastPosition)).getBlock().equals(Blocks.AIR) && blockHuntPlayer.getPositionHidden() != player.getBlockPos())) {
                    if (blockHuntPlayer.isHidden()) {
                        ((WorldExt)world).blockHunt$setBlockState(blockHuntPlayer.getPositionHidden(), Blocks.AIR.getDefaultState());
                    }
                    blockHuntPlayer.updateTimeBar(true, null);
                    blockHuntPlayer.setHidden(false);
                    realSeconds = 0;
                }
            }

            if (blockHuntPlayer.getTeam() == hidersTeam && blockHuntPlayer.lastPosition != null && realSeconds == 20 && !blockHuntPlayer.isHidden()) {
                Vec3i roundedPos = new Vec3i((int) Math.floor(player.getPos().x), (int) Math.floor(player.getPos().y), (int) Math.floor(player.getPos().z));
                Vec3i roundedOldPos = new Vec3i((int) Math.floor(blockHuntPlayer.lastPosition.x), (int) Math.floor(blockHuntPlayer.lastPosition.y), (int) Math.floor(blockHuntPlayer.lastPosition.z));
                blockHuntPlayer.updateTimeBar(!roundedPos.equals(roundedOldPos), null);
            }
            if (blockHuntPlayer.getTimeUntilHidden() <= 0 && blockHuntPlayer.getTeam() == hidersTeam && !blockHuntPlayer.isHidden()) {

                ((WorldExt)world).blockHunt$setBlockState(player.getBlockPos(), blockHuntPlayer.getDisguiseB().getDefaultState());
                blockHuntPlayer.setHidden(true);
            }

            if (blockHuntPlayer.respawnTicks == 1) spawnParticipant(player);
            if (blockHuntPlayer.respawnTicks != 0) blockHuntPlayer.respawnTicks--;

            blockHuntPlayer.lastPosition = player.getPos();
        }));

        if (this.world.getScoreboard().getTeam(hidersTeam.getName()).getPlayerList().size() == 0) this.broadcastWin(this.checkWinResult());
        this.sidebar.tick();

        if (realSeconds == 20) {
            realSeconds = 0;
        } else realSeconds++;
    }

    private BlockHuntBlock getEntityFromBlock(Block block) {
        BlockHuntBlock blockEntity = new BlockHuntBlock(EntityType.BLOCK_DISPLAY, this.world);
        blockEntity.setBlockState(block.getDefaultState());
        return blockEntity;
    }

    private void broadcastMessage(String message) {
        this.gameSpace.getPlayers().sendMessage(Text.of(message));
    }

    private void broadcastMessage(Text message) {
        this.gameSpace.getPlayers().sendMessage(message);
    }

    private void broadcastWin(WinResult result) {
        Team winningTeam = result.getWinningTeam();

        AtomicInteger seekers = new AtomicInteger();

        participants.values().stream().iterator().forEachRemaining(player -> {
            if (player.getTeam() != seekersTeam) {
                seekers.getAndIncrement();
            }
        });

        PlayerSet players = this.gameSpace.getPlayers();
        players.sendMessage(Text.literal("").append(
                                Text.literal(" ! ").formatted(Formatting.RED, Formatting.BOLD)
                        ).append(
                                Text.translatable("event.blockhunt.win", winningTeam.getDisplayName().copy().formatted(Formatting.YELLOW))
                                        .formatted(Formatting.WHITE)
                        ).formatted(Formatting.WHITE)

                        .append("\n")
                        .append(Text.literal("  - ").formatted(Formatting.RED, Formatting.BOLD))

                        .append(
                                winningTeam == seekersTeam ? Text.translatable("event.blockhunt.win_hider",

                                        Text.literal(
                                                String.valueOf(seekers.get()).formatted(Formatting.YELLOW)
                                        ))

                                        : Text.translatable("event.blockhunt.win_seeker",

                                        Text.literal(String.valueOf(this.participants.size() - 1))
                                                .formatted(Formatting.YELLOW)
                                )
                        )


        );
        players.playSound(SoundEvents.ENTITY_VILLAGER_YES);
        gameSpace.close(GameCloseReason.FINISHED);
    }

    private WinResult checkWinResult() {
        if (this.ignoreWinState) {
            return WinResult.no();
        }

        if (this.world.getScoreboard().getTeam(hidersTeam.getName()).getPlayerList().size() == 0) {
            return WinResult.win(hidersTeam);
        } else {
            return WinResult.win(seekersTeam);
        }
    }

    static class WinResult {
        final Team winningTeam;
        final boolean win;

        private WinResult(Team winningTeam, boolean win) {
            this.winningTeam = winningTeam;
            this.win = win;
        }

        static WinResult no() {
            return new WinResult(null, false);
        }

        static WinResult win(Team winningTeam) {
            return new WinResult(winningTeam, true);
        }

        public boolean isWin() {
            return this.win;
        }

        public Team getWinningTeam() {
            return this.winningTeam;
        }
    }
}