package com.github.voxxin.blockhunt.game.util.ext;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;

public interface WrittenBookItemExt {

    ArrayList<String> getPages(ItemStack book);
}
