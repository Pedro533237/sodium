package net.caffeinemc.mods.sodium.client.render.chunk.experimental;

import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.util.List;

/**
 * Encapsula o SSBO de instâncias de faces e o buffer de draw indirect.
 */
public final class FaceInstanceBuffer {
    private static final int INTS_PER_INSTANCE = 3;

    private final int ssboId;
    private final int indirectBufferId;

    private int instanceCount;

    /**
     * Cria todos os objetos GL necessários para uma sub-mesh direcional.
     */
    public FaceInstanceBuffer() {
        this.ssboId = GL15C.glGenBuffers();
        this.indirectBufferId = GL15C.glGenBuffers();
    }

    /**
     * Faz upload das instâncias para o SSBO e grava comando de draw indirect.
     */
    public void upload(List<ChunkMesher.FaceInstance> instances, int indexCountPerInstance) {
        this.instanceCount = instances.size();

        IntBuffer payload = MemoryUtil.memAllocInt(Math.max(1, instanceCount * INTS_PER_INSTANCE));

        for (ChunkMesher.FaceInstance instance : instances) {
            payload.put(instance.packedVertex());
            payload.put(instance.packedExtent());
            payload.put(instance.faceFlags());
        }

        payload.flip();

        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, this.ssboId);
        GL15C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, payload, GL15C.GL_DYNAMIC_DRAW);
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);

        MemoryUtil.memFree(payload);

        IntBuffer indirect = MemoryUtil.memAllocInt(5);
        indirect.put(indexCountPerInstance);
        indirect.put(instanceCount);
        indirect.put(0);
        indirect.put(0);
        indirect.put(0);
        indirect.flip();

        GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, this.indirectBufferId);
        GL15C.glBufferData(GL40C.GL_DRAW_INDIRECT_BUFFER, indirect, GL15C.GL_DYNAMIC_DRAW);
        GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, 0);

        MemoryUtil.memFree(indirect);
    }

    /**
     * Faz bind do SSBO no binding point definido pelo shader.
     */
    public void bindSsbo(int bindingPoint) {
        GL31C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, bindingPoint, this.ssboId);
    }

    /**
     * Emite um draw call indireto único para a sub-mesh.
     */
    public void drawIndirect() {
        if (this.instanceCount <= 0) {
            return;
        }

        GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, this.indirectBufferId);
        GL40C.glDrawElementsIndirect(GL15C.GL_TRIANGLES, GL15C.GL_UNSIGNED_INT, 0L);
        GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, 0);
    }

    /**
     * Libera recursos GL desta sub-mesh.
     */
    public void delete() {
        GL15C.glDeleteBuffers(this.ssboId);
        GL15C.glDeleteBuffers(this.indirectBufferId);
    }

    /**
     * Quantidade de instâncias carregadas no buffer.
     */
    public int getInstanceCount() {
        return this.instanceCount;
    }
}
