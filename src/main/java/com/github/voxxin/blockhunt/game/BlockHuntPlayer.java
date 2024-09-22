package com.github.voxxin.blockhunt.game;

import com.github.voxxin.blockhunt.game.util.BlockHuntBlock;
import com.github.voxxin.blockhunt.game.util.BlockHuntBossBar;
import com.github.voxxin.blockhunt.game.util.BlockHuntTitle;
import net.minecraft.block.Block;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.util.PlayerRef;

public class BlockHuntPlayer {
    private final ServerWorld world;
    private final PlayerRef playerRef;
    private final ServerPlayerEntity player;
    private BlockHuntBossBar.HideTimeBossbar bossBar;
    private Team team = null;
    private Object[] disguise = new Object[2];
    private boolean isHidden;
    public Vec3d lastPosition;
    public int respawnTicks = 0;
    private BlockPos positionHidden = null;
    public BlockPos prevBlockhitResult;

    public int lastRealSecond = 0;

    public BlockHuntPlayer(ServerWorld world, PlayerRef player) {
        this.world = world;
        this.playerRef = player;
        this.player = player.getEntity(world);
    }

    public void setTeam(Team team) {
        world.getScoreboard().addScoreHolderToTeam(player.getNameForScoreboard(), team);
        this.team = team;
    }

    public Team getTeam() {
        return this.team;
    }

    public void setDisguise(BlockHuntBlock disguise) {
        if (disguise == null) return;

        this.disguise = new Object[]{disguise, disguise.getBlockState().getBlock()};
    }

    public void setDisguise(Block block) {
        if (this.disguise == null) return;
        BlockHuntBlock disguiseEntity = (BlockHuntBlock) this.disguise[0];
        disguiseEntity.setBlockState(block.getDefaultState());

        this.disguise = new Object[]{disguiseEntity, block};
    }

    public void setDisguise(BlockHuntBlock disguise, Block block) {
        this.disguise = new Object[]{disguise, block};
    }

    public void resetDisguise() {
        if (this.disguise[0] != null) ((BlockHuntBlock) this.disguise[0]).kill();
        this.disguise = new Object[]{null, null};
    }

    public BlockHuntBlock getDisguiseE() {
        return (BlockHuntBlock) this.disguise[0];
    }

    public Block getDisguiseB() {
        return (Block) this.disguise[1];
    }

    public void setHidden(boolean isHidden) {
        if (isHidden) positionHidden = player.getBlockPos();
        else positionHidden = null;

        this.isHidden = isHidden;
    }

    public boolean isHidden() {
        return this.isHidden;
    }

    public BlockPos getPositionHidden() {
        return this.positionHidden;
    }

    public void updateTimeBar(@Nullable Boolean moved) {
        if (this.bossBar == null) {
            this.bossBar = new BlockHuntBossBar.HideTimeBossbar();
            this.bossBar.addPlayer(this.player);
        } else {
            if (moved != null) this.bossBar.update(moved);
        }
    }

    public float getTimeUntilHidden() {
        if (this.bossBar == null) return 0;
        return this.bossBar.getTimeUntilHidden();
    }

    public void removeTimeBar() {
        if (bossBar != null) this.bossBar.remove();
    }
    public BlockHuntBossBar.HideTimeBossbar getTimeBar() {
        return this.bossBar;
    }


    public void loadPlayerInventory() {
        if (team == null || team.getName().equals("specs")) return;

        switch (team.getName()) {
            case "seekers" -> {
                this.player.clearStatusEffects();
                this.player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, StatusEffectInstance.INFINITE, 0, false, false));
                this.player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, StatusEffectInstance.INFINITE, 3, false, false));

                this.player.getInventory().clear();
                this.player.getInventory().armor.set(0, new ItemStack(Items.CHAINMAIL_BOOTS).setCustomName(Text.of("§f§lHeavy Boots")));
                this.player.getInventory().armor.set(1, new ItemStack(Items.CHAINMAIL_LEGGINGS).setCustomName(Text.of("§f§lHeavy Pants")));
                this.player.getInventory().armor.set(2, new ItemStack(Items.CHAINMAIL_CHESTPLATE).setCustomName(Text.of("§f§lHeavy Chestplate")));
                this.player.getInventory().armor.set(3, new ItemStack(Items.CHAINMAIL_HELMET).setCustomName(Text.of("§f§lHeavy Helmet")));
            }
            case "hiders" -> {
                this.player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, StatusEffectInstance.INFINITE, 0, false, false));
                this.player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, StatusEffectInstance.INFINITE, 1, false, false));

                this.player.getInventory().clear();
            }
        }

        for (ItemStack item : this.player.getInventory().main) {
            item.getOrCreateNbt().putBoolean("Unbreakable", true);
            item.addHideFlag(ItemStack.TooltipSection.UNBREAKABLE);
        }

        for (ItemStack item : this.player.getInventory().armor) {
            item.getOrCreateNbt().putBoolean("Unbreakable", true);
            item.addHideFlag(ItemStack.TooltipSection.UNBREAKABLE);
        }
    }

    public void playerDeath() {
        BlockHuntTitle.sendTitle(this.player,
                Text.literal("")
                .append(
                        Text.translatable("event.blockhunt.death")
                                .formatted(Formatting.RED)
                ),
                Text.literal("")
                        .append(
                        Text.translatable("event.blockhunt.death_time", 5)
                                .formatted(Formatting.GRAY)
                        ),
                0, 0, 0
        );
        respawnTicks = 100;
    }
}
