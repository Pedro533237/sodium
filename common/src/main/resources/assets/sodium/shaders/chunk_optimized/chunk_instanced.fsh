#version 430 core

in vec2 vUv;
flat in uint vTextureId;

uniform sampler2D uAtlas;

out vec4 fragColor;

vec2 atlasUv(uint textureId, vec2 uv) {
    // Atlas simplificado de 128x128 tiles.
    float grid = 128.0;
    float tx = float(textureId % 128u);
    float ty = float(textureId / 128u);

    vec2 tileMin = vec2(tx, ty) / grid;
    vec2 tileSize = vec2(1.0 / grid);
    return tileMin + uv * tileSize;
}

void main() {
    vec2 uv = atlasUv(vTextureId, vUv);
    vec4 color = texture(uAtlas, uv);

    if (color.a < 0.1) {
        discard;
    }

    fragColor = color;
}
