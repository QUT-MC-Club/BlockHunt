package com.github.voxxin.blockhunt;

import com.github.voxxin.blockhunt.game.BlockHuntConfig;
import com.github.voxxin.blockhunt.game.BlockHuntWaiting;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.plasmid.game.GameType;

import java.util.ArrayList;
import java.util.List;

public class BlockHunt implements ModInitializer {

    public static final String ID = "blockhunt";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    public static final GameType<BlockHuntConfig> TYPE = GameType.register(
            new Identifier(ID, "blockhunt"),
            BlockHuntConfig.CODEC,
            BlockHuntWaiting::open
    );
    @Override
    public void onInitialize() {
    }

    public static Identifier id(String value) {
        return new Identifier(ID, value);
    }
    public static List<Integer> deniedIDs = new ArrayList<>();
}
