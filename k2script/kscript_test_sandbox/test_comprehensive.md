# kscript README Claims Testing Report

## Testing Environment
- Kotlin: 2.1.20
- Java: 24.0.1 (Oracle GraalVM)
- kscript: 4.2.3 (with recent enhancements)

## README Claims to Test

### 1. Basic Script Execution
- [x] Interpreter usage with shebang
- [x] Inline usage (direct code)
- [x] Stdin usage (piping)
- [x] Heredoc support
- [x] URL script execution
- [x] Process substitution

### 2. Script Configuration (@file: annotations)
- [x] @file:DependsOn for dependencies
- [x] @file:Import for including files
- [x] @file:EntryPoint for kt files
- [x] @file:CompilerOptions for compilation
- [x] @file:KotlinOptions for runtime
- [x] @file:ProjectCoordinates (NEW FEATURE)

### 3. Advanced Features
- [x] Text processing mode (-t)
- [x] Interactive REPL (--interactive) 
- [x] IDEA project generation (--idea)
- [x] Script packaging (--package)
- [x] Bootstrap header (--add-bootstrap-header)
- [x] Export to Gradle project (--export-to-gradle-project) (NEW)

### 4. New Python/NPM Packaging
- [x] Python wheel building
- [x] NPM package creation
- [x] Wrapper script functionality

## Test Results