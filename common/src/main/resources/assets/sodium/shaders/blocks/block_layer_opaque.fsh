#version 460 core

#include <sodium:globals.glsl>
#include <sodium:fog.glsl>
#include <sodium:chunk_material.glsl>
#include <minecraft:oit.glsl>

layout(location = 0) in vec4 v_Color; // The interpolated vertex color
layout(location = 1) in vec2 v_TexCoord; // The interpolated block texture coordinates
layout(location = 2) in vec2 v_FragDistance; // The fragment's distance from the camera (cylindrical and spherical)
layout(location = 3) in float fadeFactor;

uniform sampler2D u_BlockTex; // The block texture

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor; // The output fragment for the color framebuffer
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
        color = sampleColorForAccumulation(color);
        vec4 fogColor = vec4(u_FogColor.rgb * color.a, u_FogColor.a);
    #else
        vec4 fogColor = u_FogColor;
    #endif

    #ifdef OIT_ALPHA_ONLY
    float factor = 1.0;
    #else
    float factor = fadeFactor;
    #endif

    return _linearFog(color, v_FragDistance, fogColor, u_EnvironmentFog, u_RenderFog, factor);
}

void main() {
    // Plain hardware sampling: no derivatives, textureGrad or supersampling, which is cheap on every renderer
    vec4 color = texture(u_BlockTex, v_TexCoord);
    color *= v_Color; // Apply per-vertex color modulator

#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    fragColor = calculateFinalColor(color);
    #endif
}