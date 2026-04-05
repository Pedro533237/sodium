package net.caffeinemc.mods.sodium.mixin.features.render.world.optimized;

import net.caffeinemc.mods.sodium.client.render.chunk.optimized.OptimizedChunkPipeline;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Automatiza atualização de meshes quando chunks são carregados/alterados/descarregados.
 */
@Mixin(ClientChunkCache.class)
public class ClientChunkCacheOptimizedMixin {
    @Inject(method = "replaceWithPacketData", at = @At("RETURN"), require = 0)
    private void sodium$markChunkDirty(int chunkX,
                                       int chunkZ,
                                       FriendlyByteBuf friendlyByteBuf,
                                       Map<Heightmap.Types, long[]> map,
                                       Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer,
                                       CallbackInfoReturnable<LevelChunk> cir) {
        OptimizedChunkPipeline.get().onChunkChanged(new ChunkPos(chunkX, chunkZ));
    }

    @Inject(method = "drop", at = @At("HEAD"), require = 0)
    private void sodium$unloadChunk(ChunkPos pos, CallbackInfo ci) {
        OptimizedChunkPipeline.get().onChunkUnloaded(pos);
    }
}
