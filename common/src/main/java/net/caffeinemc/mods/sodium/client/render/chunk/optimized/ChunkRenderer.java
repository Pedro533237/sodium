package net.caffeinemc.mods.sodium.client.render.chunk.optimized;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.glCompileShader;
import static org.lwjgl.opengl.GL20C.glCreateProgram;
import static org.lwjgl.opengl.GL20C.glCreateShader;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20C.glGetProgrami;
import static org.lwjgl.opengl.GL20C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20C.glGetShaderi;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glShaderSource;
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL31C.GL_TRIANGLE_STRIP;
import static org.lwjgl.opengl.GL40C.glDrawElementsIndirect;

/**
 * Renderizador final do pipeline otimizado de chunks.
 * <p>
 * Responsabilidades:
 * <ul>
 *     <li>Controlar VAO/VBO/SSBO/indirect buffers</li>
 *     <li>Compilar shaders GLSL de instancing</li>
 *     <li>Construir/atualizar meshes de chunks quando necessários</li>
 *     <li>Fazer draw indirect com culling por direção</li>
 * </ul>
 */
public final class ChunkRenderer {
    private static final ResourceLocation VERTEX_SHADER = ResourceLocation.fromNamespaceAndPath("sodium", "shaders/chunk_optimized/chunk_instanced.vsh");
    private static final ResourceLocation FRAGMENT_SHADER = ResourceLocation.fromNamespaceAndPath("sodium", "shaders/chunk_optimized/chunk_instanced.fsh");

    private static final int[] QUAD_STRIP_INDICES = {0, 1, 2, 3};

    private final ChunkMesher mesher = new ChunkMesher();
    private final Map<Long, CompiledChunk> compiled = new HashMap<>();
    private final Map<Long, Boolean> dirty = new HashMap<>();

    private int vaoId;
    private int indexBufferId;
    private int programId;

    /**
     * Inicializa GL resources sob demanda.
     */
    public void initIfNeeded() {
        if (this.programId != 0) {
            return;
        }

        this.vaoId = glGenVertexArrays();
        this.indexBufferId = glGenBuffers();

        glBindVertexArray(this.vaoId);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.indexBufferId);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer indexData = stack.mallocInt(QUAD_STRIP_INDICES.length);
            indexData.put(QUAD_STRIP_INDICES).flip();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexData, org.lwjgl.opengl.GL15C.GL_STATIC_DRAW);
        }

        this.programId = createProgram(loadShader(VERTEX_SHADER), loadShader(FRAGMENT_SHADER));
    }

    /**
     * Marca um chunk para rebuild de mesh na próxima renderização.
     */
    public void markDirty(ChunkPos chunkPos) {
        this.dirty.put(chunkPos.toLong(), true);
    }

    /**
     * Remove dados de GPU de um chunk descarregado.
     */
    public void unload(ChunkPos chunkPos) {
        CompiledChunk old = this.compiled.remove(chunkPos.toLong());
        if (old != null) {
            old.instances.delete();
        }
    }

    /**
     * Rebuild automático das meshes sujas.
     */
    public void updateDirtyChunks(ClientLevel level) {
        for (var entry : this.dirty.entrySet()) {
            if (!entry.getValue()) {
                continue;
            }

            ChunkPos pos = new ChunkPos((int) (entry.getKey() >> 32), (int) (long) entry.getKey());
            LevelChunk chunk = level.getChunkSource().getChunk(pos.x, pos.z, false);
            if (chunk == null) {
                continue;
            }

            var mesh = mesher.buildMesh(level, chunk);
            var buffer = new FaceInstanceBuffer();
            buffer.upload(mesh.facesByDirection());

            CompiledChunk old = this.compiled.put(pos.toLong(), new CompiledChunk(mesh, buffer));
            if (old != null) {
                old.instances.delete();
            }

            entry.setValue(false);
        }
    }

    /**
     * Renderiza todos os chunks compilados usando draw indirect por direção.
     */
    public void render(Camera camera, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        initIfNeeded();

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        updateDirtyChunks(level);

        glUseProgram(this.programId);
        glBindVertexArray(this.vaoId);

        int mvLoc = GlStateManager._glGetUniformLocation(this.programId, "uModelView");
        int projLoc = GlStateManager._glGetUniformLocation(this.programId, "uProjection");
        int atlasLoc = GlStateManager._glGetUniformLocation(this.programId, "uAtlas");

        RenderSystem.activeTexture(org.lwjgl.opengl.GL13C.GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, mc.getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).getId());

        glUniformMatrix4fv(mvLoc, false, modelViewMatrix.get(new float[16]));
        glUniformMatrix4fv(projLoc, false, projectionMatrix.get(new float[16]));
        glUniform1i(atlasLoc, 0);

        EnumSet<Direction> visibleDirections = visibleDirections(camera);

        for (var compiledChunk : this.compiled.values()) {
            compiledChunk.instances.bindSsbo();
            compiledChunk.instances.bindIndirectBuffer();

            for (Direction direction : Direction.values()) {
                if (!visibleDirections.contains(direction)) {
                    continue;
                }

                long offsetBytes = (long) direction.ordinal() * 5L * Integer.BYTES;
                glDrawElementsIndirect(GL_TRIANGLE_STRIP, org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT, offsetBytes);
            }
        }
    }

    /**
     * Culling direcional simples baseado no vetor de visão da câmera.
     */
    private EnumSet<Direction> visibleDirections(Camera camera) {
        var look = camera.getLookVector();
        EnumSet<Direction> dirs = EnumSet.noneOf(Direction.class);

        if (look.x() >= -0.2f) dirs.add(Direction.WEST);
        if (look.x() <= 0.2f) dirs.add(Direction.EAST);
        if (look.y() >= -0.2f) dirs.add(Direction.DOWN);
        if (look.y() <= 0.2f) dirs.add(Direction.UP);
        if (look.z() >= -0.2f) dirs.add(Direction.NORTH);
        if (look.z() <= 0.2f) dirs.add(Direction.SOUTH);

        return dirs;
    }

    private String loadShader(ResourceLocation location) {
        try (var input = Minecraft.getInstance().getResourceManager().open(location)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar shader: " + location, e);
        }
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vs = createShader(GL_VERTEX_SHADER, vertexSource);
        int fs = createShader(GL_FRAGMENT_SHADER, fragmentSource);

        int program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);

        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(program);
            throw new IllegalStateException("Falha de link do shader program: " + log);
        }

        glDeleteShader(vs);
        glDeleteShader(fs);
        return program;
    }

    private int createShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("Falha de compilação de shader: " + glGetShaderInfoLog(shader));
        }

        return shader;
    }

    public void delete() {
        for (CompiledChunk chunk : this.compiled.values()) {
            chunk.instances.delete();
        }
        this.compiled.clear();
        if (this.vaoId != 0) {
            glDeleteVertexArrays(this.vaoId);
        }
        if (this.programId != 0) {
            glDeleteProgram(this.programId);
        }
    }

    private record CompiledChunk(ChunkMesher.ChunkMesh mesh, FaceInstanceBuffer instances) {
    }
}
