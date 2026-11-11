package com.qwennobert.seedminer.seed;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import dev.xpple.seedmapper.command.CustomClientCommandSource;
import dev.xpple.seedmapper.command.arguments.SeedResolutionArgument;
import dev.xpple.seedmapper.util.SeedIdentifier;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Asks SeedMapper for the seed of the current world, the same way its commands do. This respects SeedMapper's
 * {@code SeedResolutionOrder}: by default the {@code Seed} config, then the seeds saved per server
 * ({@code SavedSeeds}), then SeedMapper's online seed database.
 */
public final class SeedMapperSeed {
    private SeedMapperSeed() {
    }

    public static Result get(Minecraft mc) {
        ClientPacketListener handler = mc.getConnection();
        if (handler == null) return null;

        try {
            CustomClientCommandSource source = CustomClientCommandSource.of((FabricClientCommandSource) handler.getSuggestionsProvider());
            Pair<SeedResolutionArgument.SeedResolution.Method, SeedIdentifier> seed = source.getSeed();
            return new Result(seed.getSecond(), describe(seed.getFirst()));
        }
        catch (CommandSyntaxException e) {
            // SeedMapper knows no seed for this world
            return null;
        }
        catch (Throwable t) {
            // SeedMapper internals are not API, don't crash the module if they change
            return null;
        }
    }

    private static String describe(SeedResolutionArgument.SeedResolution.Method method) {
        return switch (method) {
            case SEED_CONFIG -> "SeedMapper config";
            case SAVED_SEEDS_CONFIG -> "SeedMapper saved seeds";
            case ONLINE_DATABASE -> "SeedMapper online database";
            case COMMAND_SOURCE -> "SeedMapper";
        };
    }

    public record Result(SeedIdentifier seed, String origin) {
    }
}
