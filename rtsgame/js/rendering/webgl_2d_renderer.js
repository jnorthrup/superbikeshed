// webgl_2d_renderer.js
// This renderer provides a basic 2D rendering context using WebGL.
// It's designed to draw simple shapes like rectangles with specified colors.
export class WebGL2DRenderer {
    constructor(canvas) {
        this.canvas = canvas;
        this.gl = this.initWebGL();
        if (!this.gl) return;
        this.shaderProgram = this.initShaders();
        if (!this.shaderProgram) return;

        // Buffers for position and color data
        this.positionBuffer = this.gl.createBuffer();
        this.colorBuffer = this.gl.createBuffer();

        // Get attribute and uniform locations from the shader program
        this.attributes = {
            position: this.gl.getAttribLocation(this.shaderProgram, 'aPosition'),
            color: this.gl.getAttribLocation(this.shaderProgram, 'aColor'),
        };
        this.uniforms = {
            resolution: this.gl.getUniformLocation(this.shaderProgram, 'uResolution'),
        };

        // Activate the shader program and set the resolution uniform
        this.gl.useProgram(this.shaderProgram);
        this.gl.uniform2f(this.uniforms.resolution, canvas.width, canvas.height);
    }

    // Initialize WebGL context
    initWebGL() {
        const gl = this.canvas.getContext('webgl') || this.canvas.getContext('experimental-webgl');
        if (!gl) {
            console.error('WebGL not supported or failed to initialize.');
            return null;
        }
        gl.clearColor(0.2, 0.2, 0.2, 1.0); // Set clear color to dark gray
        gl.viewport(0, 0, gl.canvas.width, gl.canvas.height); // Set viewport to cover the entire canvas
        return gl;
    }

    // Initialize shaders (vertex and fragment shaders)
    initShaders() {
        const vertexShaderSource = `
            attribute vec2 aPosition;   // Input: position of the vertex
            attribute vec4 aColor;      // Input: color of the vertex
            uniform vec2 uResolution;   // Uniform: resolution of the canvas
            varying vec4 vColor;        // Output to fragment shader: interpolated color

            void main() {
                // Convert pixel coordinates to clip space coordinates (-1 to +1)
                // This transforms the rectangle from pixels to a normalized coordinate system
                vec2 zeroToOne = aPosition / uResolution;
                vec2 clipSpace = (zeroToOne * 2.0) - 1.0;

                // Set the final position of the vertex in clip space, flipping the Y-axis
                gl_Position = vec4(clipSpace * vec2(1, -1), 0, 1);
                vColor = aColor; // Pass the color to the fragment shader
            }
        `;

        const fragmentShaderSource = `
            precision mediump float;    // Set default precision for floats
            varying vec4 vColor;        // Input from vertex shader: interpolated color

            void main() {
                gl_FragColor = vColor; // Set the output color for the fragment
            }
        `;

        // Compile both shaders
        const vertexShader = this.compileShader(this.gl.VERTEX_SHADER, vertexShaderSource);
        const fragmentShader = this.compileShader(this.gl.FRAGMENT_SHADER, fragmentShaderSource);

        // Create and link the shader program
        const shaderProgram = this.gl.createProgram();
        this.gl.attachShader(shaderProgram, vertexShader);
        this.gl.attachShader(shaderProgram, fragmentShader);
        this.gl.linkProgram(shaderProgram);

        // Check for linking errors
        if (!this.gl.getProgramParameter(shaderProgram, this.gl.LINK_STATUS)) {
            console.error('Shader program linking failed:', this.gl.getProgramInfoLog(shaderProgram));
            return null;
        }
        return shaderProgram;
    }

    // Helper function to compile a single shader
    compileShader(type, source) {
        const shader = this.gl.createShader(type);
        this.gl.shaderSource(shader, source);
        this.gl.compileShader(shader);
        if (!this.gl.getShaderParameter(shader, this.gl.COMPILE_STATUS)) {
            console.error('Shader compilation failed:', this.gl.getShaderInfoLog(shader));
            this.gl.deleteShader(shader);
            return null;
        }
        return shader;
    }

    // Draws a filled rectangle (quad) with a single color
    drawRect(x, y, width, height, color) {
        if (!this.gl) return;

        // Calculate coordinates for the two triangles that form the rectangle
        const x1 = x;
        const y1 = y;
        const x2 = x + width;
        const y2 = y + height;

        const positions = [
            x1, y1, // Top-left
            x2, y1, // Top-right
            x1, y2, // Bottom-left
            x1, y2, // Bottom-left (repeated for second triangle)
            x2, y1, // Top-right (repeated for second triangle)
            x2, y2, // Bottom-right
        ];

        // Apply the same color to all vertices of the rectangle
        const colors = [
            ...color, ...color, ...color,
            ...color, ...color, ...color,
        ];

        // Bind position buffer and upload data
        this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.positionBuffer);
        this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(positions), this.gl.STATIC_DRAW);
        this.gl.vertexAttribPointer(this.attributes.position, 2, this.gl.FLOAT, false, 0, 0);
        this.gl.enableVertexAttribArray(this.attributes.position);

        // Bind color buffer and upload data
        this.gl.bindBuffer(this.gl.ARRAY_BUFFER, this.colorBuffer);
        this.gl.bufferData(this.gl.ARRAY_BUFFER, new Float32Array(colors), this.gl.STATIC_DRAW);
        this.gl.vertexAttribPointer(this.attributes.color, 4, this.gl.FLOAT, false, 0, 0);
        this.gl.enableVertexAttribArray(this.attributes.color);

        // Draw the 6 vertices as two triangles
        this.gl.drawArrays(this.gl.TRIANGLES, 0, 6);
    }

    // Clears the canvas
    clear() {
        if (!this.gl) return;
        this.gl.clear(this.gl.COLOR_BUFFER_BIT);
    }
}