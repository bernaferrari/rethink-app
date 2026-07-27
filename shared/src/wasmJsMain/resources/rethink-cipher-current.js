/*
 * Copyright 2026 RethinkDNS and its authors.
 *
 * Inspired by Paper Shaders' Dithering shader and QuietGuard's adaptation of it. Rewritten as a
 * "cipher current": loose packets become an ordered, encrypted stream when protection is active.
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
        uniform float u_phase;
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

        float bayer2(float x, float y) {
            return mod(2.0 * x + 3.0 * y, 4.0);
        }

        float bayer4(vec2 cell) {
            float low = bayer2(mod(cell.x, 2.0), mod(cell.y, 2.0));
            float high = bayer2(
                mod(floor(cell.x / 2.0), 2.0),
                mod(floor(cell.y / 2.0), 2.0)
            );
            return (4.0 * low + high) / 16.0;
        }

        void main() {
            const float tau = 6.28318530718;
            float theta = u_phase * tau;
            float enabledMix = clamp(u_active, 0.0, 1.0);
            float recoveringMix = clamp(u_recovering, 0.0, 1.0);
            float pixelSize = max(7.0, min(u_resolution.x, u_resolution.y) * 0.008);
            vec2 cell = floor(gl_FragCoord.xy / pixelSize);
            vec2 pixelCoord = (cell + 0.5) * pixelSize;
            vec2 uv = pixelCoord / u_resolution;
            float aspect = u_resolution.x / max(u_resolution.y, 1.0);
            vec2 p = uv - 0.5;
            p.x *= aspect;

            vec2 loop = vec2(cos(theta), sin(theta));
            float noise = snoise(p * 2.15 + loop * 0.52);
            float fineNoise = snoise(p * 5.2 - loop.yx * 0.31);

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
                    u_phase * 4.0 +
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

            vec3 inactiveInk = mix(vec3(0.80, 0.27, 0.31), u_field, 0.28);
            vec3 protectedInk = mix(u_accent, u_secondary, cipherWeave * 0.72);
            vec3 ink = mix(inactiveInk, protectedInk, enabledMix);
            ink = mix(ink, vec3(0.94, 0.34, 0.20), rejected * recoveringMix);

            float centerQuiet =
                0.12 + 0.88 * smoothstep(0.13, 0.37, length(p * vec2(0.8, 1.0)));
            float texture = 0.78 + 0.22 * (noise * 0.5 + 0.5);
            float alpha =
                mark *
                (0.060 + 0.060 * enabledMix) *
                centerQuiet *
                texture *
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
            state.uniforms = {
                resolution: gl.getUniformLocation(program, "u_resolution"),
                phase: gl.getUniformLocation(program, "u_phase"),
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
        gl.uniform1f(state.uniforms.phase, value.phase);
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
