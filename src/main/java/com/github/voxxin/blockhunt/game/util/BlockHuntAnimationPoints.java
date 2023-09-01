package com.github.voxxin.blockhunt.game.util;

import com.github.voxxin.blockhunt.BlockHunt;
import com.github.voxxin.blockhunt.game.util.ext.WorldExt;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public record BlockHuntAnimationPoints(Vec3i start, Vec3i end, boolean isAnimationPlayPoint) {

    public BlockHuntAnimationPoints( Vec3i start, Vec3i end, boolean isAnimationPlayPoint) {
        //Make sure it saves the lowest most north-western point as start
        int trueStartX = Math.min(start.getX(), end.getX());
        int trueStartY = Math.min(start.getY(), end.getY());
        int trueStartZ = Math.min(start.getZ(), end.getZ());

        this.start = new Vec3i(trueStartX, trueStartY, trueStartZ);

        //Make sure it saves the highest most south-eastern point as end
        int trueEndX = Math.max(start.getX(), end.getX());
        int trueEndY = Math.max(start.getY(), end.getY());
        int trueEndZ = Math.max(start.getZ(), end.getZ());

        this.end = new Vec3i(trueEndX, trueEndY, trueEndZ);

        this.isAnimationPlayPoint = isAnimationPlayPoint;
    }

    public int[] sizeOfAnimation() {
        int xDiff = this.end.getX() - this.start.getX();
        int yDiff = this.end.getY() - this.start.getY();
        int zDiff = this.end.getZ() - this.start.getZ();

        int[] measures = new int[3];

        measures[0] = Math.abs(xDiff);
        measures[1] = Math.abs(yDiff);
        measures[2] = Math.abs(zDiff);

        return measures;
    }
    public BlockState faceBlockState(BlockState thisBlockState, BlockState animationBlockState) {
        Collection<Property<?>> thisBlockRotations = thisBlockState.getProperties();
        Collection<Property<?>> animationBlockRotations = animationBlockState.getProperties();
        if (thisBlockRotations == animationBlockRotations) return animationBlockState;
        //thisBlockRotations.removeIf(property -> !property.getName().equals("facing"));

//        BlockState newBlockState = animationBlockState.getBlock().getDefaultState();
//        for (BlockRotation rotation : thisBlockRotations) {
//            if (rotation == BlockRotation.CLOCKWISE_90)
//                newBlockState = newBlockState.rotate(BlockRotation.CLOCKWISE_90);
//            newBlockState.rotate(BlockRotation.CLOCKWISE_90);
//        }
//
//        return newBlockState;
        return animationBlockState;
    }

    public ActionResult applyBlocksInFrame(BlockHuntAnimationPoints animationPoint, World world) {
        if (!Arrays.equals(animationPoint.sizeOfAnimation(), this.sizeOfAnimation())) {
            BlockHunt.LOGGER.error("Animation points are not the same size!" + " Size of AnimationPlayPoint: " + Arrays.toString(animationPoint.sizeOfAnimation()) + "  Size of Animation: " + Arrays.toString(this.sizeOfAnimation()));
            return ActionResult.FAIL;
        }
        if (!animationPoint.isAnimationPlayPoint) {
            BlockHunt.LOGGER.error("Animation point is not a play point!");
            return ActionResult.FAIL;
        }
        ArrayList<BlockPos> animationPointPositions = new ArrayList<>();
        ArrayList<BlockPos> thisPositions = new ArrayList<>();

        for (int x = animationPoint.start.getX(); x <= animationPoint.end.getX(); x++) {
            for (int y = animationPoint.start.getY(); y <= animationPoint.end.getY(); y++) {
                for (int z = animationPoint.start.getZ(); z <= animationPoint.end.getZ(); z++) {
                    animationPointPositions.add(new BlockPos(x, y, z));
                }
            }
        }

        for (int x = this.start.getX(); x <= this.end.getX(); x++) {
            for (int y = this.start.getY(); y <= this.end.getY(); y++) {
                for (int z = this.start.getZ(); z <= this.end.getZ(); z++) {
                    thisPositions.add(new BlockPos(x, y, z));
                }
            }
        }

        animationPointPositions.forEach(blockPos -> {
            int index = animationPointPositions.indexOf(blockPos);
            BlockPos relativePositionForIndex = thisPositions.get(index);

            if (world.getBlockState(relativePositionForIndex).getBlock() == Blocks.STRUCTURE_VOID) return;
            ((WorldExt)world).blockHunt$setBlockState(blockPos, world.getBlockState(relativePositionForIndex));
        });


        return ActionResult.SUCCESS;
    }
}
