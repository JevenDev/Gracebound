#version 150

#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float TrailTime;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float softBand(float value, float width) {
    return smoothstep(0.0, width, value) * (1.0 - smoothstep(1.0 - width, 1.0, value));
}

void main() {
    float progress = clamp(texCoord0.x, 0.0, 1.0);
    float style = floor(texCoord0.y + 0.001);
    float edge = fract(texCoord0.y) * 4.5;
    float center = 1.0 - clamp(abs(edge - 0.54) * 1.85, 0.0, 1.0);
    vec4 color = vertexColor * ColorModulator;

    if (style < 0.5) {
        float ember = sin(progress * 34.0 - TrailTime * 18.0) * 0.5 + 0.5;
        float vein = smoothstep(0.68, 1.0, ember) * center;
        color.rgb += vec3(0.24, 0.12, 0.02) * vein;
        color.a *= 0.78 + 0.22 * center;
    } else if (style < 1.5) {
        float braid = sin(progress * 42.0 + edge * 8.0 - TrailTime * 24.0);
        float script = softBand(fract(progress * 12.0 - TrailTime * 1.45 + edge * 0.08), 0.18);
        float rune = smoothstep(0.72, 1.0, sin(progress * 127.0 - TrailTime * 21.0 + edge * 3.0) * 0.5 + 0.5);
        float spark = smoothstep(0.84, 1.0, sin(progress * 53.0 + TrailTime * 18.0 + texCoord0.y * 11.0) * 0.5 + 0.5);
        vec3 teal = vec3(0.34, 1.0, 0.92);
        vec3 violet = vec3(0.78, 0.42, 1.0);
        vec3 gold = vec3(1.0, 0.86, 0.34);
        color.rgb = mix(color.rgb, mix(teal, violet, braid * 0.5 + 0.5), 0.5);
        color.rgb += gold * (rune * center * 0.36 + script * 0.22 + spark * 0.18);
        color.a *= 0.58 + center * 0.5 + script * 0.18;
    } else if (style < 2.5) {
        float wave = sin(progress * 18.0 + edge * 2.4 - TrailTime * 12.0) * 0.5 + 0.5;
        float curtain = softBand(fract(progress * 5.0 - TrailTime * 0.8 + edge * 0.12), 0.28);
        vec3 green = vec3(0.18, 1.0, 0.62);
        vec3 blue = vec3(0.2, 0.72, 1.0);
        vec3 rose = vec3(1.0, 0.48, 0.74);
        color.rgb = mix(mix(green, blue, wave), rose, curtain * 0.34);
        color.rgb += vec3(0.38, 0.72, 1.0) * center * curtain * 0.32;
        color.a *= (0.48 + center * 0.48) * (0.78 + curtain * 0.28);
    } else {
        float cell = fract(progress * 11.0 - TrailTime * 1.35);
        float cut = step(0.16, cell) * (1.0 - step(0.82, cell));
        float snap = step(0.5, fract(texCoord0.y * 8.0 + progress * 3.0));
        float stroke = max(softBand(cell, 0.1), snap * 0.38);
        vec3 ink = vec3(0.02, 0.07, 0.08);
        vec3 cyan = vec3(0.22, 1.0, 0.9);
        vec3 bone = vec3(1.0, 0.96, 0.62);
        color.rgb = mix(ink, mix(cyan, bone, stroke), 0.72 + stroke * 0.28);
        color.rgb += bone * smoothstep(0.76, 1.0, sin(progress * 47.0 + TrailTime * 9.0) * 0.5 + 0.5) * 0.2;
        color.a *= cut * (0.42 + stroke * 0.68);
    }

    fragColor = color * linear_fog_fade(vertexDistance, FogStart, FogEnd);
}
