# Zero-Error Build Strategy

## Systematic Approach to Error-Free Codebase

### 1. Pre-Build Validation
```bash
# Check syntax before building
./gradlew :module-name:compileKotlinCommon --console=plain --no-daemon

# Run quick checks
./gradlew clean --console=plain --no-daemon
```

### 2. Incremental Error Resolution

#### Step 1: Identify All Errors
```bash
# Build with full error output
./gradlew build --console=plain --no-daemon --stacktrace > build-errors.log 2>&1

# Or module by module
./gradlew :trikeshed-lib:build --console=plain --no-daemon
```

#### Step 2: Fix Dependency Issues First
- Check version inconsistencies in root `build.gradle.kts`
- Ensure all modules declare proper dependencies
- Verify multiplatform targets are consistent

#### Step 3: Fix Compilation Errors
- Import issues: Add wildcard imports per CLAUDE.md
- Type mismatches: Use proper CoreTypes (Indexed, Join)
- Missing implementations: Add expect/actual declarations

### 3. Claude Workflow for Zero Errors

#### A. Interactive Error Resolution
```
1. Run build → Capture errors
2. Ask Claude: "Fix these build errors: [paste errors]"
3. Apply fixes incrementally
4. Re-run build to verify
```

#### B. Proactive Error Prevention
```
1. Before writing new code:
   - "Check if this import exists in the project"
   - "Verify this type matches existing patterns"
   
2. After writing code:
   - "Run build for this module"
   - "Check for any type mismatches"
```

### 4. Automated Validation Pipeline

Create a validation script:

```kotlin
// buildSrc/src/main/kotlin/ValidationPlugin.kt
import org.gradle.api.Plugin
import org.gradle.api.Project

class ValidationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("validateCode") {
            doLast {
                // Check imports
                project.fileTree("src").matching {
                    include("**/*.kt")
                }.forEach { file ->
                    val content = file.readText()
                    // Validate wildcard imports
                    if (!content.contains("import borg.trikeshed.lib.*")) {
                        println("WARNING: ${file.path} missing wildcard import")
                    }
                }
            }
        }
    }
}
```

### 5. Continuous Integration Checks

#### Pre-commit Hook
```bash
#!/bin/bash
# .git/hooks/pre-commit

echo "Running pre-commit validation..."

# Quick syntax check
./gradlew compileKotlinCommon --console=plain --no-daemon

if [ $? -ne 0 ]; then
    echo "Build failed. Fix errors before committing."
    exit 1
fi
```

#### GitHub Actions Workflow
```yaml
name: Zero-Error Build
on: [push, pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      - name: Build and Validate
        run: |
          ./gradlew build --console=plain --no-daemon
          ./gradlew test --console=plain --no-daemon
```

### 6. Claude Commands for Error-Free Development

#### Before Starting Work
```
"Check the current build status"
"List any known compilation issues"
"Verify all dependencies are resolved"
```

#### While Coding
```
"Validate this code against CoreTypes"
"Check if this import exists"
"Ensure this follows the codebase patterns"
```

#### After Making Changes
```
"Run build for affected modules"
"Check for any new warnings or errors"
"Verify tests still pass"
```

### 7. Common Error Patterns and Fixes

#### Missing Coroutines Version
```kotlin
// In root build.gradle.kts
val coroutinesVersion = "1.7.3"
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
}
```

#### Type Mismatch with Indexed
```kotlin
// Wrong
val list: List<T> = ...

// Correct
val indexed: Indexed<T> = list.toIdx()
```

#### Missing Platform Implementations
```kotlin
// In commonMain
expect fun platformSpecific(): String

// In jvmMain
actual fun platformSpecific(): String = "JVM"

// In jsMain
actual fun platformSpecific(): String = "JS"
```

### 8. Sandbox VM Zero-Error Submissions

#### Pre-submission Checklist
1. Clean build: `./gradlew clean build`
2. All tests pass: `./gradlew test`
3. No warnings: `./gradlew build -Dwarnings.mode=fail`
4. Lint clean: `./gradlew lint`

#### Sandbox Validation Script
```bash
#!/bin/bash
# validate-for-sandbox.sh

set -e  # Exit on any error

echo "=== Sandbox Pre-flight Check ==="

# Clean workspace
./gradlew clean --console=plain --no-daemon

# Build all modules
./gradlew build --console=plain --no-daemon

# Run tests
./gradlew test --console=plain --no-daemon

# Check for uncommitted changes
if [[ -n $(git status -s) ]]; then
    echo "ERROR: Uncommitted changes detected"
    exit 1
fi

echo "=== All checks passed! Ready for sandbox submission ==="
```

### 9. Error Recovery Procedures

If errors occur:

1. **Capture Full Context**
   ```bash
   ./gradlew build --stacktrace --info > error-log.txt 2>&1
   ```

2. **Ask Claude for Targeted Help**
   ```
   "Here's the error log: [paste]. Focus on fixing the first error."
   ```

3. **Incremental Fixes**
   - Fix one module at a time
   - Run build after each fix
   - Commit working states

### 10. Monitoring and Metrics

Track build health:
```kotlin
// Track build success rate
task("buildMetrics") {
    doLast {
        val successFile = file("build-success.log")
        successFile.appendText("${Date()}: SUCCESS\n")
    }
}
```

## Summary

Zero-error builds require:
1. Systematic error identification
2. Incremental resolution
3. Automated validation
4. Continuous monitoring
5. Clear communication with Claude

Use Claude as your pair programmer:
- Ask for validation before implementing
- Request build checks after changes
- Get help with specific error messages
- Maintain a feedback loop

The goal is to catch errors early and fix them systematically, maintaining a always-green build status.