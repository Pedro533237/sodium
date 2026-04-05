package net.caffeinemc.mods.sodium.client.render.chunk.optimized;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Gera mesh de um chunk 16x16x16 com:
 * <ul>
 *     <li>remoção de faces internas (face culling entre voxels adjacentes)</li>
 *     <li>greedy meshing por direção</li>
 *     <li>particionamento em 6 submeshes por direção (N/S/E/W/UP/DOWN)</li>
 * </ul>
 */
public final class ChunkMesher {
    public static final int SECTION_SIZE = 16;

    /**
     * Estrutura final enviada para o renderer: uma lista de instâncias por direção.
     */
    public record ChunkMesh(EnumMap<Direction, List<FaceInstance>> facesByDirection, int totalFaceCount) {
    }

    /**
     * Face compactada para renderização por instancing.
     *
     * @param packedBase metadados em 32 bits (posição base + normal + textura)
     * @param sizeU      largura do retângulo no plano local da face
     * @param sizeV      altura do retângulo no plano local da face
     */
    public record FaceInstance(int packedBase, int sizeU, int sizeV) {
    }

    /**
     * Reconstroi uma mesh completa de um chunk inteiro.
     */
    public ChunkMesh buildMesh(ClientLevel level, LevelChunk chunk) {
        var byDirection = new EnumMap<Direction, List<FaceInstance>>(Direction.class);

        for (var direction : Direction.values()) {
            byDirection.put(direction, new ArrayList<>());
            greedyMeshDirection(level, chunk, direction, byDirection.get(direction));
        }

        int count = byDirection.values().stream().mapToInt(List::size).sum();
        return new ChunkMesh(byDirection, count);
    }

    /**
     * Executa greedy meshing em uma direção específica.
     * <p>
     * Fluxo:
     * <ol>
     *     <li>Cria uma máscara 2D para cada fatia da seção.</li>
     *     <li>Marca células onde a face é visível.</li>
     *     <li>Agrupa retângulos adjacentes com mesmo material.</li>
     *     <li>Emite uma única FaceInstance por retângulo.</li>
     * </ol>
     */
    private void greedyMeshDirection(ClientLevel level, LevelChunk chunk, Direction direction, List<FaceInstance> output) {
        for (int slice = 0; slice < SECTION_SIZE; slice++) {
            int[][] textureMask = new int[SECTION_SIZE][SECTION_SIZE];

            for (int u = 0; u < SECTION_SIZE; u++) {
                for (int v = 0; v < SECTION_SIZE; v++) {
                    var pos = mapSliceToLocal(direction, slice, u, v);
                    int textureId = getVisibleFaceTexture(level, chunk, pos[0], pos[1], pos[2], direction);
                    textureMask[u][v] = textureId;
                }
            }

            boolean[][] consumed = new boolean[SECTION_SIZE][SECTION_SIZE];
            for (int u = 0; u < SECTION_SIZE; u++) {
                for (int v = 0; v < SECTION_SIZE; v++) {
                    if (consumed[u][v] || textureMask[u][v] < 0) {
                        continue;
                    }

                    int textureId = textureMask[u][v];
                    int width = 1;
                    while (u + width < SECTION_SIZE
                            && !consumed[u + width][v]
                            && textureMask[u + width][v] == textureId) {
                        width++;
                    }

                    int height = 1;
                    boolean expandable = true;
                    while (v + height < SECTION_SIZE && expandable) {
                        for (int k = 0; k < width; k++) {
                            if (consumed[u + k][v + height] || textureMask[u + k][v + height] != textureId) {
                                expandable = false;
                                break;
                            }
                        }

                        if (expandable) {
                            height++;
                        }
                    }

                    for (int du = 0; du < width; du++) {
                        for (int dv = 0; dv < height; dv++) {
                            consumed[u + du][v + dv] = true;
                        }
                    }

                    var origin = mapSliceToLocal(direction, slice, u, v);
                    int packed = VertexPacker.encode(origin[0], origin[1], origin[2], direction.ordinal(), textureId);
                    output.add(new FaceInstance(packed, width, height));
                }
            }
        }
    }

    /**
     * Decide se uma face deve existir e, caso exista, resolve o id de textura.
     */
    private int getVisibleFaceTexture(ClientLevel level, LevelChunk chunk, int localX, int localY, int localZ, Direction direction) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                chunk.getPos().getMinBlockX() + localX,
                chunk.getMinY() + localY,
                chunk.getPos().getMinBlockZ() + localZ
        );

        BlockState self = level.getBlockState(pos);
        if (self.isAir()) {
            return -1;
        }

        pos.move(direction);
        BlockState adjacent = level.getBlockState(pos);

        if (!adjacent.isAir() && adjacent.canOcclude()) {
            return -1;
        }

        return Math.floorMod(self.getBlock().hashCode(), 16384);
    }

    /**
     * Mapeia coordenadas (slice, u, v) para (x, y, z) conforme a direção.
     */
    private int[] mapSliceToLocal(Direction direction, int slice, int u, int v) {
        return switch (direction) {
            case DOWN, UP -> new int[]{u, slice, v};
            case NORTH, SOUTH -> new int[]{u, v, slice};
            case WEST, EAST -> new int[]{slice, v, u};
        };
    }
}
