package com.github.voxxin.blockhunt.game;

import eu.pb4.sidebars.api.SidebarUtils;
import eu.pb4.sidebars.api.lines.SidebarLine;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import xyz.nucleoid.plasmid.game.common.GlobalWidgets;
import xyz.nucleoid.plasmid.game.common.widget.SidebarWidget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockHuntSidebar {
    private final SidebarWidget sidebar;
    private static ArrayList<SidebarLine> lines = new ArrayList<>();
    private final World world;
    private final BlockHuntStageManager stageManager;
    public BlockHuntSidebar(GlobalWidgets widgets, Identifier map_name, World world, BlockHuntStageManager stageManager) {
        this.sidebar = widgets.addSidebar(Text.translatable("gameType.blockhunt.standard").formatted(Formatting.GOLD));
        this.world = world;
        this.stageManager = stageManager;

        String mapName = map_name.toString();
        mapName = mapName.replaceAll("blockhunt:", "");
        mapName = mapName.replaceAll("_", " ");
        mapName = mapName.substring(0, 1).toUpperCase() + mapName.substring(1);

        lines.clear();

        long seekersReleaseT = stageManager.seekersRelease;
        long seekersDurationTick = seekersReleaseT - stageManager.startTime;
        long totalSecondsR = seekersDurationTick / 20;
        int minutesR = (int) (totalSecondsR / 60);
        int secondsR = (int) (totalSecondsR % 60);
        String seekersRelease = String.format("%d:%02d", minutesR, secondsR);

        lines.add(SidebarLine.create(0, Text.literal("")));
        lines.add(SidebarLine.create(1, Text.literal("").append(Text.translatable("sidebar.blockhunt.map_name", Text.literal(mapName).formatted(Formatting.AQUA)))));
        lines.add(SidebarLine.create(2, Text.literal("")));
        lines.add(SidebarLine.create(3, Text.literal("").append(Text.translatable("sidebar.blockhunt.hider_count",  Text.literal(String.valueOf(world.getScoreboard().getTeam("hiders").getPlayerList().size())).formatted(Formatting.GREEN)))));
        lines.add(SidebarLine.create(4, Text.literal("")));
        lines.add(SidebarLine.create(5, Text.literal("").append(Text.translatable("sidebar.blockhunt.seeker_count",  Text.literal(String.valueOf(world.getScoreboard().getTeam("seekers").getPlayerList().size())).formatted(Formatting.GREEN)))));
        lines.add(SidebarLine.create(7, Text.literal("")));
        lines.add(SidebarLine.create(8, Text.literal("").append(Text.translatable("sidebar.blockhunt.game_time", Text.literal(stageManager.gameTime[0] / 60 / 20 + ":0" + stageManager.gameTime[1] / 20).formatted(Formatting.GREEN)))));
        lines.add(SidebarLine.create(9, Text.literal("")));
        lines.add(SidebarLine.create(10, Text.literal("").append(Text.translatable("sidebar.blockhunt.seeker_countdown", Text.literal(seekersRelease).formatted(Formatting.GREEN)))));
        lines.add(SidebarLine.create(11, Text.literal("")));

        for (SidebarLine line : lines) {
            this.sidebar.setLine(line);
        }
    }
    public void tick() {
        long currentTick = this.world.getTime();
        long finishTick = stageManager.finishTime;
        long gameDurationTicks = finishTick - currentTick;
        long totalSeconds = gameDurationTicks / 20;
        int minutes = (int) (totalSeconds / 60);
        int seconds = (int) (totalSeconds % 60);
        String formattedTime = String.format("%d:%02d", minutes, seconds);

        this.world.getPlayers().forEach(playerEntity -> {
            ServerPlayerEntity player = (ServerPlayerEntity) playerEntity;
            sidebar.addPlayer(player);
        });

        lines.set(3, SidebarLine.create(3, Text.literal("").append(Text.translatable("sidebar.blockhunt.hider_count", Text.literal(String.valueOf(this.world.getScoreboard().getTeam("hiders").getPlayerList().size())).formatted(Formatting.GREEN)))));
        lines.set(5, SidebarLine.create(5, Text.literal("").append(Text.translatable("sidebar.blockhunt.seeker_count", Text.literal(String.valueOf(this.world.getScoreboard().getTeam("seekers").getPlayerList().size())).formatted(Formatting.GREEN)))));
        lines.set(8, SidebarLine.create(8, Text.literal("").append(Text.translatable("sidebar.blockhunt.game_time", Text.literal(formattedTime).formatted(Formatting.GREEN)))));
        if (!stageManager.seekersReleased) {
            long seekersRelease = stageManager.seekersRelease;
            long seekersDurationTick = seekersRelease - currentTick;
            long totalSecondsR = seekersDurationTick / 20;
            int minutesR = (int) (totalSecondsR / 60);
            int secondsR = (int) (totalSecondsR % 60);
            String formattedTimeR = String.format("%d:%02d", minutesR, secondsR);

            lines.set(lines.size() - 2, SidebarLine.create(10, Text.literal("").append(Text.translatable("sidebar.blockhunt.seeker_countdown", Text.literal(formattedTimeR).formatted(Formatting.GREEN)))));
            lines.set(lines.size() - 1, SidebarLine.create(11, Text.literal("")));
        } else if (lines.size() == 11) {
            world.getPlayers().forEach(player -> {
                ((ServerPlayerEntity) player).playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            });

            this.sidebar.removeLine(lines.size());
            lines.remove(lines.size() - 1);
            this.sidebar.removeLine(lines.size());
            lines.remove(lines.size() - 1);
        }

        for (SidebarLine line : lines) {
            this.sidebar.setLine(line);
        }
    }
}
