#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

uniform sampler2D InSampler;
uniform sampler2D PanelSampler;

layout(std140) uniform HudConfig {
    // Bar area inside the panel texture: x, y, width, height in panel pixels from the texture's top-left corner.
    vec4 BarRect;
    vec4 BarColor;
    // Distance of the panel from the right and bottom screen edges, in GUI pixels.
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
    ivec2 panelSize = textureSize(PanelSampler, 0);
    int scale = autoGuiScale(outSize);

    // Framebuffer pixel with the origin at the bottom-left corner of the screen.
    ivec2 pixel = ivec2(floor(texCoord * vec2(outSize)));
    // Bottom-left corner of the panel, pinned to the bottom-right of the screen.
    ivec2 origin = ivec2(outSize.x - (int(Margin.x) + panelSize.x) * scale, int(Margin.y) * scale);
    ivec2 offset = pixel - origin;
    if (any(lessThan(offset, ivec2(0))) || any(greaterThanEqual(offset, panelSize * scale))) {
        return;
    }

    // Panel texel in texture space (row 0 is the top row of the PNG).
    ivec2 local = offset / scale;
    ivec2 texel = ivec2(local.x, panelSize.y - 1 - local.y);
    vec4 color = texelFetch(PanelSampler, texel, 0);

    // GameTime is the fraction of the current day (24000 ticks); the bar refills every 100 ticks.
    float fill = fract(GameTime * 24000.0 / 100.0);
    vec2 bar = vec2(texel) - BarRect.xy;
    if (bar.x >= 0.0 && bar.y >= 0.0 && bar.x < BarRect.z && bar.y < BarRect.w && bar.x < floor(fill * BarRect.z + 0.5)) {
        float shade = 1.0 - 0.35 * (bar.y / max(BarRect.w - 1.0, 1.0));
        color = vec4(BarColor.rgb * shade, BarColor.a);
    }

    fragColor = vec4(mix(scene.rgb, color.rgb, color.a), 1.0);
}
