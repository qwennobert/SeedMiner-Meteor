package com.qwennobert.seedminer.compress;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Items that can be crafted into a block (or ingot) to save inventory space. */
public enum Compressible {
    DIAMOND(Items.DIAMOND, Items.DIAMOND_BLOCK, 9),
    EMERALD(Items.EMERALD, Items.EMERALD_BLOCK, 9),
    COAL(Items.COAL, Items.COAL_BLOCK, 9),
    REDSTONE(Items.REDSTONE, Items.REDSTONE_BLOCK, 9),
    LAPIS(Items.LAPIS_LAZULI, Items.LAPIS_BLOCK, 9),
    RAW_IRON(Items.RAW_IRON, Items.RAW_IRON_BLOCK, 9),
    RAW_COPPER(Items.RAW_COPPER, Items.RAW_COPPER_BLOCK, 9),
    RAW_GOLD(Items.RAW_GOLD, Items.RAW_GOLD_BLOCK, 9),
    GOLD_NUGGET(Items.GOLD_NUGGET, Items.GOLD_INGOT, 9),
    QUARTZ(Items.QUARTZ, Items.QUARTZ_BLOCK, 4);

    private static final Compressible[] VALUES = values();

    public final Item item;
    public final Item result;
    /** How many items one result needs. */
    public final int count;

    Compressible(Item item, Item result, int count) {
        this.item = item;
        this.result = result;
        this.count = count;
    }

    public static Compressible of(Item item) {
        for (Compressible compressible : VALUES) {
            if (compressible.item == item) return compressible;
        }
        return null;
    }

    public static boolean supports(Item item) {
        return of(item) != null;
    }
}
