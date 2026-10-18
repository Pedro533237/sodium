package net.caffeinemc.mods.sodium.client.gpu.device.batch;

import net.caffeinemc.mods.sodium.api.memory.MemoryIntrinsics;
import net.caffeinemc.mods.sodium.client.gpu.device.context.DrawContext;
import net.caffeinemc.mods.sodium.client.util.UInt32;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

public final class GLDrawBatch extends MultiDrawBatch {
    private final long pElementPointer;
    private final long pElementCount;
    private final long pBaseVertex;

    public GLDrawBatch(int capacity) {
        this.pElementPointer = MemoryUtil.nmemAlignedAlloc(32, (long) capacity * Pointer.POINTER_SIZE);
        MemoryUtil.memSet(this.pElementPointer, 0x0, (long) capacity * Pointer.POINTER_SIZE);

        this.pElementCount = MemoryUtil.nmemAlignedAlloc(32, (long) capacity * Integer.BYTES);
        this.pBaseVertex = MemoryUtil.nmemAlignedAlloc(32, (long) capacity * Integer.BYTES);
    }

    @Override
    public void put(int size, int elementCount, int baseVertex, long elementOffset) {
        MemoryIntrinsics.putInt(this.pElementCount + (size << 2), UInt32.uncheckedDowncast(elementCount));
        MemoryIntrinsics.putInt(this.pBaseVertex + (size << 2), UInt32.uncheckedDowncast(baseVertex));

        // * 4 to convert to bytes (the index buffer contains integers)
        MemoryIntrinsics.putAddress(this.pElementPointer + (size << Pointer.POINTER_SHIFT), elementOffset << 2);

        this.updateMaxElementCount(elementCount);
    }

    @Override
    public boolean tryExtendLast(int size, int elementCount, int baseVertex, long elementOffset) {
        if (size <= 0) {
            return false;
        }

        final long last = size - 1;
        final int lastCount = MemoryIntrinsics.getInt(this.pElementCount + (last << 2));
        final int lastBase = MemoryIntrinsics.getInt(this.pBaseVertex + (last << 2));
        final long lastOffset = MemoryIntrinsics.getAddress(this.pElementPointer + (last << Pointer.POINTER_SHIFT));

        // Same index data, and the new vertex range starts exactly where the previous one ended
        // (6 indices per 4 vertices).
        if (lastOffset != (elementOffset << 2) || lastBase + (lastCount / 6) * 4 != baseVertex) {
            return false;
        }

        final int merged = lastCount + elementCount;
        MemoryIntrinsics.putInt(this.pElementCount + (last << 2), merged);
        this.updateMaxElementCount(merged);
        return true;
    }

    @Override
    public void draw(DrawContext context) {
        context.getPass().multiDrawIndexed(MemoryUtil.memPointerBuffer(this.pElementPointer, this.size),
                MemoryUtil.memIntBuffer(this.pElementCount, this.size),
                MemoryUtil.memIntBuffer(this.pBaseVertex, this.size), this.size);
    }

    @Override
    public void delete() {
        MemoryUtil.nmemAlignedFree(this.pElementPointer);
        MemoryUtil.nmemAlignedFree(this.pElementCount);
        MemoryUtil.nmemAlignedFree(this.pBaseVertex);
    }
}