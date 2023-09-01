package com.github.voxxin.blockhunt.game.mixin;

import com.github.voxxin.blockhunt.game.util.ext.WorldExt;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(World.class)
public abstract class WorldMixin implements WorldExt {

    @Shadow public abstract boolean setBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth);

    @Override
    public void blockHunt$setBlockState(int x, int y, int z, BlockState blockState) {
        this.setBlockState(new BlockPos(x,y,z), blockState, 3, 0);
    }

    @Override
    public void blockHunt$setBlockState(BlockPos blockPos, BlockState blockState) {
        this.setBlockState(blockPos, blockState, 3, 0);
    }

}
