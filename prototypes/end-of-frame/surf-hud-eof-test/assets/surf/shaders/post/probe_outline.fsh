#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
uniform sampler2D OutlineSampler;

layout(std140) uniform ProbeConfig {
    // Size of the probe box as a fraction of the screen.
    vec2 BoxFraction;
    // Distance of the box from the left and top screen edges, in GUI pixels.
    vec2 Margin;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// Vanilla Window.calculateScale for GUI scale "Auto" (ignores the force-unicode-font rule).
int autoGuiScale(ivec2 size) {
    int scale = 1;
    while (scale < size.x && scale < size.y && size.x / (scale + 1) >= 320 && size.y / (scale + 1) >= 240) {
        scale++;
    }
    return scale;
}

void main() {
    vec4 scene = texture(InSampler, texCoord);
    fragColor = vec4(scene.rgb, 1.0);

    ivec2 outSize = textureSize(InSampler, 0);
    int scale = autoGuiScale(outSize);

    vec2 pixel = texCoord * vec2(outSize);
    vec2 boxSize = floor(vec2(outSize) * BoxFraction);
    // Top-left corner of the box; the framebuffer origin is bottom-left, so "top" is the high y edge.
    vec2 boxMin = vec2(Margin.x * scale, float(outSize.y) - Margin.y * scale - boxSize.y);
    vec2 local = pixel - boxMin;
    if (any(lessThan(local, vec2(0.0))) || any(greaterThanEqual(local, boxSize))) {
        return;
    }

    float border = float(scale);
    if (local.x < border || local.y < border || local.x >= boxSize.x - border || local.y >= boxSize.y - border) {
        fragColor = vec4(1.0, 0.85, 0.2, 1.0);
        return;
    }

    // The whole entity_outline target, scaled down into the box.
    vec4 outline = texture(OutlineSampler, local / boxSize);
    vec3 background = vec3(0.05, 0.05, 0.08);
    fragColor = vec4(mix(background, outline.rgb, clamp(outline.a, 0.0, 1.0)), 1.0);
}
