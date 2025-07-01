#!/bin/bash

echo "Building RTS Game WASM..."

# Create build directory
mkdir -p build/wasm

# Copy resources
cp -r src/wasmJsMain/resources/* build/wasm/

# Note: The actual Kotlin/WASM compilation would be done by gradle
# For now, create a placeholder to show the build structure

cat > build/wasm/rtsgame.js << 'EOF'
// This would be the compiled Kotlin/WASM output
console.log("RTS Game WASM Module Loaded");

// Placeholder for Kotlin compiled code
window.rtsgame = {
    main: function() {
        console.log("Starting RTS Game...");
        document.getElementById('loading').style.display = 'none';
        document.getElementById('gameCanvas').style.display = 'block';
    }
};

// Auto-start
window.addEventListener('load', function() {
    window.rtsgame.main();
});
EOF

echo "Build complete! Output in build/wasm/"
echo ""
echo "To run locally:"
echo "  cd build/wasm"
echo "  python3 -m http.server 8080"
echo ""
echo "Then open http://localhost:8080 in a WebGPU-enabled browser"