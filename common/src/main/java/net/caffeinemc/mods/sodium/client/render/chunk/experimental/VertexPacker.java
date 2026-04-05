package net.caffeinemc.mods.sodium.client.render.chunk.experimental;

/**
 * Compacta dados de instância em 32 bits para reduzir tráfego de memória.
 *
 * Layout do inteiro compactado (LSB -> MSB):
 * bits 0..4   = x local (0..31)
 * bits 5..9   = y local (0..31)
 * bits 10..14 = z local (0..31)
 * bits 15..17 = normal/facing (0..5)
 * bits 18..31 = texture id (0..16383)
 */
public final class VertexPacker {
    private static final int X_BITS = 5;
    private static final int Y_BITS = 5;
    private static final int Z_BITS = 5;
    private static final int NORMAL_BITS = 3;
    private static final int TEXTURE_BITS = 14;

    private static final int X_SHIFT = 0;
    private static final int Y_SHIFT = X_SHIFT + X_BITS;
    private static final int Z_SHIFT = Y_SHIFT + Y_BITS;
    private static final int NORMAL_SHIFT = Z_SHIFT + Z_BITS;
    private static final int TEXTURE_SHIFT = NORMAL_SHIFT + NORMAL_BITS;

    private static final int X_MASK = (1 << X_BITS) - 1;
    private static final int Y_MASK = (1 << Y_BITS) - 1;
    private static final int Z_MASK = (1 << Z_BITS) - 1;
    private static final int NORMAL_MASK = (1 << NORMAL_BITS) - 1;
    private static final int TEXTURE_MASK = (1 << TEXTURE_BITS) - 1;

    private VertexPacker() {
    }

    /**
     * Empacota posição local, direção da face e textura em um único inteiro.
     */
    public static int encode(int x, int y, int z, int normal, int textureId) {
        return ((x & X_MASK) << X_SHIFT)
                | ((y & Y_MASK) << Y_SHIFT)
                | ((z & Z_MASK) << Z_SHIFT)
                | ((normal & NORMAL_MASK) << NORMAL_SHIFT)
                | ((textureId & TEXTURE_MASK) << TEXTURE_SHIFT);
    }

    /**
     * Recupera o eixo X local do valor compactado.
     */
    public static int decodeX(int packed) {
        return (packed >>> X_SHIFT) & X_MASK;
    }

    /**
     * Recupera o eixo Y local do valor compactado.
     */
    public static int decodeY(int packed) {
        return (packed >>> Y_SHIFT) & Y_MASK;
    }

    /**
     * Recupera o eixo Z local do valor compactado.
     */
    public static int decodeZ(int packed) {
        return (packed >>> Z_SHIFT) & Z_MASK;
    }

    /**
     * Recupera o índice da normal/facing da face.
     */
    public static int decodeNormal(int packed) {
        return (packed >>> NORMAL_SHIFT) & NORMAL_MASK;
    }

    /**
     * Recupera o identificador da textura.
     */
    public static int decodeTextureId(int packed) {
        return (packed >>> TEXTURE_SHIFT) & TEXTURE_MASK;
    }

    /**
     * Empacota largura/altura do retângulo do greedy meshing em 16 bits cada.
     */
    public static int encodeExtent(int width, int height) {
        return (width & 0xFFFF) | ((height & 0xFFFF) << 16);
    }

    /**
     * Retorna largura codificada em {@link #encodeExtent(int, int)}.
     */
    public static int decodeExtentWidth(int extentPacked) {
        return extentPacked & 0xFFFF;
    }

    /**
     * Retorna altura codificada em {@link #encodeExtent(int, int)}.
     */
    public static int decodeExtentHeight(int extentPacked) {
        return (extentPacked >>> 16) & 0xFFFF;
    }
}
