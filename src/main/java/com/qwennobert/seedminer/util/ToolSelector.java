package com.qwennobert.seedminer.util;

import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;

/**
 * Picks the hotbar slot to break a block with: the highest fortune for ores, and for everything else the fastest
 * tool without fortune, so the fortune pickaxe only wears down on ores.
 */
public final class ToolSelector {
    /** Nothing in the hotbar is better than what is held, keep it. */
    public static final int KEEP = -1;
    /** The block can only be mined with a fortune tool, which is reserved for ores. */
    public static final int ONLY_FORTUNE = -2;

    private ToolSelector() {
    }

    /**
     * @param ore                true if fortune increases the drops of this block
     * @param fortuneOnlyOnOres  don't use fortune tools on other blocks
     * @param fortuneFallback    use a fortune tool on other blocks anyway if nothing else can mine them
     * @param minDurability      tools with less durability left (percent) are not used
     */
    public static int select(PlayerInventory inventory, BlockState state, boolean ore, boolean fortuneOnlyOnOres, boolean fortuneFallback, int minDurability) {
        boolean toolRequired = state.isToolRequired();

        int best = KEEP, reserved = KEEP;
        double bestScore = Double.NEGATIVE_INFINITY, reservedScore = Double.NEGATIVE_INFINITY;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (isWorn(stack, minDurability)) continue;
            if (toolRequired && !stack.isSuitableFor(state)) continue;

            int fortune = level(stack, Enchantments.FORTUNE);
            int silkTouch = level(stack, Enchantments.SILK_TOUCH);
            double score = stack.getMiningSpeedMultiplier(state) * 10 + level(stack, Enchantments.EFFICIENCY);

            boolean allowed;
            if (ore) {
                // Fortune first, then speed. Silk touch would drop the ore block instead of the items.
                score += fortune * 1000;
                allowed = silkTouch == 0;
            }
            else allowed = !fortuneOnlyOnOres || fortune == 0;

            if (allowed) {
                if (score > bestScore) {
                    bestScore = score;
                    best = slot;
                }
            }
            else if (score > reservedScore) {
                reservedScore = score;
                reserved = slot;
            }
        }

        if (best != KEEP) return best;
        if (reserved == KEEP) return KEEP;
        // Ores may fall back to silk touch, other blocks only to fortune if allowed
        return ore || fortuneFallback ? reserved : ONLY_FORTUNE;
    }

    public static boolean hasFortune(ItemStack stack) {
        return level(stack, Enchantments.FORTUNE) > 0;
    }

    /** Mending repairs it from the experience of mined ores, silk touch would make them drop none. */
    public static boolean canMend(ItemStack stack) {
        return stack.isDamageable() && level(stack, Enchantments.MENDING) > 0 && level(stack, Enchantments.SILK_TOUCH) == 0;
    }

    /** The tool has less durability left than minDurability (percent) and must not be used anymore. */
    public static boolean isWorn(ItemStack stack, int minDurability) {
        if (!stack.isDamageable() || minDurability <= 0) return false;
        int left = stack.getMaxDamage() - stack.getDamage();
        return left * 100 < minDurability * stack.getMaxDamage();
    }

    /** Durability left in percent, rounded down. */
    public static int durability(ItemStack stack) {
        if (!stack.isDamageable()) return 100;
        return (stack.getMaxDamage() - stack.getDamage()) * 100 / stack.getMaxDamage();
    }

    private static int level(ItemStack stack, RegistryKey<Enchantment> enchantment) {
        return Utils.getEnchantmentLevel(stack, enchantment);
    }
}
