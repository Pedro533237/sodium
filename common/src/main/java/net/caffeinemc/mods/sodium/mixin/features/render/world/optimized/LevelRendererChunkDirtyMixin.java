package net.caffeinemc.mods.sodium.mixin.features.render.world.optimized;

import net.caffeinemc.mods.sodium.client.render.chunk.optimized.OptimizedChunkPipeline;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marca o chunk como sujo quando blocos individuais mudam.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererChunkDirtyMixin {
    @Inject(method = "setBlockDirty", at = @At("HEAD"), require = 0)
    private void sodium$onBlockDirty(BlockPos pos, boolean important, CallbackInfo ci) {
        OptimizedChunkPipeline.get().onBlockChanged(pos);
    }
}
