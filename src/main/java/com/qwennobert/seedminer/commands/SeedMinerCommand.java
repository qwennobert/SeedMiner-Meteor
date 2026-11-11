package com.qwennobert.seedminer.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.qwennobert.seedminer.modules.SeedMiner;
import com.qwennobert.seedminer.seed.ResolvedSeed;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class SeedMinerCommand extends Command {
    public SeedMinerCommand() {
        super("seedminer", "Controls the Seed Miner module.", "sm-miner");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> status());
        builder.then(literal("status").executes(context -> status()));

        builder.then(literal("seed").then(argument("seed", StringArgumentType.greedyString()).executes(context -> {
            String seed = StringArgumentType.getString(context, "seed");
            Long parsed = ResolvedSeed.parseSeed(seed);
            if (parsed == null) {
                error("Invalid seed.");
                return SINGLE_SUCCESS;
            }
            module().setManualSeed(seed);
            info("Manual seed set to (highlight)%d(default).", parsed);
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("clear-seed").executes(context -> {
            module().setManualSeed("");
            info("Manual seed cleared.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("rescan").executes(context -> {
            module().rescan();
            info("Rescanning.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("skip").executes(context -> {
            module().skipTarget();
            info("Skipped current target.");
            return SINGLE_SUCCESS;
        }));
    }

    private int status() {
        SeedMiner module = module();
        ResolvedSeed seed = module.seed();
        info("Status: %s", module.status());
        info("Seed: %s", seed == null ? "none" : seed.seed() + " (" + seed.origin() + ")");
        info("Predicted: %d, mined: %d, skipped: %d, invalid: %d", module.predicted(), module.mined(), module.skipped(), module.invalid());
        String antiDetection = module.antiDetectionInfo();
        if (antiDetection != null) info("Anti-Detection: %s", antiDetection);
        String timeLeft = module.timeLeftInfo();
        if (timeLeft != null) info(timeLeft);
        return SINGLE_SUCCESS;
    }

    private static SeedMiner module() {
        return Modules.get().get(SeedMiner.class);
    }
}
