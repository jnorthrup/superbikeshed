# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Essential Development Commands

### Setup & Installation
```bash
npm install                    # Install dependencies
npm run preprocess:models     # Convert and optimize 3D models (OBJ to GLB)
```

### Development Workflow
```bash
npm run dev                   # Start development server with model preprocessing
npm start                     # Alternative development command
npm run build                 # Production build
```

### Development Server
- **Port**: 9002 (configured in webpack.config.mjs)
- **Hot Reload**: Enabled for development
- **Model Processing**: Automatic OBJ to GLB conversion and optimization

## Core Architecture

### TrikeShed Data Architecture
- **Location**: `src/trikeshed/core.ts`
- **Purpose**: Deterministic tensor-based data system for immutable state management
- **Key Types**: `Join<A,B>`, `Twin<T>`, `Tensor`, `Series`
- **Critical for**: Replay capability, deterministic simulation, cache-optimized data structures

### Dual Engine Approach
1. **Modern Simulation** (`js/core/simulation.js`): EntityManager-based with async initialization
2. **High-Performance GameEngine** (`js/core/gameEngine.js`): Spatial indexing and batch processing

### Key Entry Points
- `index.html` - Main HTML with canvas and UI elements
- `js/app.js` - Primary application entry point, initializes core systems
- `js/main.js` - Alternative entry for cache-stratified engine

### Performance Architecture
- **Spatial Indexing**: 64x64 world regions for cache locality (`js/core/spatialIndex.js`)
- **Batch Processing**: 64-entity batches optimized for L1 cache (`js/core/batchProcessor.js`)
- **Memory Stratification**: Hot/warm/cold data separation for optimal cache performance
- **Deterministic RNG**: All randomness uses seeded generators for replay capability

## Core Game Systems

### Configuration
- `js/config/gameConstants.js` - World size, terrain types, core constants
- `js/config/unitTypes.js` - Unit definitions and stats
- `js/config/buildingTypes.js` - Building definitions and stats
- `js/config/simulationConfig.js` - Simulation parameters and settings

### Resource System
- **Standard Resources**: Mass, Energy
- **Advanced Resource**: Computronium (computational warfare, advanced AI)
- **Terrain as Resource**: Dynamic landscape consumption

### Command & Control (C&C)
- Hierarchical command structures with latency simulation
- Authority influenced by unit health, veterancy, Computronium cores
- Strategic AI systems for autonomous gameplay

### Battle Recording
- Deterministic replay system for analysis (`js/core/battleReplay.js`)
- Battle journaling for event tracking (`js/ai/battleJournal.js`)
- Recording utilities in `js/core/recordingUtils.js`

## Development Patterns

### Asset Pipeline
- **3D Models**: OBJ files in `/models/` → processed to GLB in `/processed_models/`
- **Model Manifest**: `public/assets/model-manifest.json` for asset management
- **Automatic Processing**: Webpack handles conversion during build

### Testing
- Unit tests alongside source files (`.test.js` pattern)
- Quick tests in `js/tests/` directory
- Scenario testing capabilities for game balance

### State Management
- **Immutable Updates**: Critical for determinism and replay capability
- **Entity Management**: Units, buildings, projectiles, effects managed by EntityManager
- **Global State**: Accessible via `window.gameContext` and `window.simulation`

### Rendering Systems
- **Three.js Renderer**: Primary 3D rendering (`js/rendering/threeRenderer.js`) 

## Important Notes

### Determinism Requirements
- All randomness must use seeded RNG (`js/core/deterministicRNG.js`)
- State changes must be immutable for replay capability
- No direct state mutation - use TrikeShed patterns

### Performance Considerations
- Cache-friendly data structures preferred
- Minimal allocations in hot paths
- Spatial locality optimization for entity processing
- Batch operations for efficiency

### Code Organization
- Core game logic in `js/core/`
- AI systems in `js/ai/`
- Configuration isolated in `js/config/`
- TypeScript core architecture in `src/trikeshed/`

### Documentation
- Comprehensive design docs in `/docs/`
- Architecture overview in `docs/architecture.md`
- Game design document in `docs/the-rts-concepts.md`
- Implementation guides for complex systems