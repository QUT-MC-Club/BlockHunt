package com.github.voxxin.blockhunt.game.mixin;

import com.github.voxxin.blockhunt.game.util.ext.DisplayEntityExt;
import net.minecraft.block.BlockState;
import net.minecraft.entity.decoration.DisplayEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DisplayEntity.BlockDisplayEntity.class)
public abstract class DisplayEntityMixin implements DisplayEntityExt {


    @Shadow protected abstract void setBlockState(BlockState state);

    public void blockHunt$setState(BlockState blockState) {
        this.setBlockState(blockState);
    }
}
