package com.qwennobert.seedminer.predict;

import com.github.cubiomes.Cubiomes;

import java.util.stream.IntStream;

/**
 * All cubiomes ore features. Every feature has to be simulated, even unwanted ones, because ores that generate
 * later can only replace certain blocks placed by earlier ores. Same list as SeedMapper's {@code OreArgument.ORES}:
 * every ore type cubiomes knows, so new ones (like infested stone) are included automatically.
 */
public final class OreFeatures {
    private OreFeatures() {
    }

    public static int[] all() {
        return IntStream.range(0, Cubiomes.ORE_NUM()).toArray();
    }
}
