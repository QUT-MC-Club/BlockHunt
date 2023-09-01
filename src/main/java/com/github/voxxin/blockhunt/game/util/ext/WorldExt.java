package com.github.voxxin.blockhunt.game.util.ext;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public interface WorldExt {
    void blockHunt$setBlockState(int x, int y, int z, BlockState blockState);
    void blockHunt$setBlockState(BlockPos blockPos, BlockState blockState);
}
