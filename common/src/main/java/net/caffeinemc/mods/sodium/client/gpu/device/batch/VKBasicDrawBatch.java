package net.caffeinemc.mods.sodium.client.gpu.device.batch;

import net.caffeinemc.mods.sodium.client.gpu.device.context.DrawContext;
import net.caffeinemc.mods.sodium.client.util.UInt32;

/**
 * Portable Vulkan fallback which submits one indexed draw at a time.
 * This intentionally uses only the basic indexed-draw operation, so it does
 * not require optional multi-draw or indirect-draw features.
 */
public final class VKBasicDrawBatch extends MultiDrawBatch {
    private final int[] indexCounts;
    private final int[] baseVertices;
    private final int[] firstIndices;

    public VKBasicDrawBatch(int capacity) {
        this.indexCounts = new int[capacity];
        this.baseVertices = new int[capacity];
        this.firstIndices = new int[capacity];
    }

    @Override
    public void put(int index, int elementCount, int baseVertex, long elementOffset) {
        this.indexCounts[index] = elementCount;
        this.baseVertices[index] = baseVertex;
        this.firstIndices[index] = UInt32.uncheckedDowncast(elementOffset);
        this.updateMaxElementCount(elementCount);
    }

    @Override
    public void draw(DrawContext context) {
        var pass = context.getPass();
        for (int i = 0; i < this.size; i++) {
            pass.drawIndexed(this.indexCounts[i], 1, this.firstIndices[i], this.baseVertices[i], 0);
        }
    }

    @Override
    public void delete() {
        // Java-owned arrays require no explicit native cleanup.
    }
}
