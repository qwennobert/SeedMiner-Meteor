package com.qwennobert.seedminer.seed;

import com.github.cubiomes.Cubiomes;
import com.qwennobert.seedminer.SeedMinerAddon;
import dev.xpple.seedmapper.util.SeedIdentifier;
import net.minecraft.client.Minecraft;

/**
 * A world seed together with the generator settings and where it came from.
 */
public record ResolvedSeed(long seed, int version, int generatorFlags, String origin) {
    /** The Minecraft version this addon is built for, used when the seed comes without a version. */
    private static final int DEFAULT_VERSION = Cubiomes.MC_26_3();

    public static ResolvedSeed resolve(Minecraft mc, SeedSource source, String manualSeed, boolean largeBiomes) {
        int flags = largeBiomes ? Cubiomes.LARGE_BIOMES() : 0;

        return switch (source) {
            case Manual -> manual(manualSeed, flags);
            case SeedMapper -> seedMapper(mc, flags);
            case Auto -> {
                ResolvedSeed seed = manual(manualSeed, flags);
                yield seed != null ? seed : seedMapper(mc, flags);
            }
        };
    }

    private static ResolvedSeed manual(String text, int flags) {
        Long seed = parseSeed(text);
        return seed == null ? null : new ResolvedSeed(seed, DEFAULT_VERSION, flags, "manual seed");
    }

    private static ResolvedSeed seedMapper(Minecraft mc, int flags) {
        SeedMapperSeed.Result result = SeedMapperSeed.get(mc);
        if (result == null) return null;

        SeedIdentifier identifier = result.seed();
        int version = identifier.hasVersion() ? identifier.version() : DEFAULT_VERSION;
        return new ResolvedSeed(identifier.seed(), version, flags | identifier.generatorFlags(), result.origin());
    }

    /**
     * Parses a seed the same way the vanilla world creation screen does: numbers are used as-is, any other text is hashed.
     */
    public static Long parseSeed(String text) {
        if (text == null) return null;
        text = text.trim();
        if (text.isEmpty()) return null;

        try {
            return Long.parseLong(text);
        }
        catch (NumberFormatException e) {
            SeedMinerAddon.LOG.debug("Seed '{}' is not a number, using its hash", text);
            return (long) text.hashCode();
        }
    }
}
