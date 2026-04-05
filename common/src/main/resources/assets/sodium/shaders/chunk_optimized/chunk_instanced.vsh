#version 430 core

// Quad base em triangle strip: 4 índices (0,1,2,3)
const vec2 BASE_POS[4] = vec2[4](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(0.0, 1.0),
    vec2(1.0, 1.0)
);

layout(std430, binding = 0) readonly buffer FaceInstances {
    // packedBase: posição base + normal + textura
    // packedSize: sizeU nos 16 bits baixos, sizeV nos 16 altos
    uvec2 faces[];
};

uniform mat4 uModelView;
uniform mat4 uProjection;

out vec2 vUv;
flat out uint vTextureId;

uvec3 decodePos(uint packed) {
    return uvec3((packed >> 0u) & 31u, (packed >> 5u) & 31u, (packed >> 10u) & 31u);
}

uint decodeNormalId(uint packed) {
    return (packed >> 15u) & 7u;
}

uint decodeTextureId(uint packed) {
    return (packed >> 18u) & 16383u;
}

vec3 normalFromId(uint id) {
    if (id == 0u) return vec3(0.0, -1.0, 0.0);
    if (id == 1u) return vec3(0.0, 1.0, 0.0);
    if (id == 2u) return vec3(0.0, 0.0, -1.0);
    if (id == 3u) return vec3(0.0, 0.0, 1.0);
    if (id == 4u) return vec3(-1.0, 0.0, 0.0);
    return vec3(1.0, 0.0, 0.0);
}

void main() {
    uvec2 face = faces[gl_BaseInstance + gl_InstanceID];
    uint packedBase = face.x;
    uint packedSize = face.y;

    vec3 base = vec3(decodePos(packedBase));
    uint normalId = decodeNormalId(packedBase);
    vTextureId = decodeTextureId(packedBase);

    float sizeU = float(packedSize & 0xFFFFu);
    float sizeV = float((packedSize >> 16u) & 0xFFFFu);

    vec2 corner = BASE_POS[gl_VertexID];
    vec3 normal = normalFromId(normalId);

    vec3 tangent;
    vec3 bitangent;

    if (abs(normal.y) > 0.5) {
        tangent = vec3(1.0, 0.0, 0.0);
        bitangent = vec3(0.0, 0.0, 1.0);
    } else if (abs(normal.x) > 0.5) {
        tangent = vec3(0.0, 0.0, 1.0);
        bitangent = vec3(0.0, 1.0, 0.0);
    } else {
        tangent = vec3(1.0, 0.0, 0.0);
        bitangent = vec3(0.0, 1.0, 0.0);
    }

    vec3 worldPos = base + tangent * (corner.x * sizeU) + bitangent * (corner.y * sizeV);

    gl_Position = uProjection * uModelView * vec4(worldPos, 1.0);
    vUv = corner;
}
