package net.caffeinemc.mods.sodium.client.render.chunk.optimized;

import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;

/**
 * Fachada singleton para integração com mixins.
 */
public final class OptimizedChunkPipeline {
    private static final OptimizedChunkPipeline INSTANCE = new OptimizedChunkPipeline();

    private final ChunkRenderer renderer = new ChunkRenderer();

    private OptimizedChunkPipeline() {
    }

    public static OptimizedChunkPipeline get() {
        return INSTANCE;
    }

    public void render(Camera camera, Matrix4f modelView, Matrix4f projection) {
        this.renderer.render(camera, modelView, projection);
    }

    public void onChunkChanged(ChunkPos pos) {
        this.renderer.markDirty(pos);
    }

    public void onBlockChanged(BlockPos pos) {
        this.renderer.markDirty(new ChunkPos(pos));
    }

    public void onChunkUnloaded(ChunkPos pos) {
        this.renderer.unload(pos);
    }
}
