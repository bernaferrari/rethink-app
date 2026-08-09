/*
 * Copyright 2026 RethinkDNS and its authors.
 *
 * Inspired by the contour motion of Paper Shaders' Waves and Simplex Noise effects. Rewritten as
 * Rethink's own "signal loom": encrypted currents cross, align, and keep flowing as protection
 * becomes active.
 * Paper Shaders: https://github.com/paper-design/shaders
 */
(() => {
    const vertexSource = `#version 300 es
        precision highp float;
        layout(location = 0) in vec2 a_position;
        out vec2 v_uv;
        void main() {
            v_uv = a_position * 0.5 + 0.5;
            gl_Position = vec4(a_position, 0.0, 1.0);
        }
    `;

    const fragmentSource = `#version 300 es
        precision highp float;

        uniform vec2 u_resolution;
        uniform float u_time;
        uniform float u_active;
        uniform float u_recovering;
        uniform float u_opacity;
        uniform vec3 u_accent;
        uniform vec3 u_secondary;
        uniform vec3 u_field;

        in vec2 v_uv;
        out vec4 fragColor;

        vec3 permute(vec3 x) {
            return mod(((x * 34.0) + 1.0) * x, 289.0);
        }

        float snoise(vec2 v) {
            const vec4 C = vec4(
                0.211324865405187,
                0.366025403784439,
                -0.577350269189626,
                0.024390243902439
            );
            vec2 i = floor(v + dot(v, C.yy));
            vec2 x0 = v - i + dot(i, C.xx);
            vec2 i1 = x0.x > x0.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
            vec4 x12 = x0.xyxy + C.xxzz;
            x12.xy -= i1;
            i = mod(i, 289.0);
            vec3 p = permute(
                permute(i.y + vec3(0.0, i1.y, 1.0)) +
                    i.x + vec3(0.0, i1.x, 1.0)
            );
            vec3 m = max(
                0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)),
                0.0
            );
            m = m * m;
            m = m * m;
            vec3 x = 2.0 * fract(p * C.www) - 1.0;
            vec3 h = abs(x) - 0.5;
            vec3 ox = floor(x + 0.5);
            vec3 a0 = x - ox;
            m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
            vec3 g;
            g.x = a0.x * x0.x + h.x * x0.y;
            g.yz = a0.yz * x12.xz + h.yz * x12.yw;
            return 130.0 * dot(m, g);
        }

        float contour(float value, float width) {
            float ridge = abs(fract(value) - 0.5);
            return 1.0 - smoothstep(width, width + 0.055, ridge);
        }

        float roundedBoxSdf(vec2 point, vec2 halfSize, float radius) {
            vec2 edge = abs(point) - halfSize + radius;
            return min(max(edge.x, edge.y), 0.0) +
                length(max(edge, 0.0)) -
                radius;
        }

        void main() {
            float enabledMix = clamp(u_active, 0.0, 1.0);
            float recoveringMix = clamp(u_recovering, 0.0, 1.0);
            vec2 uv = gl_FragCoord.xy / u_resolution;
            float aspect = u_resolution.x / max(u_resolution.y, 1.0);
            vec2 p = uv - 0.5;
            p.x *= aspect;

            // Independent frequencies keep the field coherent without returning to one short
            // master loop. Their combined cycle is measured in days, not seconds.
            float thetaA = u_time * 0.07137;
            float thetaB = u_time * 0.04311;
            float thetaC = u_time * 0.02653;
            vec2 loopA = vec2(cos(thetaA), sin(thetaA));
            vec2 loopB = vec2(
                cos(thetaB + 2.09439510239),
                sin(thetaB + 2.09439510239)
            );
            float broadNoise = snoise(p * 1.42 + loopA * 0.47);
            float detailNoise = snoise(
                mat2(0.82, -0.57, 0.57, 0.82) * p * 3.05 + loopB * 0.31
            );

            float longWave =
                0.14 * sin(p.x * 2.35 + loopA.x * 1.15) +
                0.055 * sin(p.x * 5.4 - loopA.y * 0.85);
            float currentField =
                p.y + longWave + broadNoise * 0.115 + detailNoise * 0.025;
            float crossField =
                p.x * 0.62 -
                p.y * 0.18 +
                broadNoise * 0.095 +
                0.045 * sin(p.y * 5.2 + loopB.x);

            float primaryRibbons = contour(
                currentField * 5.0 + u_time * 0.01271,
                mix(0.405, 0.365, enabledMix)
            );
            float secondaryRibbons = contour(
                crossField * 6.2 - u_time * 0.00833,
                0.438
            );
            float fineThread = contour(
                currentField * 10.0 -
                    detailNoise * 0.14 -
                    u_time * 0.01789 +
                    sin(thetaC) * 0.11,
                0.472
            );

            float weaveGate =
                0.5 + 0.5 * sin((currentField - crossField) * 15.0);
            float weave =
                primaryRibbons * (0.56 + 0.44 * weaveGate) +
                secondaryRibbons * (0.24 + 0.28 * (1.0 - weaveGate)) * enabledMix +
                fineThread * (0.10 + 0.12 * enabledMix);
            weave = clamp(weave, 0.0, 1.0);

            float looseSignal =
                0.5 + 0.5 * sin(
                    p.x * 3.1 - p.y * 4.0 + broadNoise * 2.0 + loopA.x
                );
            float signal = mix(looseSignal * 0.42, weave, 0.40 + enabledMix * 0.60);

            // Low-pass the procedural detail behind the central tunnel. This is the shader
            // equivalent of frosted glass: the current remains alive, but loses sharp contrast
            // before it reaches the status control.
            vec2 tunnelPoint = vec2(p.x, (1.0 - uv.y) - 0.34);
            float tunnelDistance = roundedBoxSdf(
                tunnelPoint,
                vec2(0.078, 0.098),
                0.035
            );
            float frostMask = 1.0 - smoothstep(-0.012, 0.055, tunnelDistance);
            float frostedSignal = 0.27 + broadNoise * 0.045;
            float frostStrength = mix(0.84, 0.58, enabledMix);
            signal = mix(signal, frostedSignal, frostMask * frostStrength);

            vec3 inactiveInk = mix(vec3(0.82, 0.29, 0.33), u_field, 0.22);
            float colorFlow =
                0.5 + 0.5 * sin(currentField * 8.0 + crossField * 3.0 + loopB.y);
            vec3 protectedInk = mix(u_accent, u_secondary, colorFlow);
            vec3 ink = mix(inactiveInk, protectedInk, enabledMix);

            float recoveryPulse =
                recoveringMix *
                contour(currentField * 4.0 + u_time * 0.01109, 0.43);
            ink = mix(ink, vec3(0.94, 0.34, 0.20), recoveryPulse * 0.70);

            float edgePresence =
                0.74 + 0.26 * smoothstep(0.05, 0.62, abs(p.x));
            float microTexture =
                0.94 + 0.06 * sin(
                    gl_FragCoord.x * 0.37 + gl_FragCoord.y * 0.29
                );
            float alpha =
                (0.030 + signal * (0.105 + 0.055 * enabledMix)) *
                edgePresence *
                microTexture *
                clamp(u_opacity, 0.0, 1.0);
            fragColor = vec4(ink, alpha);
        }
    `;

    const state = {
        mounted: false,
        canvas: null,
        gl: null,
        program: null,
        vao: null,
        frameRequest: 0,
        startTime: 0,
        values: null,
        uniforms: null,
    };

    function compile(gl, type, source) {
        const shader = gl.createShader(type);
        gl.shaderSource(shader, source);
        gl.compileShader(shader);
        if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
            const message = gl.getShaderInfoLog(shader);
            gl.deleteShader(shader);
            throw new Error(`Rethink cipher shader failed to compile: ${message}`);
        }
        return shader;
    }

    function createProgram(gl) {
        const vertex = compile(gl, gl.VERTEX_SHADER, vertexSource);
        const fragment = compile(gl, gl.FRAGMENT_SHADER, fragmentSource);
        const program = gl.createProgram();
        gl.attachShader(program, vertex);
        gl.attachShader(program, fragment);
        gl.linkProgram(program);
        gl.deleteShader(vertex);
        gl.deleteShader(fragment);
        if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
            const message = gl.getProgramInfoLog(program);
            gl.deleteProgram(program);
            throw new Error(`Rethink cipher shader failed to link: ${message}`);
        }
        return program;
    }

    function prepareComposeCanvas() {
        document.querySelectorAll("body > canvas:not(#rethink-flow-canvas)").forEach((canvas) => {
            canvas.style.position = "relative";
            canvas.style.zIndex = "1";
            canvas.style.background = "transparent";
        });
    }

    function ensureRenderer() {
        if (state.gl) return true;

        const canvas = document.createElement("canvas");
        canvas.id = "rethink-flow-canvas";
        canvas.setAttribute("aria-hidden", "true");
        Object.assign(canvas.style, {
            position: "fixed",
            inset: "0",
            width: "100vw",
            height: "100vh",
            pointerEvents: "none",
            zIndex: "2",
        });
        document.body.prepend(canvas);
        prepareComposeCanvas();

        const gl = canvas.getContext("webgl2", {
            alpha: true,
            antialias: false,
            depth: false,
            stencil: false,
            premultipliedAlpha: false,
            powerPreference: "high-performance",
        });
        if (!gl) {
            canvas.remove();
            return false;
        }

        try {
            const program = createProgram(gl);
            const vao = gl.createVertexArray();
            const buffer = gl.createBuffer();
            gl.bindVertexArray(vao);
            gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
            gl.bufferData(
                gl.ARRAY_BUFFER,
                new Float32Array([-1, -1, 3, -1, -1, 3]),
                gl.STATIC_DRAW,
            );
            gl.enableVertexAttribArray(0);
            gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
            gl.bindVertexArray(null);

            state.canvas = canvas;
            state.gl = gl;
            state.program = program;
            state.vao = vao;
            state.startTime = performance.now();
            state.uniforms = {
                resolution: gl.getUniformLocation(program, "u_resolution"),
                time: gl.getUniformLocation(program, "u_time"),
                active: gl.getUniformLocation(program, "u_active"),
                recovering: gl.getUniformLocation(program, "u_recovering"),
                opacity: gl.getUniformLocation(program, "u_opacity"),
                accent: gl.getUniformLocation(program, "u_accent"),
                secondary: gl.getUniformLocation(program, "u_secondary"),
                field: gl.getUniformLocation(program, "u_field"),
            };
            return true;
        } catch (error) {
            console.error(error);
            canvas.remove();
            return false;
        }
    }

    function resize() {
        const pixelRatio = Math.min(window.devicePixelRatio || 1, 1.5);
        const width = Math.max(1, Math.round(window.innerWidth * pixelRatio));
        const height = Math.max(1, Math.round(window.innerHeight * pixelRatio));
        if (state.canvas.width !== width || state.canvas.height !== height) {
            state.canvas.width = width;
            state.canvas.height = height;
        }
        state.gl.viewport(0, 0, width, height);
    }

    function draw() {
        state.frameRequest = 0;
        if (!state.mounted || !state.values || !state.gl) return;

        resize();
        prepareComposeCanvas();
        const gl = state.gl;
        const value = state.values;
        gl.useProgram(state.program);
        gl.bindVertexArray(state.vao);
        gl.uniform2f(state.uniforms.resolution, state.canvas.width, state.canvas.height);
        gl.uniform1f(
            state.uniforms.time,
            (performance.now() - state.startTime) / 1000,
        );
        gl.uniform1f(state.uniforms.active, value.active);
        gl.uniform1f(state.uniforms.recovering, value.recovering);
        gl.uniform1f(state.uniforms.opacity, value.opacity);
        gl.uniform3f(state.uniforms.accent, value.ar, value.ag, value.ab);
        gl.uniform3f(state.uniforms.secondary, value.sr, value.sg, value.sb);
        gl.uniform3f(state.uniforms.field, value.fr, value.fg, value.fb);
        gl.drawArrays(gl.TRIANGLES, 0, 3);
        gl.bindVertexArray(null);
    }

    function scheduleDraw() {
        if (!state.frameRequest) state.frameRequest = window.requestAnimationFrame(draw);
    }

    globalThis.rethinkProtectionFlow = {
        mount() {
            state.mounted = ensureRenderer();
            if (state.canvas) state.canvas.style.display = state.mounted ? "block" : "none";
            scheduleDraw();
        },
        unmount() {
            state.mounted = false;
            if (state.frameRequest) {
                window.cancelAnimationFrame(state.frameRequest);
                state.frameRequest = 0;
            }
            if (state.canvas) state.canvas.style.display = "none";
        },
        update(
            phase,
            active,
            recovering,
            ar,
            ag,
            ab,
            sr,
            sg,
            sb,
            fr,
            fg,
            fb,
            br,
            bg,
            bb,
            opacity,
        ) {
            state.values = {
                phase,
                active,
                recovering,
                ar,
                ag,
                ab,
                sr,
                sg,
                sb,
                fr,
                fg,
                fb,
                br,
                bg,
                bb,
                opacity,
            };
            scheduleDraw();
        },
    };
})();
