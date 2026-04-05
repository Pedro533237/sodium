package net.caffeinemc.mods.sodium.client.render.chunk.experimental;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Gera mesh por seção 16x16x16 com face culling + greedy meshing.
 */
public class ChunkMesher {
    public static final int SECTION_SIZE = 16;

    /**
     * Resultado de meshing contendo seis sub-meshes (um por direção).
     */
    public record ChunkMesh(List<FaceInstance> north,
                            List<FaceInstance> south,
                            List<FaceInstance> east,
                            List<FaceInstance> west,
                            List<FaceInstance> up,
                            List<FaceInstance> down) {
        /**
         * Acessa as instâncias por índice de face 0..5 na ordem Direction.values().
         */
        public List<FaceInstance> byFaceIndex(int faceIndex) {
            return switch (faceIndex) {
                case 0 -> down;
                case 1 -> up;
                case 2 -> north;
                case 3 -> south;
                case 4 -> west;
                case 5 -> east;
                default -> List.of();
            };
        }
    }

    /**
     * Descrição de uma face em formato instanciado para SSBO.
     */
    public record FaceInstance(int packedVertex, int packedExtent, int faceFlags) {
    }

    /**
     * Gera uma malha compactada para uma seção de chunk.
     */
    public ChunkMesh build(ClientLevel level, SectionPos sectionPos) {
        var north = new ArrayList<FaceInstance>();
        var south = new ArrayList<FaceInstance>();
        var east = new ArrayList<FaceInstance>();
        var west = new ArrayList<FaceInstance>();
        var up = new ArrayList<FaceInstance>();
        var down = new ArrayList<FaceInstance>();

        for (Direction direction : Direction.values()) {
            meshDirection(level, sectionPos, direction, switch (direction) {
                case NORTH -> north;
                case SOUTH -> south;
                case EAST -> east;
                case WEST -> west;
                case UP -> up;
                case DOWN -> down;
            });
        }

        return new ChunkMesh(north, south, east, west, up, down);
    }

    /**
     * Processa uma direção específica para permitir sub-mesh por face e culling direcional.
     */
    private void meshDirection(ClientLevel level, SectionPos sectionPos, Direction direction, List<FaceInstance> output) {
        final int[][] maskTexture = new int[SECTION_SIZE][SECTION_SIZE];
        final boolean[][] maskFilled = new boolean[SECTION_SIZE][SECTION_SIZE];
        final boolean[][] used = new boolean[SECTION_SIZE][SECTION_SIZE];

        for (int slice = 0; slice < SECTION_SIZE; slice++) {
            for (int u = 0; u < SECTION_SIZE; u++) {
                for (int v = 0; v < SECTION_SIZE; v++) {
                    used[u][v] = false;

                    BlockPos.MutableBlockPos current = toWorld(sectionPos, direction, slice, u, v);
                    BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos(
                            current.getX() + direction.getStepX(),
                            current.getY() + direction.getStepY(),
                            current.getZ() + direction.getStepZ());

                    BlockState a = level.getBlockState(current);
                    BlockState b = level.getBlockState(neighbor);

                    if (isSolid(a) && !isSolid(b)) {
                        maskFilled[u][v] = true;
                        maskTexture[u][v] = blockTextureId(a);
                    } else {
                        maskFilled[u][v] = false;
                        maskTexture[u][v] = 0;
                    }
                }
            }

            // Greedy meshing 2D por fatia da direção atual.
            for (int u = 0; u < SECTION_SIZE; u++) {
                for (int v = 0; v < SECTION_SIZE; v++) {
                    if (!maskFilled[u][v] || used[u][v]) {
                        continue;
                    }

                    int textureId = maskTexture[u][v];
                    int width = 1;

                    while (u + width < SECTION_SIZE
                            && maskFilled[u + width][v]
                            && !used[u + width][v]
                            && maskTexture[u + width][v] == textureId) {
                        width++;
                    }

                    int height = 1;
                    boolean growing = true;

                    while (v + height < SECTION_SIZE && growing) {
                        for (int testU = u; testU < u + width; testU++) {
                            if (!maskFilled[testU][v + height]
                                    || used[testU][v + height]
                                    || maskTexture[testU][v + height] != textureId) {
                                growing = false;
                                break;
                            }
                        }

                        if (growing) {
                            height++;
                        }
                    }

                    for (int markU = u; markU < u + width; markU++) {
                        for (int markV = v; markV < v + height; markV++) {
                            used[markU][markV] = true;
                        }
                    }

                    int localX = localX(direction, slice, u, v);
                    int localY = localY(direction, slice, u, v);
                    int localZ = localZ(direction, slice, u, v);

                    int packedVertex = VertexPacker.encode(localX, localY, localZ, direction.get3DDataValue(), textureId);
                    int packedExtent = VertexPacker.encodeExtent(width, height);
                    int faceFlags = 1 << direction.get3DDataValue();

                    output.add(new FaceInstance(packedVertex, packedExtent, faceFlags));
                }
            }
        }
    }

    /**
     * Converte coordenadas de fatia para posição global.
     */
    private static BlockPos.MutableBlockPos toWorld(SectionPos sectionPos, Direction direction, int slice, int u, int v) {
        int baseX = sectionPos.minBlockX();
        int baseY = sectionPos.minBlockY();
        int baseZ = sectionPos.minBlockZ();

        return switch (direction) {
            case DOWN, UP -> new BlockPos.MutableBlockPos(baseX + u, baseY + slice, baseZ + v);
            case NORTH, SOUTH -> new BlockPos.MutableBlockPos(baseX + u, baseY + v, baseZ + slice);
            case WEST, EAST -> new BlockPos.MutableBlockPos(baseX + slice, baseY + u, baseZ + v);
        };
    }

    /**
     * Gera o eixo X local da origem do retângulo.
     */
    private static int localX(Direction direction, int slice, int u, int v) {
        return switch (direction) {
            case DOWN, UP, NORTH, SOUTH -> u;
            case WEST, EAST -> slice;
        };
    }

    /**
     * Gera o eixo Y local da origem do retângulo.
     */
    private static int localY(Direction direction, int slice, int u, int v) {
        return switch (direction) {
            case DOWN, UP -> slice;
            case NORTH, SOUTH -> v;
            case WEST, EAST -> u;
        };
    }

    /**
     * Gera o eixo Z local da origem do retângulo.
     */
    private static int localZ(Direction direction, int slice, int u, int v) {
        return switch (direction) {
            case DOWN, UP, WEST, EAST -> v;
            case NORTH, SOUTH -> slice;
        };
    }

    /**
     * Determina se um bloco participa da malha sólida.
     */
    private static boolean isSolid(BlockState state) {
        return !state.isAir() && state.canOcclude();
    }

    /**
     * Mapeia o bloco para um id de textura estável e compacto.
     */
    private static int blockTextureId(BlockState state) {
        return net.minecraft.world.level.block.Block.getId(state) & 0x3FFF;
    }
}
