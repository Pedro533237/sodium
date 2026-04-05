package net.caffeinemc.mods.sodium.client.render.chunk.optimized;

import net.minecraft.core.Direction;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.EnumMap;
import java.util.List;

import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL43C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glBindBufferBase;

/**
 * Encapsula os buffers GPU usados pelo pipeline de instancing:
 * <ul>
 *     <li>SSBO com os dados das faces (posição/normal/textura + dimensões)</li>
 *     <li>Indirect buffer com o comando DrawElementsIndirectCommand</li>
 * </ul>
 */
public final class FaceInstanceBuffer {
    private static final int INDIRECT_COMMAND_SIZE_INTS = 5;

    private final int ssboId;
    private final int indirectBufferId;

    public FaceInstanceBuffer() {
        this.ssboId = glGenBuffers();
        this.indirectBufferId = glGenBuffers();
    }

    /**
     * Faz upload das instâncias de um chunk para o SSBO e preenche os comandos indirect por direção.
     */
    public void upload(EnumMap<Direction, List<ChunkMesher.FaceInstance>> facesByDirection) {
        int instanceCount = facesByDirection.values().stream().mapToInt(List::size).sum();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ssboData = stack.mallocInt(Math.max(1, instanceCount * 2));

            for (Direction direction : Direction.values()) {
                for (var face : facesByDirection.get(direction)) {
                    ssboData.put(face.packedBase());
                    ssboData.put((face.sizeU() & 0xFFFF) | (face.sizeV() << 16));
                }
            }
            ssboData.flip();

            glBindBuffer(GL_SHADER_STORAGE_BUFFER, this.ssboId);
            glBufferData(GL_SHADER_STORAGE_BUFFER, ssboData, GL_DYNAMIC_DRAW);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, this.ssboId);

            IntBuffer indirect = stack.mallocInt(Math.max(INDIRECT_COMMAND_SIZE_INTS * 6, INDIRECT_COMMAND_SIZE_INTS));

            int baseInstance = 0;
            for (Direction direction : Direction.values()) {
                int count = facesByDirection.get(direction).size();

                // DrawElementsIndirectCommand = {count, instanceCount, firstIndex, baseVertex, baseInstance}
                indirect.put(4);          // count: 4 índices para um triangle strip de quad base
                indirect.put(count);      // instanceCount
                indirect.put(0);          // firstIndex
                indirect.put(0);          // baseVertex
                indirect.put(baseInstance);

                baseInstance += count;
            }
            indirect.flip();

            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, this.indirectBufferId);
            glBufferData(GL_DRAW_INDIRECT_BUFFER, indirect, GL_STATIC_DRAW);
        }
    }

    /**
     * Liga o SSBO no binding point 0.
     */
    public void bindSsbo() {
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, this.ssboId);
    }

    /**
     * Liga o indirect buffer para draw indirect.
     */
    public void bindIndirectBuffer() {
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, this.indirectBufferId);
    }

    public void delete() {
        glDeleteBuffers(this.ssboId);
        glDeleteBuffers(this.indirectBufferId);
    }
}
