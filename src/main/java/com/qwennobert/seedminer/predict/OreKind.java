package com.qwennobert.seedminer.predict;

import com.github.cubiomes.Cubiomes;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Blocks that can be predicted from the seed, mapped between Minecraft blocks and cubiomes block ids.
 * Cubiomes does not distinguish stone and deepslate ore variants, so both map to the same id.
 */
public enum OreKind {
    COAL(Cubiomes::COAL_ORE, Items.COAL, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
    COPPER(Cubiomes::COPPER_ORE, Items.RAW_COPPER, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
    IRON(Cubiomes::IRON_ORE, Items.RAW_IRON, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
    GOLD(Cubiomes::GOLD_ORE, Items.RAW_GOLD, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE),
    REDSTONE(Cubiomes::REDSTONE_ORE, Items.REDSTONE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
    LAPIS(Cubiomes::LAPIS_ORE, Items.LAPIS_LAZULI, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
    DIAMOND(Cubiomes::DIAMOND_ORE, Items.DIAMOND, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
    EMERALD(Cubiomes::EMERALD_ORE, Items.EMERALD, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
    RAW_COPPER_BLOCK(Cubiomes::RAW_COPPER_BLOCK, Items.RAW_COPPER_BLOCK, Blocks.RAW_COPPER_BLOCK),
    RAW_IRON_BLOCK(Cubiomes::RAW_IRON_BLOCK, Items.RAW_IRON_BLOCK, Blocks.RAW_IRON_BLOCK),
    NETHER_GOLD(Cubiomes::NETHER_GOLD_ORE, Items.GOLD_NUGGET, Blocks.NETHER_GOLD_ORE),
    NETHER_QUARTZ(Cubiomes::NETHER_QUARTZ_ORE, Items.QUARTZ, Blocks.NETHER_QUARTZ_ORE),
    ANCIENT_DEBRIS(Cubiomes::ANCIENT_DEBRIS, Items.ANCIENT_DEBRIS, Blocks.ANCIENT_DEBRIS);

    private static final OreKind[] VALUES = values();
    private static final Map<Block, OreKind> BY_BLOCK = new HashMap<>();

    static {
        for (OreKind kind : VALUES) {
            for (Block block : kind.blocks) BY_BLOCK.put(block, kind);
        }
    }

    private final IntSupplier cubiomesId;
    private final Item drop;
    private final List<Block> blocks;
    private int cachedId = Integer.MIN_VALUE;

    OreKind(IntSupplier cubiomesId, Item drop, Block... blocks) {
        this.cubiomesId = cubiomesId;
        this.drop = drop;
        this.blocks = List.of(blocks);
    }

    /** Resolved lazily, cubiomes must not be touched before SeedMapper has loaded the native library. */
    public int cubiomesId() {
        if (cachedId == Integer.MIN_VALUE) cachedId = cubiomesId.getAsInt();
        return cachedId;
    }

    public List<Block> blocks() {
        return blocks;
    }

    /** What the ore normally drops (without silk touch). */
    public Item drop() {
        return drop;
    }

    /** True for the normal drop of this ore and for the ore block itself (silk touch). */
    public boolean isDrop(Item item) {
        if (item == drop) return true;
        for (Block block : blocks) {
            if (block.asItem() == item) return true;
        }
        return false;
    }

    /** True if fortune increases the drops (not for raw ore blocks and ancient debris). */
    public boolean fortuneAffects() {
        return this != RAW_COPPER_BLOCK && this != RAW_IRON_BLOCK && this != ANCIENT_DEBRIS;
    }

    /** Average experience from mining it without silk touch (vanilla drops, e.g. nether quartz 2-5). */
    public double experience() {
        return switch (this) {
            case COAL -> 1;
            case NETHER_GOLD -> 0.5;
            case REDSTONE -> 3;
            case LAPIS, NETHER_QUARTZ -> 3.5;
            case DIAMOND, EMERALD -> 5;
            default -> 0;
        };
    }

    public boolean matches(Block block) {
        return blocks.contains(block);
    }

    public static OreKind of(Block block) {
        return BY_BLOCK.get(block);
    }

    public static OreKind ofCubiomes(int id) {
        for (OreKind kind : VALUES) {
            if (kind.cubiomesId() == id) return kind;
        }
        return null;
    }

    public static boolean isSupported(Block block) {
        return of(block) != null;
    }
}
