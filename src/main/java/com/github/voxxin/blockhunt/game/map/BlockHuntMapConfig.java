package com.github.voxxin.blockhunt.game.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;


public record BlockHuntMapConfig(Identifier id) {
    public static final Codec<BlockHuntMapConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(BlockHuntMapConfig::id)
    ).apply(instance, BlockHuntMapConfig::new));
}
