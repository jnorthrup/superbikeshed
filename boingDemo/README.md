# boingDemo

Kotlin Multiplatform bouncing ball demo with audio feedback.

## Build

```bash
./gradlew build
```

## Run

### Desktop
```bash
./gradlew :boingDemo:run
```

### Web
```bash
./gradlew :boingDemo:wasmJsBrowserDevelopmentRun
```

### Native
```bash
./gradlew :boingDemo:runDebugExecutableNative
```

## Architecture

- **commonMain**: Core demo logic and shared resources
- **desktopMain**: JVM desktop implementation  
- **wasmJsMain**: Web browser implementation
- **nativeMain**: Native platform implementation

## Audio Support

| Platform | Status |
|----------|---------|
| Desktop  | ✅ Working |
| Web      | ✅ Working |
| Native   | ⚠️ Stub implementation |

## Dependencies

- Kotlin Multiplatform
- Platform-specific audio libraries