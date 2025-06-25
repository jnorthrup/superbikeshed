# RTS Game

A real-time strategy game built with Kotlin Multiplatform, WebGPU, and SpaceGraph.

## Development

Use Gradle to build and run the project:

```bash
# Build all targets (JVM, WasmJs, Native)
./gradlew buildAll
# Alternatively, to build only the default artifacts:
# ./gradlew build

# Run the JVM version
./gradlew runJvm

# Run the WasmJs browser version (development server)
./gradlew runWasm
# Alternatively:
# ./gradlew wasmJsBrowserDevelopmentRun

# Clean all build artifacts
./gradlew cleanAll
```

## GitHub Pages Deployment

The interactive demo is deployed to GitHub Pages.

### Files to Deploy:

1.  **`interactive-demo.html`** - The main HTML file for the demo.
2.  **`build/js/packages/rtsgame/kotlin/`** - The compiled WasmJs artifacts. (The exact path might vary based on your `build.gradle.kts` configuration, ensure this is correct).

### Deployment Command Example:

This example assumes you are in the root directory of the project and want to deploy to the `gh-pages` branch.

```bash
# (Optional) Create and switch to gh_pages branch if it doesn't exist
# git checkout -b gh_pages

# Ensure your working directory is clean and all changes are committed
# Build the project to generate the necessary artifacts
./gradlew buildAll

# Add the demo files to the staging area
git add interactive-demo.html
git add build/js/packages/rtsgame/kotlin/ # Adjust path if necessary

# Commit the changes
git commit -m "🎮 Deploy Interactive RTS WebGPU Demo"

# Push to the gh-pages branch on GitHub
git push origin gh-pages
# If deploying for the first time or to a new gh-pages branch:
# git push origin HEAD:gh_pages
```

The game will be available at: `https://yourusername.github.io/yourrepositoryname/interactive-demo.html` (replace `yourusername` and `yourrepositoryname` accordingly).

## Project Structure

The project follows a Kotlin Multiplatform structure:

```
rtsgame/
├── kotlin/                 # Shared Kotlin module with its own source sets and build script
│   ├── src/                # Source sets like commonMain, jvmMain, jsMain, etc.
│   └── build.gradle.kts    # Build script for the 'kotlin' module
├── src/                    # Main project source sets
│   ├── commonMain/kotlin/  # Code common to all platforms
│   ├── jvmMain/kotlin/     # JVM-specific code
│   ├── wasmJsMain/kotlin/  # WasmJs-specific code
│   └── nativeMain/kotlin/  # Platform-native code (e.g., macosArm64Main, linuxX64Main)
│       └── (or specific targets like src/macosArm64Main/kotlin/)
├── interactive-demo.html   # Main interactive demo file
├── build.gradle.kts        # Main project build script
└── settings.gradle.kts     # Includes the 'kotlin' module and other project settings
```
The main interactive demo can be found in `interactive-demo.html` in the project root.

## Features

-   **Kotlin Multiplatform:** Write code once and run it on JVM, Web (WasmJs), and Native platforms.
-   **WebGPU Rendering:** Utilizes the modern WebGPU API for high-performance graphics.
-   **SpaceGraph Integration:** Visualizes entity relationships and game state.
-   **Real-time Simulation:** Entities move, interact, and battle in real-time.
-   **Interactive Demo:** Control panel with buttons to start/stop simulation, add units, reset, and zoom.
-   **Cross-platform Compatibility:** Aims for a unified experience across different targets.
-   **TrikeShed Type System:** Leverages a robust type system for data modeling (dependency).

## Contributing

1.  Fork the repository.
2.  Create a feature branch.
3.  Make your changes, ensuring they align with the multiplatform architecture.
4.  Submit a pull request.

## License

MIT