package net.caffeinemc.mods.sodium.client.render.chunk.experimental;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

/**
 * Renderizador de chunks experimental com instancing + SSBO + draw indirect.
 */
public final class ChunkRenderer {
    private static final int SSBO_BINDING = 3;

    private final Minecraft minecraft;
    private final ChunkMesher mesher;

    private final Long2ObjectOpenHashMap<SectionRenderData> sections = new Long2ObjectOpenHashMap<>();

    private int vao;
    private int vbo;
    private int ebo;
    private int shaderProgram;

    /**
     * Estrutura por seção contendo as seis sub-meshes direcionais.
     */
    private static final class SectionRenderData {
        private final FaceInstanceBuffer[] faceBuffers = new FaceInstanceBuffer[6];

        private SectionRenderData() {
            for (int i = 0; i < faceBuffers.length; i++) {
                faceBuffers[i] = new FaceInstanceBuffer();
            }
        }

        private void delete() {
            for (FaceInstanceBuffer buffer : faceBuffers) {
                buffer.delete();
            }
        }
    }

    /**
     * Cria o renderizador e prepara recursos GL compartilhados.
     */
    public ChunkRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.mesher = new ChunkMesher();
        this.initGlObjects();
    }

    /**
     * Inicializa modelo base (quad) e shader de instancing.
     */
    private void initGlObjects() {
        this.vao = GL30C.glGenVertexArrays();
        this.vbo = GL15C.glGenBuffers();
        this.ebo = GL15C.glGenBuffers();

        GL30C.glBindVertexArray(this.vao);

        // Modelo base: 4 vértices 2D em triangle strip-friendly layout.
        float[] baseVertices = {
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 1.0f
        };

        int[] indices = {0, 1, 2, 2, 1, 3};

        FloatBuffer vertexBuffer = MemoryUtil.memAllocFloat(baseVertices.length).put(baseVertices).flip();
        IntBuffer indexBuffer = MemoryUtil.memAllocInt(indices.length).put(indices).flip();

        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, this.vbo);
        GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, vertexBuffer, GL15C.GL_STATIC_DRAW);
        GL20C.glEnableVertexAttribArray(0);
        GL20C.glVertexAttribPointer(0, 2, GL11C.GL_FLOAT, false, 2 * Float.BYTES, 0L);

        GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, this.ebo);
        GL15C.glBufferData(GL15C.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15C.GL_STATIC_DRAW);

        GL30C.glBindVertexArray(0);

        MemoryUtil.memFree(vertexBuffer);
        MemoryUtil.memFree(indexBuffer);

        this.shaderProgram = compileShaderProgram(
                "assets/sodium/shaders/experimental/chunk_instanced.vsh",
                "assets/sodium/shaders/experimental/chunk_instanced.fsh");
    }

    /**
     * Marca seção alterada para reconstrução no próximo frame.
     */
    public void markSectionDirty(SectionPos pos) {
        this.sections.remove(pos.asLong());
    }

    /**
     * Renderiza chunks visíveis com culling por direção e draw indirect.
     */
    public void render(Camera camera) {
        ClientLevel level = this.minecraft.level;

        if (level == null) {
            return;
        }

        GL20C.glUseProgram(this.shaderProgram);
        GL30C.glBindVertexArray(this.vao);

        EnumSet<Direction> visibleDirections = computeVisibleDirections(camera);

        // Faixa local de renderização para manter custo previsível.
        SectionPos cameraSection = SectionPos.of(camera.getBlockPosition());
        int radius = Math.min(8, this.minecraft.options.getEffectiveRenderDistance());

        for (int sx = cameraSection.x() - radius; sx <= cameraSection.x() + radius; sx++) {
            for (int sy = cameraSection.y() - 2; sy <= cameraSection.y() + 2; sy++) {
                for (int sz = cameraSection.z() - radius; sz <= cameraSection.z() + radius; sz++) {
                    SectionPos pos = SectionPos.of(sx, sy, sz);

                    if (!isSectionPotentiallyVisible(camera, pos)) {
                        continue;
                    }

                    SectionRenderData data = this.sections.computeIfAbsent(pos.asLong(), ignored -> buildSection(level, pos));

                    for (Direction direction : visibleDirections) {
                        FaceInstanceBuffer faceBuffer = data.faceBuffers[direction.get3DDataValue()];
                        faceBuffer.bindSsbo(SSBO_BINDING);
                        faceBuffer.drawIndirect();
                    }
                }
            }
        }

        GL30C.glBindVertexArray(0);
        GL20C.glUseProgram(0);
    }

    /**
     * Cria buffers de uma seção inteira com sub-meshes por direção.
     */
    private SectionRenderData buildSection(ClientLevel level, SectionPos pos) {
        ChunkMesher.ChunkMesh mesh = this.mesher.build(level, pos);
        SectionRenderData data = new SectionRenderData();

        for (Direction direction : Direction.values()) {
            data.faceBuffers[direction.get3DDataValue()].upload(mesh.byFaceIndex(direction.get3DDataValue()), 6);
        }

        return data;
    }

    /**
     * Culling simples por posição da câmera para evitar render de faces opostas.
     */
    private static EnumSet<Direction> computeVisibleDirections(Camera camera) {
        EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);

        var look = camera.getLookVector();

        if (look.x() >= -0.01) directions.add(Direction.EAST);
        if (look.x() <= 0.01) directions.add(Direction.WEST);
        if (look.y() >= -0.01) directions.add(Direction.UP);
        if (look.y() <= 0.01) directions.add(Direction.DOWN);
        if (look.z() >= -0.01) directions.add(Direction.SOUTH);
        if (look.z() <= 0.01) directions.add(Direction.NORTH);

        return directions;
    }

    /**
     * Culling espacial coarse usando AABB da seção.
     */
    private static boolean isSectionPotentiallyVisible(Camera camera, SectionPos pos) {
        AABB sectionBounds = new AABB(
                pos.minBlockX(), pos.minBlockY(), pos.minBlockZ(),
                pos.minBlockX() + 16.0, pos.minBlockY() + 16.0, pos.minBlockZ() + 16.0);
        return sectionBounds.closerThan(camera.getPosition(), 256.0);
    }

    /**
     * Libera todos os recursos GL do pipeline experimental.
     */
    public void destroy() {
        for (SectionRenderData data : this.sections.values()) {
            data.delete();
        }

        this.sections.clear();

        GL15C.glDeleteBuffers(this.vbo);
        GL15C.glDeleteBuffers(this.ebo);
        GL30C.glDeleteVertexArrays(this.vao);
        GL20C.glDeleteProgram(this.shaderProgram);
    }

    /**
     * Compila shaders GLSL (vertex + fragment) para pipeline instanciado.
     */
    private int compileShaderProgram(String vertexPath, String fragmentPath) {
        int vertexShader = compileShader(GL20C.GL_VERTEX_SHADER, readResource(vertexPath));
        int fragmentShader = compileShader(GL20C.GL_FRAGMENT_SHADER, readResource(fragmentPath));

        int program = GL20C.glCreateProgram();
        GL20C.glAttachShader(program, vertexShader);
        GL20C.glAttachShader(program, fragmentShader);
        GL20C.glLinkProgram(program);

        if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            throw new IllegalStateException("Erro ao linkar shader de chunks experimentais: " + GL20C.glGetProgramInfoLog(program));
        }

        GL20C.glDeleteShader(vertexShader);
        GL20C.glDeleteShader(fragmentShader);

        GL20C.glUseProgram(program);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(program, "uAtlas"), 0);
        GL20C.glUseProgram(0);

        return program;
    }

    /**
     * Compila shader individual e valida mensagens de erro.
     */
    private static int compileShader(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);

        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            throw new IllegalStateException("Erro de compilação GLSL: " + GL20C.glGetShaderInfoLog(shader));
        }

        return shader;
    }

    /**
     * Carrega arquivo GLSL empacotado nos recursos do mod.
     */
    private static String readResource(String path) {
        try (var stream = ChunkRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Shader não encontrado: " + path);
            }

            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler shader: " + path, e);
        }
    }
}
