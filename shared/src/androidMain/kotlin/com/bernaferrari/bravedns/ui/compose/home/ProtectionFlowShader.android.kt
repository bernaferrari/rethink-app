/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.home

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas

/**
 * GPU "cipher current" for Android 13+. Loose packets become an ordered encrypted stream as
 * protection activates; outliers remain outside the tunnel and turn warm while recovering.
 *
 * The simplex field and Bayer thresholding are adapted from Paper Shaders' Dithering shader,
 * with dedicated geometry and state behavior.
 *
 * @see <a href="https://github.com/paper-design/shaders">Paper Shaders</a>
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun ProtectionFlowShader(
    phase: Float,
    activeProgress: Float,
    recoveringProgress: Float,
    accent: Color,
    secondary: Color,
    field: Color,
    surface: Color,
    opacity: Float,
    modifier: Modifier = Modifier,
) {
    val shader = remember {
        runCatching { RuntimeShader(PROTECTION_FLOW_AGSL) }
            .onFailure { Log.w("ProtectionFlowShader", "AGSL unavailable: ${it.message}") }
            .getOrNull()
    }
    if (shader == null) {
        ProtectionFlowCanvas(
            phase,
            activeProgress,
            recoveringProgress,
            accent,
            secondary,
            field,
            surface,
            opacity,
            modifier,
        )
        return
    }

    val paint = remember { Paint().apply { isAntiAlias = false } }
    Canvas(modifier = modifier) {
        shader.setFloatUniform("iResolution", size.width, size.height)
        shader.setFloatUniform("iPhase", phase)
        shader.setFloatUniform("uActive", activeProgress.coerceIn(0f, 1f))
        shader.setFloatUniform("uRecovering", recoveringProgress.coerceIn(0f, 1f))
        shader.setFloatUniform("uOpacity", opacity.coerceIn(0f, 1f))
        shader.setFloatUniform("uAccent", accent.red, accent.green, accent.blue, accent.alpha)
        shader.setFloatUniform(
            "uSecondary",
            secondary.red,
            secondary.green,
            secondary.blue,
            secondary.alpha,
        )
        shader.setFloatUniform("uField", field.red, field.green, field.blue, field.alpha)
        shader.setFloatUniform("uSurface", surface.red, surface.green, surface.blue, surface.alpha)
        paint.shader = shader
        drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}

private const val PROTECTION_FLOW_AGSL =
    """
uniform float2 iResolution;
uniform float iPhase;
uniform float uActive;
uniform float uRecovering;
uniform float uOpacity;
uniform half4 uAccent;
uniform half4 uSecondary;
uniform half4 uField;
uniform half4 uSurface;

half3 blend3(half3 a, half3 b, float t) {
    return a + (b - a) * half(t);
}

float3 permute(float3 x) {
    return mod(((x * 34.0) + 1.0) * x, 289.0);
}

float snoise(float2 v) {
    const float4 C = float4(
        0.211324865405187,
        0.366025403784439,
        -0.577350269189626,
        0.024390243902439
    );
    float2 i = floor(v + dot(v, C.yy));
    float2 x0 = v - i + dot(i, C.xx);
    float2 i1 = x0.x > x0.y ? float2(1.0, 0.0) : float2(0.0, 1.0);
    float4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;
    i = mod(i, 289.0);
    float3 p = permute(
        permute(i.y + float3(0.0, i1.y, 1.0)) +
            i.x + float3(0.0, i1.x, 1.0)
    );
    float3 m = max(
        0.5 - float3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)),
        0.0
    );
    m = m * m;
    m = m * m;
    float3 x = 2.0 * fract(p * C.www) - 1.0;
    float3 h = abs(x) - 0.5;
    float3 ox = floor(x + 0.5);
    float3 a0 = x - ox;
    m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
    float3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
}

float bayer2(float x, float y) {
    return mod(2.0 * x + 3.0 * y, 4.0);
}

float bayer4(float2 cell) {
    float low = bayer2(mod(cell.x, 2.0), mod(cell.y, 2.0));
    float high = bayer2(
        mod(floor(cell.x / 2.0), 2.0),
        mod(floor(cell.y / 2.0), 2.0)
    );
    return (4.0 * low + high) / 16.0;
}

half4 main(float2 fragCoord) {
    float tau = 6.2831853;
    float theta = iPhase * tau;
    float enabledMix = clamp(uActive, 0.0, 1.0);
    float recoveringMix = clamp(uRecovering, 0.0, 1.0);
    float pixelSize = max(7.0, min(iResolution.x, iResolution.y) * 0.008);
    float2 cell = floor(fragCoord / pixelSize);
    float2 pixelCoord = (cell + 0.5) * pixelSize;
    float2 uv = pixelCoord / iResolution;
    float aspect = iResolution.x / max(iResolution.y, 1.0);
    float2 p = uv - 0.5;
    p.x *= aspect;

    float2 orbit = float2(cos(theta), sin(theta));
    float noise = snoise(p * 2.15 + orbit * 0.52);
    float fineNoise = snoise(p * 5.2 - orbit.yx * 0.31);
    float centerline =
        0.15 * sin(p.x * 2.7 + noise * 0.72) +
        0.045 * sin(p.x * 7.0 - theta * 2.0);
    float fromCurrent = abs(p.y - centerline);
    float currentWidth = 0.19 + 0.025 * sin(p.x * 5.0 + theta);
    float currentBody = 1.0 - smoothstep(currentWidth, currentWidth + 0.25, fromCurrent);
    float guardRails = 1.0 - smoothstep(
        0.012,
        0.045,
        abs(fromCurrent - currentWidth)
    );

    float laneSeed = floor(cell.y / 3.0) * 0.071;
    float packetPhase = fract(
        (p.x / max(aspect, 0.001) + 0.5) * 4.0 -
            iPhase * 4.0 +
            laneSeed +
            noise * 0.055
    );
    float packet = 1.0 - smoothstep(0.055, 0.19, abs(packetPhase - 0.5));
    float cipherWeave = 0.5 + 0.5 * sin(
        (p.y - centerline) * 43.0 + p.x * 6.0 + fineNoise * 0.7
    );
    float ordered =
        currentBody * (0.18 + packet * 0.55 + cipherWeave * 0.18) +
        guardRails * 0.72;
    float scattered = 0.16 + (noise * 0.5 + 0.5) * 0.44;
    float rejected =
        (1.0 - currentBody) *
        smoothstep(0.64, 0.86, fineNoise * 0.5 + 0.5) *
        packet;
    float shape = mix(scattered, ordered, enabledMix);
    shape = max(shape, rejected * (0.38 + 0.42 * recoveringMix));
    float mark = step(bayer4(cell), shape);

    half3 inactiveInk = blend3(half3(0.80, 0.27, 0.31), uField.rgb, 0.28);
    half3 protectedInk = blend3(uAccent.rgb, uSecondary.rgb, cipherWeave * 0.72);
    half3 ink = blend3(inactiveInk, protectedInk, enabledMix);
    ink = blend3(ink, half3(0.94, 0.34, 0.20), rejected * recoveringMix);
    float centerQuiet =
        0.12 + 0.88 * smoothstep(0.13, 0.37, length(p * float2(0.8, 1.0)));
    float texture = 0.78 + 0.22 * (noise * 0.5 + 0.5);
    float alpha =
        mark *
        (0.060 + 0.060 * enabledMix) *
        centerQuiet *
        texture *
        clamp(uOpacity, 0.0, 1.0);
    half3 color = blend3(uSurface.rgb, ink, alpha);
    return half4(color, 1.0);
}
"""
