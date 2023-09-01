package com.github.voxxin.blockhunt.game;

import com.github.voxxin.blockhunt.game.map.BlockHuntMapConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import xyz.nucleoid.plasmid.game.common.config.PlayerConfig;

public record BlockHuntConfig(BlockHuntMapConfig mapConfig) {
    public static final Codec<BlockHuntConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockHuntMapConfig.CODEC.fieldOf("map").forGetter(BlockHuntConfig::mapConfig)
    ).apply(instance, BlockHuntConfig::new));
}
