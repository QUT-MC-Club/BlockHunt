package com.github.voxxin.blockhunt.game.mixin;

import com.github.voxxin.blockhunt.game.util.ext.WrittenBookItemExt;
import net.minecraft.item.ItemStack;
import net.minecraft.item.WrittenBookItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;

import static net.minecraft.item.WrittenBookItem.isValid;

@Mixin(WrittenBookItem.class)
public class WrittenBookItemMixin implements WrittenBookItemExt {
    @Override
    public ArrayList<String> getPages(ItemStack book) {
        ArrayList<String> pages = new ArrayList<>();

        NbtCompound nbtCompound = book.getNbt();
        assert nbtCompound != null;
        NbtList rawPage = nbtCompound.getList("pages", 8);

        for (int i = 0; i < rawPage.size(); ++i) {
            pages.add(rawPage.getString(i));
        }

        return pages;
    }
}
