package net.caffeinemc.mods.sodium.client.render.chunk.optimized;

/**
 * Utilitário para compactar os metadados de uma face em 32 bits.
 * <p>
 * Layout adotado (do bit menos significativo para o mais significativo):
 * <ul>
 *     <li>0..4   -> posição local X (0..31)</li>
 *     <li>5..9   -> posição local Y (0..31)</li>
 *     <li>10..14 -> posição local Z (0..31)</li>
 *     <li>15..17 -> normal da face (0..5, usando os índices de {@link net.minecraft.core.Direction})</li>
 *     <li>18..31 -> id de textura (0..16383)</li>
 * </ul>
 */
public final class VertexPacker {
    private static final int X_BITS = 5;
    private static final int Y_BITS = 5;
    private static final int Z_BITS = 5;
    private static final int NORMAL_BITS = 3;

    private static final int X_SHIFT = 0;
    private static final int Y_SHIFT = X_SHIFT + X_BITS;
    private static final int Z_SHIFT = Y_SHIFT + Y_BITS;
    private static final int NORMAL_SHIFT = Z_SHIFT + Z_BITS;
    private static final int TEXTURE_SHIFT = NORMAL_SHIFT + NORMAL_BITS;

    private static final int X_MASK = (1 << X_BITS) - 1;
    private static final int Y_MASK = (1 << Y_BITS) - 1;
    private static final int Z_MASK = (1 << Z_BITS) - 1;
    private static final int NORMAL_MASK = (1 << NORMAL_BITS) - 1;
    private static final int TEXTURE_MASK = (1 << (32 - TEXTURE_SHIFT)) - 1;

    private VertexPacker() {
    }

    /**
     * Compacta posição, normal e textura em um inteiro de 32 bits.
     */
    public static int encode(int localX, int localY, int localZ, int normalId, int textureId) {
        return (localX & X_MASK) << X_SHIFT
                | (localY & Y_MASK) << Y_SHIFT
                | (localZ & Z_MASK) << Z_SHIFT
                | (normalId & NORMAL_MASK) << NORMAL_SHIFT
                | (textureId & TEXTURE_MASK) << TEXTURE_SHIFT;
    }

    /**
     * Extrai o X local da palavra compactada.
     */
    public static int decodeX(int packed) {
        return (packed >>> X_SHIFT) & X_MASK;
    }

    /**
     * Extrai o Y local da palavra compactada.
     */
    public static int decodeY(int packed) {
        return (packed >>> Y_SHIFT) & Y_MASK;
    }

    /**
     * Extrai o Z local da palavra compactada.
     */
    public static int decodeZ(int packed) {
        return (packed >>> Z_SHIFT) & Z_MASK;
    }

    /**
     * Extrai o índice da normal da face (0..5).
     */
    public static int decodeNormalId(int packed) {
        return (packed >>> NORMAL_SHIFT) & NORMAL_MASK;
    }

    /**
     * Extrai o id de textura da palavra compactada.
     */
    public static int decodeTextureId(int packed) {
        return (packed >>> TEXTURE_SHIFT) & TEXTURE_MASK;
    }
}
