# RTS Game - Consolidated Documentation

## Project Overview
**Status**: Active development  
**Type**: Kotlin Multiplatform real-time strategy game  
**Purpose**: Cross-platform RTS game with WebGPU rendering and SpaceGraph integration

## Core Technology Stack
- **Kotlin Multiplatform** - JVM, Web (WasmJs), Native platforms
- **WebGPU** - Modern graphics API for high-performance rendering
- **SpaceGraph** - Entity relationship visualization
- **TrikeShed Type System** - Robust data modeling

## Key Features
- **Real-time Simulation** - Entities move, interact, and battle in real-time
- **Interactive Demo** - Control panel with start/stop, add units, reset, zoom
- **Cross-platform Compatibility** - Unified experience across targets
- **Modern Graphics** - WebGPU rendering for high performance
- **Entity Visualization** - SpaceGraph integration for game state

## Project Structure
```
rtsgame/
├── kotlin/                 # Shared Kotlin module
├── src/                    # Main project source sets
│   ├── commonMain/kotlin/  # Code common to all platforms
│   ├── jvmMain/kotlin/     # JVM-specific code
│   ├── wasmJsMain/kotlin/  # WasmJs-specific code
│   └── nativeMain/kotlin/  # Platform-native code
├── interactive-demo.html   # Main interactive demo file
└── build.gradle.kts        # Main project build script
```

## Development Commands
```bash
# Build all targets
./gradlew buildAll

# Run JVM version
./gradlew runJvm

# Run WasmJs browser version
./gradlew runWasm

# Clean all build artifacts
./gradlew cleanAll
```

## GitHub Pages Deployment
The interactive demo is deployed to GitHub Pages at:
`https://yourusername.github.io/yourrepositoryname/interactive-demo.html`

### Deployment Files:
1. `interactive-demo.html` - Main HTML file
2. `build/js/packages/rtsgame/kotlin/` - Compiled WasmJs artifacts

### Deployment Process:
```bash
./gradlew buildAll
git add interactive-demo.html
git add build/js/packages/rtsgame/kotlin/
git commit -m "🎮 Deploy Interactive RTS WebGPU Demo"
git push origin gh-pages
```

## Documentation Status
- **Total Files**: 15 (4 root level, 11 in docs/)
- **Large Files**: 5 files >10KB (architecture, implementation guides)
- **Consolidation**: Completed June 24, 2025
- **Status**: All phases completed successfully

## Architecture Highlights
- **Multiplatform Architecture** - Shared codebase across platforms
- **WebGPU Integration** - Modern graphics pipeline
- **SpaceGraph Visualization** - Entity relationship mapping
- **Real-time Game Loop** - Continuous simulation with user interaction
- **Interactive Controls** - Web-based control panel for demo

## Contributing
1. Fork the repository
2. Create a feature branch
3. Ensure multiplatform compatibility
4. Submit a pull request

## License
MIT

---
*Last Updated: June 24, 2025*  
*Documentation Status: Consolidated* 