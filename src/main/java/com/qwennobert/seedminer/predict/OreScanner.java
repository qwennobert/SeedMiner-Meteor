package com.qwennobert.seedminer.predict;

import com.qwennobert.seedminer.SeedMinerAddon;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Runs ore predictions on a background thread, chunk by chunk, and hands the results back to the client thread.
 * All public methods are meant to be called from the client thread.
 */
public class OreScanner {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Seed Miner Scanner");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private final Queue<ChunkResult> results = new ConcurrentLinkedQueue<>();
    private final Set<Long> requested = new HashSet<>();
    private final AtomicInteger pending = new AtomicInteger();

    /** Incremented whenever results become stale, workers drop chunks of older generations. */
    private volatile int generation;
    private ScanParams params;

    // Only accessed on the worker thread
    private OrePredictor predictor;
    private ScanParams predictorParams;

    /**
     * Sets the prediction parameters, invalidating all previous results if they changed.
     *
     * @return true if the parameters changed
     */
    public boolean setParams(ScanParams params) {
        if (Objects.equals(this.params, params)) return false;
        this.params = params;
        reset();
        return true;
    }

    public void reset() {
        generation++;
        requested.clear();
        results.clear();
    }

    /**
     * Queues every chunk within {@code radius} chunks of {@code center} that has not been scanned yet, nearest first.
     */
    public void scanAround(ChunkPos center, int radius) {
        if (params == null) return;

        ScanParams params = this.params;
        int generation = this.generation;

        for (ChunkPos chunk : spiral(center, radius)) {
            if (!requested.add(chunk.pack())) continue;

            pending.incrementAndGet();
            executor.execute(() -> {
                try {
                    if (generation != this.generation) return;
                    results.add(new ChunkResult(generation, chunk, predict(params, chunk)));
                }
                catch (Throwable t) {
                    SeedMinerAddon.LOG.error("Failed to predict ores in chunk {}", chunk, t);
                }
                finally {
                    pending.decrementAndGet();
                }
            });
        }
    }

    /** Returns the next finished chunk, or null if none is ready. */
    public ChunkResult poll() {
        ChunkResult result;
        while ((result = results.poll()) != null) {
            if (result.generation() == generation) return result;
        }
        return null;
    }

    public int pendingChunks() {
        return pending.get();
    }

    public int scannedChunks() {
        return requested.size() - pending.get();
    }

    private List<PredictedOre> predict(ScanParams params, ChunkPos chunk) {
        if (!params.equals(predictorParams)) {
            closePredictor();
            predictor = new OrePredictor(params.seed(), params.version(), params.dimension(), params.generatorFlags());
            predictorParams = params;
        }

        Set<Integer> wanted = new HashSet<>();
        for (OreKind kind : params.ores()) wanted.add(kind.cubiomesId());

        List<PredictedOre> ores = new ArrayList<>();
        predictor.predictChunk(chunk.x(), chunk.z(), wanted::contains, params.minY(), params.maxY(), params.oreVeins(),
            (x, y, z, block) -> ores.add(new PredictedOre(new BlockPos(x, y, z), OreKind.ofCubiomes(block))));
        return ores;
    }

    private void closePredictor() {
        if (predictor != null) {
            predictor.close();
            predictor = null;
            predictorParams = null;
        }
    }

    private static List<ChunkPos> spiral(ChunkPos center, int radius) {
        List<ChunkPos> chunks = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        chunks.add(center);
        for (int r = 1; r <= radius; r++) {
            for (int i = -r; i < r; i++) {
                chunks.add(new ChunkPos(center.x() + i, center.z() - r));
                chunks.add(new ChunkPos(center.x() + r, center.z() + i));
                chunks.add(new ChunkPos(center.x() - i, center.z() + r));
                chunks.add(new ChunkPos(center.x() - r, center.z() - i));
            }
        }
        return chunks;
    }

    public record ChunkResult(int generation, ChunkPos chunk, List<PredictedOre> ores) {
    }
}
