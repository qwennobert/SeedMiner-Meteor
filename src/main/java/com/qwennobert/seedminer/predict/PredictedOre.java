package com.qwennobert.seedminer.predict;

import net.minecraft.core.BlockPos;

public record PredictedOre(BlockPos pos, OreKind kind) {
}
