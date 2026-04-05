#version 430 core

layout(location = 0) in vec2 aQuad;

layout(std430, binding = 3) readonly buffer FaceInstances {
    uvec3 instances[];
};

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;
flat out uint vTextureId;

uvec4 decodePackedVertex(uint packed) {
    uint x = (packed >> 0u) & 31u;
    uint y = (packed >> 5u) & 31u;
    uint z = (packed >> 10u) & 31u;
    uint n = (packed >> 15u) & 7u;
    return uvec4(x, y, z, n);
}

uvec2 decodeExtent(uint packed) {
    uint w = packed & 0xFFFFu;
    uint h = (packed >> 16u) & 0xFFFFu;
    return uvec2(w, h);
}

vec3 buildOffset(uint face, vec2 quad, uvec2 extent) {
    vec2 scale = vec2(extent);

    if (face == 0u || face == 1u) {
        return vec3(quad.x * scale.x, 0.0, quad.y * scale.y);
    }

    if (face == 2u || face == 3u) {
        return vec3(quad.x * scale.x, quad.y * scale.y, 0.0);
    }

    return vec3(0.0, quad.y * scale.y, quad.x * scale.x);
}

void main() {
    uvec3 entry = instances[gl_InstanceID];

    uvec4 decoded = decodePackedVertex(entry.x);
    uvec2 extent = decodeExtent(entry.y);

    vec3 base = vec3(decoded.xyz);
    vec3 localOffset = buildOffset(decoded.w, aQuad, extent);
    vec3 worldPos = base + localOffset;

    vUv = aQuad * vec2(extent);
    vTextureId = (entry.x >> 18u) & 0x3FFFu;

    gl_Position = ProjMat * ModelViewMat * vec4(worldPos, 1.0);
}
