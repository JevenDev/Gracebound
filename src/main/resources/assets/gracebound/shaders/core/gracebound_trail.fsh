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
        float rune = smoothstep(0.74, 1.0, sin(progress * 91.0 - TrailTime * 15.0) * 0.5 + 0.5);
        vec3 teal = vec3(0.42, 1.0, 0.92);
        vec3 violet = vec3(0.8, 0.46, 1.0);
        vec3 gold = vec3(1.0, 0.82, 0.38);
        color.rgb = mix(color.rgb, mix(teal, violet, braid * 0.5 + 0.5), 0.38);
        color.rgb += gold * rune * center * 0.26;
        color.a *= 0.66 + center * 0.44;
    } else {
        float wave = sin(progress * 18.0 + edge * 2.4 - TrailTime * 12.0) * 0.5 + 0.5;
        float curtain = softBand(fract(progress * 5.0 - TrailTime * 0.8 + edge * 0.12), 0.28);
        vec3 green = vec3(0.18, 1.0, 0.62);
        vec3 blue = vec3(0.2, 0.72, 1.0);
        vec3 rose = vec3(1.0, 0.48, 0.74);
        color.rgb = mix(mix(green, blue, wave), rose, curtain * 0.34);
        color.rgb += vec3(0.38, 0.72, 1.0) * center * curtain * 0.32;
        color.a *= (0.48 + center * 0.48) * (0.78 + curtain * 0.28);
    }

    fragColor = color * linear_fog_fade(vertexDistance, FogStart, FogEnd);
}
