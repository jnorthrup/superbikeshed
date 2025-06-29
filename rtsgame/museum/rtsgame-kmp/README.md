---
**Note: This project is an archived version or an earlier experimental iteration of the RTS game. The current active development for the RTS game (featuring a WebGPU-based interactive demo) can be found in the main `rtsgame` directory at the root of this repository.**
---

# RTS Game - Kotlin Multiplatform

A Real-Time Strategy game built with Kotlin Multiplatform, supporting both desktop and web platforms.

## Project Structure

- `shared/` - Common code shared between platforms
  - Entity system
  - Game state management
  - Command system
- `desktop/` - Desktop-specific implementation
- `web/` - Web-specific implementation

## Building the Project

To build the project, run:

```bash
./gradlew build
```

## Running the Game

### Desktop
```bash
./gradlew :desktop:run
```

### Web
```bash
./gradlew :web:jsBrowserDevelopmentRun
```

## Features

- Multiplatform support (Desktop and Web)
- Entity-Component System
- Command pattern for game actions
- Resource management
- Unit and building systems
- Combat mechanics

## Development

The project uses:
- Kotlin Multiplatform
- Kotlinx Serialization
- Kotlinx Coroutines
- Compose Multiplatform for UI

## License

MIT License 