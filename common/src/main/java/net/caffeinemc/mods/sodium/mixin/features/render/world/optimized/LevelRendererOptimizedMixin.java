package net.caffeinemc.mods.sodium.mixin.features.render.world.optimized;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.caffeinemc.mods.sodium.client.render.chunk.optimized.OptimizedChunkPipeline;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepta o pipeline padrão e delega para o renderer otimizado.
 *
 * <p>Por segurança, o cancelamento só é ativado quando a propriedade JVM
 * {@code -Dsodium.optimized_chunk_pipeline=true} está definida.</p>
 */
@Mixin(LevelRenderer.class)
public class LevelRendererOptimizedMixin {
    @Unique
    private static final boolean ENABLED = Boolean.getBoolean("sodium.optimized_chunk_pipeline");

    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true, require = 0)
    private void sodium$renderOptimized(GraphicsResourceAllocator allocator,
                                        DeltaTracker deltaTracker,
                                        boolean drawBlockOutline,
                                        Camera camera,
                                        Matrix4f frustumMatrix,
                                        Matrix4f projectionMatrix,
                                        Matrix4f modelViewMatrix,
                                        GpuBufferSlice fogBuffer,
                                        Vector4f fogVector,
                                        boolean shouldTick,
                                        CallbackInfo ci) {
        if (!ENABLED) {
            return;
        }

        OptimizedChunkPipeline.get().render(camera, modelViewMatrix, projectionMatrix);
        ci.cancel();
    }
}
