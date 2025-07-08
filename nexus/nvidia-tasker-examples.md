# NVIDIA Tasker Examples

## Running the Script

```bash
./nvidia-tasker.main.kts
```

## Example 1: Direct AI Query

```bash
./nvidia-tasker.main.kts "Explain the concept of coroutines in Kotlin"
```

## Example 2: Interactive Mode

```bash
./nvidia-tasker.main.kts
> What is the difference between suspend and async in Kotlin?
> exit
```

## Example 3: Coordinate-Based File Editing

First, inspect your file with line numbers:
```bash
cat -n example.kt
```

### Insert Code After a Specific Line

```bash
./nvidia-tasker.main.kts --edit << 'EOF'
INSERT: example.kt
AFTER-LINE: 10
<<CODE
    fun newFunction() {
        println("Added by NVIDIA Tasker")
    }
CODE
EOF
```

### Replace Lines

```bash
./nvidia-tasker.main.kts --edit << 'EOF'
REPLACE: example.kt
LINES: 5-8
<<CODE
    // Updated implementation
    fun improvedFunction() {
        return "Better code"
    }
CODE
EOF
```

### Delete Lines

```bash
./nvidia-tasker.main.kts --edit << 'EOF'
DELETE: example.kt
LINES: 15-20
EOF
```

## Example 4: AI-Assisted Code Analysis

```bash
# First, show the file with line numbers
cat -n MyClass.kt

# Then ask AI about specific lines
./nvidia-tasker.main.kts "In MyClass.kt, what does the function at lines 25-35 do?"
```

## Example 5: Workflow - AI Suggests, Human Approves, Bot Executes

1. **Human inspects with coordinates:**
   ```bash
   cat -n src/main/kotlin/App.kt
   ```

2. **Human asks AI for suggestion:**
   ```bash
   ./nvidia-tasker.main.kts "I need to add error handling to the function at line 42 in App.kt"
   ```

3. **AI suggests code with line numbers**

4. **Human creates precise edit instruction:**
   ```bash
   ./nvidia-tasker.main.kts --edit << 'EOF'
   INSERT: src/main/kotlin/App.kt
   AFTER-LINE: 42
   <<CODE
       try {
           // existing code will be wrapped
       } catch (e: Exception) {
           logger.error("Operation failed", e)
           throw ServiceException("Failed to process", e)
       }
   CODE
   EOF
   ```

## Key Safety Features

1. **Coordinate-based edits** - Uses line numbers, not pattern matching
2. **Human inspection first** - Always use `cat -n` or `grep -n` to get coordinates
3. **Heredoc safety** - Multi-line code without escaping issues
4. **API key rotation** - Automatically cycles through multiple keys for quota management
5. **Clear state machine** - Shows what the bot is doing at each step

## Environment Variables

Set these for API key rotation:
```bash
export NVIDIA_API_KEY="your-primary-key"
export NVIDIA_API_KEY_2="your-second-key"
export NVIDIA_API_KEY_3="your-third-key"
```

The script includes a default key but will prefer your environment variables.