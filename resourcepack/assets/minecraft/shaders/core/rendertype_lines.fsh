#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

// ===== CONFIGURAÇÕES =====
#define RAINBOW_SPEED 2.0     // Velocidade do RGB (maior = mais rápido)
#define OUTLINE_ALPHA 1.0     // Opacidade (0.0 a 1.0)
// =========================

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    // Cor RGB animada
    float hue = fract(GameTime * RAINBOW_SPEED);
    vec3 rainbow = hsv2rgb(vec3(hue, 1.0, 1.0));
    
    vec4 color = vec4(rainbow, OUTLINE_ALPHA);
    
    // Aplica fog
    color = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, 
                      FogEnvironmentalStart, FogEnvironmentalEnd, 
                      FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);

#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
    fragColor = color;
#endif
}
