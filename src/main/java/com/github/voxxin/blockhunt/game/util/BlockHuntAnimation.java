package com.github.voxxin.blockhunt.game.util;

import com.github.voxxin.blockhunt.BlockHunt;
import com.github.voxxin.blockhunt.game.util.ext.WrittenBookItemExt;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.item.WrittenBookItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;

public class BlockHuntAnimation {
    private static int animationIndex = 0;
    public final Identifier animationName;
    private ArrayList<BlockHuntAnimationPoints> frames = new ArrayList<>();
    private BlockHuntAnimationPoints animationPlayPoint;
    private World world;
    public Settings settings;
    private boolean finishedAnimation = false;
    private int ticks = 0;

    public BlockHuntAnimation(Identifier animationName, @Nullable BlockHuntAnimationPoints animationPlayPoint, @Nullable BlockPos settingsPoint) {
        this.animationName = animationName;
        this.animationPlayPoint = animationPlayPoint;
        this.settings = new Settings(this.animationName, settingsPoint, null);
        frames.clear();
        ticks = 0;
        animationIndex = 0;
    }

    public void setAnimationPlayPoint(BlockHuntAnimationPoints animationPlayPoint) {
        this.animationPlayPoint = animationPlayPoint;
    }

    public void setSettingsPoint(BlockPos settingsPoint) {
        this.settings = new Settings(this.animationName, settingsPoint, null);
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public void setFrame(BlockHuntAnimationPoints frame, int index) {
        int tempIndex = 0;
        if (index <= 0) tempIndex = 1;
        while ((frames.size() - 1) < (index + tempIndex)) {
            frames.add(frame);
        }
        frames.set(index, frame);
    }

    private void nextFrame() {
        if (this.world == null) return;
        if (frames.size() == 0) {
            BlockHunt.LOGGER.error("No frames were added to animation " + animationName + ". Will be forever skipping this animation.");
            finishedAnimation = true;
            return;
        }
        ActionResult returnVal = frames.get(animationIndex).applyBlocksInFrame(animationPlayPoint, world);
        if (!returnVal.isAccepted()) BlockHunt.LOGGER.error("Failed to apply animation frame " + animationIndex + " of animation " + animationName
                + "\n" + " Will be skipping this frame.");

        if (settings.loop && animationIndex == frames.size() - 1) {animationIndex = 0; return;}
        else if (!settings.loop && animationIndex == frames.size() - 1) {finishedAnimation = true; return;}

        animationIndex++;
    }

    public void tick(float startTick, float gameTick) {
        settings.tick();
        settings.setWorld(world);
        if ((settings.startTime + startTick) > gameTick) return;
        if (finishedAnimation) return;
        if (animationPlayPoint == null) return;
        int fps = (int) (settings.fps * 20);
        if (ticks >= fps) {
            ticks = 0;
            nextFrame();
        }
        ticks++;
    }

    public static class Settings {
        private final Identifier animationName;
        private final BlockPos settingsPoint;
        private World world;
        private boolean loop = false;
        private double fps = 1;

        public double startTime = 0;

        private boolean parsedValues = false;

        public Settings(Identifier animationName, BlockPos settingsPoint, @Nullable World world) {
            this.animationName = animationName;
            this.settingsPoint = settingsPoint;
            this.world = world;
            parseValues();
        }

        public void setWorld(World world) {
            this.world = world;
        }

        public void tick() {
            if (world != null && !parsedValues) {
                parseValues();
            }
        }

        private void parseValues() {
            if (world != null && settingsPoint != null) {
                parsedValues = true;

                LecternBlockEntity lectern = (LecternBlockEntity) world.getBlockEntity(settingsPoint);
                if (lectern == null) {
                    BlockHunt.LOGGER.error("Settings could not be created, since no lectern was found at position " + settingsPoint);
                    return;
                }
                if (!lectern.hasBook()) {
                    BlockHunt.LOGGER.error("Settings could not be created, since no book was found on lectern at position " + settingsPoint);
                    return;
                }
                WrittenBookItem book = (WrittenBookItem) lectern.getBook().getItem();

                ArrayList<String> bookContents = ((WrittenBookItemExt)book).getPages(lectern.getBook());

                if (!book.getName(lectern.getBook()).getString().equals(animationName.getPath())) {
                    BlockHunt.LOGGER.error("Settings could not be created, since the book on lectern at position " + settingsPoint + " does not match the animation name " + animationName.getPath() + " (" + animationName + ") was looking for " + book.getName(lectern.getBook()));
                    return;
                }

                for (String page : bookContents) {
                    page = page.toLowerCase();
                    page = page.replace("\\n", " ");
                    page = page.replace("\"", " \" ");

                    if (page.contains("loop")) {
                        int startIndex = page.indexOf("loop") + "loop".length();
                        String remainingContent = page.substring(startIndex).trim();

                        String[] split = remainingContent.split("\\s+|\\n");
                        if (split.length == 0) {
                            BlockHunt.LOGGER.error("Settings could not be created, since the book on lectern at position " + settingsPoint + " does not have a loop value");
                            return;
                        }

                        if (split[0].equals("true") || split[0].equals("false")) {
                            this.loop = Boolean.parseBoolean(split[0]);
                        } else {
                            BlockHunt.LOGGER.error("Settings could not be created, since the book on lectern at position " + settingsPoint + " does not have a valid loop value");
                            return;
                        }
                    }
                    if (page.contains("framerate")) {
                        int startIndex = page.indexOf("framerate") + "framerate".length();
                        String remainingContent = page.substring(startIndex).trim();

                        String[] split = remainingContent.split("\\s+|\\n");
                        if (split.length == 0) {
                            BlockHunt.LOGGER.error("Settings could not be created, since the book on lectern at position " + settingsPoint + " does not have a frameRate value");
                            return;
                        }
                        try {
                            this.fps = Double.parseDouble(split[0]);
                            if (fps == 0) {
                                this.fps = 1;
                                BlockHunt.LOGGER.error("Settings could not be created, since the book on lectern at position " + settingsPoint + " does not have a frameRate value above 1");
                                return;
                            }
                        } catch (NumberFormatException e) {
                            BlockHunt.LOGGER.error("Settings could not be created, since the book on lectern at position " + settingsPoint + " does not have a valid frameRate value");
                            return;
                        }
                    }
                    if (page.contains("starttime")) {
                        int startIndex = page.indexOf("starttime") + "starttime".length();
                        String remainingContent = page.substring(startIndex).trim();

                        String[] split = remainingContent.split("\\s+|\\n");
                        if (split.length == 0) {
                            BlockHunt.LOGGER.error("Settings could not be created, since the book on lectern at position " + settingsPoint + " does not have a valid startTime value");
                            return;
                        }
                        try {
                            boolean secondSplit = Arrays.stream(split).toList().size() == 2;

                            int minutes = 0;
                            int seconds = 0;
                            if (split[0].contains("m:")) minutes = Integer.parseInt(split[0].replace("m:", ""));
                            if (split[0].contains("s:")) seconds = Integer.parseInt(split[0].replace("s:", ""));
                            if (secondSplit && split[1].contains("m:"))
                                minutes = Integer.parseInt(split[1].replace("m:", ""));
                            if (secondSplit && split[1].contains("s:"))
                                seconds = Integer.parseInt(split[1].replace("s:", ""));

                            this.startTime = ((minutes * 60) + seconds) * 20;
                        } catch (NumberFormatException e) {
                            BlockHunt.LOGGER.error("Settings could not be created, since the book on lectern at position " + settingsPoint + " does not have a valid startTime value format");
                            return;
                        }
                    }
                }
            }
        }
    }
}
