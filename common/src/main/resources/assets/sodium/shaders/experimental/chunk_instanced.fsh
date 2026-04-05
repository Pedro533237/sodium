#version 430 core

in vec2 vUv;
flat in uint vTextureId;

uniform sampler2D uAtlas;

out vec4 fragColor;

vec2 atlasUv(uint textureId, vec2 uv) {
    float atlasGrid = 128.0;
    float tx = mod(float(textureId), atlasGrid);
    float ty = floor(float(textureId) / atlasGrid);

    vec2 base = vec2(tx, ty) / atlasGrid;
    vec2 tile = uv / atlasGrid;

    return base + tile;
}

void main() {
    vec2 uv = atlasUv(vTextureId, fract(vUv));
    vec4 color = texture(uAtlas, uv);

    if (color.a < 0.01) {
        discard;
    }

    fragColor = color;
}
