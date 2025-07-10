#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <time.h>
#include <signal.h>
#include <errno.h>

#define MAX_PATH 4096
#define MAX_CMD 8192

// FSM States
typedef enum {
    STATE_INIT = 0,
    STATE_VALIDATING,
    STATE_SANDBOXING,
    STATE_TASKING,
    STATE_MONITORING,
    STATE_COMPLETING,
    STATE_CLEANUP,
    STATE_DONE
} fsm_state_t;

// State names for logging
const char* state_names[] = {
    "INIT",
    "VALIDATING", 
    "SANDBOXING",
    "TASKING",
    "MONITORING",
    "COMPLETING",
    "CLEANUP",
    "DONE"
};

// Agent context
typedef struct {
    char role[64];
    char task[256];
    int duration;
    pid_t agent_pid;
    char sandbox_dir[MAX_PATH];
    char task_id[64];
    time_t start_time;
    fsm_state_t current_state;
} agent_context_t;

// Color codes
#define COLOR_RED     "\033[0;31m"
#define COLOR_GREEN   "\033[0;32m"
#define COLOR_YELLOW  "\033[1;33m"
#define COLOR_BLUE    "\033[0;34m"
#define COLOR_PURPLE  "\033[0;35m"
#define COLOR_CYAN    "\033[0;36m"
#define COLOR_RESET   "\033[0m"

// Logging functions
void log_info(const char* msg) {
    printf("%s[INFO]%s %s\n", COLOR_BLUE, COLOR_RESET, msg);
}

void log_success(const char* msg) {
    printf("%s[SUCCESS]%s %s\n", COLOR_GREEN, COLOR_RESET, msg);
}

void log_error(const char* msg) {
    printf("%s[ERROR]%s %s\n", COLOR_RED, COLOR_RESET, msg);
    fflush(stdout);
}

void log_state(const char* state, const char* msg) {
    printf("%s[FSM:%s]%s %s\n", COLOR_PURPLE, state, COLOR_RESET, msg);
}

// Calculate sum of K2Script components
long calculate_component_sum() {
    long sum = 0;
    struct stat st;
    
    const char* components[] = {
        "./k2script/build/bin/native/releaseExecutable/k2script.kexe",
        "./k2script/build/libs/k2script-jvm.jar",
        "./k2script/build/libs/k2script-js.js",
        "./nexus/nvidia-tasker-stacktrace.kts",
        NULL
    };
    
    for (int i = 0; components[i] != NULL; i++) {
        if (stat(components[i], &st) == 0) {
            sum += st.st_size;
            sum += st.st_mtime % 10000; // Add time component
        }
    }
    
    return sum;
}

// Validate installation sum
int validate_sum() {
    long expected_sum = 0;
    long actual_sum = calculate_component_sum();
    char sum_file[MAX_PATH];
    FILE* fp;
    
    snprintf(sum_file, sizeof(sum_file), "./.k2script-launcher.sum");
    
    // Read or create sum file
    fp = fopen(sum_file, "r");
    if (fp) {
        fscanf(fp, "%ld", &expected_sum);
        fclose(fp);
        
        if (expected_sum == actual_sum) {
            char msg[256];
            snprintf(msg, sizeof(msg), "Sum validated: %ld", actual_sum);
            log_success(msg);
            return 0;
        } else {
            char msg[256];
            snprintf(msg, sizeof(msg), "Sum mismatch! Expected: %ld, Got: %ld", expected_sum, actual_sum);
            log_error(msg);
            return -1;
        }
    } else {
        // First run - save sum
        fp = fopen(sum_file, "w");
        if (fp) {
            fprintf(fp, "%ld", actual_sum);
            fclose(fp);
            char msg[256];
            snprintf(msg, sizeof(msg), "Initial sum stored: %ld", actual_sum);
            log_info(msg);
            return 0;
        }
        return -1;
    }
}

// Create sandbox directory
int create_sandbox(agent_context_t* ctx) {
    char cmd[MAX_CMD];
    snprintf(ctx->task_id, sizeof(ctx->task_id), "task-%ld-%d", time(NULL), getpid());
    snprintf(ctx->sandbox_dir, sizeof(ctx->sandbox_dir), "./.k2script-sandbox/%s", ctx->task_id);
    
    snprintf(cmd, sizeof(cmd), "mkdir -p %s", ctx->sandbox_dir);
    if (system(cmd) != 0) {
        log_error("Failed to create sandbox directory");
        return -1;
    }
    
    // Copy necessary files
    snprintf(cmd, sizeof(cmd), "cp ./nexus/nvidia-tasker-stacktrace.kts %s/", ctx->sandbox_dir);
    system(cmd);
    
    // Create sandbox config
    char config_path[MAX_PATH];
    snprintf(config_path, sizeof(config_path), "%s/sandbox.conf", ctx->sandbox_dir);
    FILE* fp = fopen(config_path, "w");
    if (fp) {
        fprintf(fp, "TASK_ID=%s\n", ctx->task_id);
        fprintf(fp, "SANDBOX_ROOT=%s\n", ctx->sandbox_dir);
        fprintf(fp, "MEMORY_LIMIT=512M\n");
        fprintf(fp, "CPU_SHARES=50\n");
        fprintf(fp, "NETWORK_ALLOWED=true\n");
        fclose(fp);
    }
    
    char msg[256];
    snprintf(msg, sizeof(msg), "Sandbox created: %s", ctx->sandbox_dir);
    log_info(msg);
    return 0;
}

// Launch agent process
int launch_agent(agent_context_t* ctx) {
    pid_t pid = fork();
    
    if (pid == -1) {
        log_error("Failed to fork agent process");
        return -1;
    }
    
    if (pid == 0) {
        // Child process
        char k2_cmd[MAX_CMD];
        
        // Change to sandbox directory
        if (chdir(ctx->sandbox_dir) != 0) {
            exit(1);
        }
        
        // Set environment
        setenv("K2_SANDBOX_MODE", "true", 1);
        setenv("K2_ROLE", ctx->role, 1);
        setenv("K2_TASK", ctx->task, 1);
        
        // Build command
        if (access("../../k2script/build/bin/native/releaseExecutable/k2script.kexe", X_OK) == 0) {
            snprintf(k2_cmd, sizeof(k2_cmd), 
                "timeout %d ../../k2script/build/bin/native/releaseExecutable/k2script.kexe nvidia-tasker-stacktrace.kts %s",
                ctx->duration, ctx->task);
        } else {
            snprintf(k2_cmd, sizeof(k2_cmd), 
                "timeout %d k2script nvidia-tasker-stacktrace.kts %s",
                ctx->duration, ctx->task);
        }
        
        // Execute
        execl("/bin/sh", "sh", "-c", k2_cmd, NULL);
        exit(1); // If exec fails
    }
    
    // Parent process
    ctx->agent_pid = pid;
    char msg[256];
    snprintf(msg, sizeof(msg), "Agent launched with PID: %d", pid);
    log_success(msg);
    
    // Save PID to file
    char pid_file[MAX_PATH];
    snprintf(pid_file, sizeof(pid_file), "%s/agent.pid", ctx->sandbox_dir);
    FILE* fp = fopen(pid_file, "w");
    if (fp) {
        fprintf(fp, "%d", pid);
        fclose(fp);
    }
    
    return 0;
}

// Monitor agent execution
int monitor_agent(agent_context_t* ctx) {
    int status;
    pid_t result;
    time_t current_time;
    int elapsed;
    
    log_info("Monitoring agent execution...");
    
    while (1) {
        result = waitpid(ctx->agent_pid, &status, WNOHANG);
        
        if (result == ctx->agent_pid) {
            // Process finished
            if (WIFEXITED(status)) {
                char msg[256];
                snprintf(msg, sizeof(msg), "Agent exited with code: %d", WEXITSTATUS(status));
                log_info(msg);
            }
            break;
        }
        
        current_time = time(NULL);
        elapsed = (int)(current_time - ctx->start_time);
        
        if (elapsed >= ctx->duration) {
            log_info("Duration limit reached, terminating agent");
            kill(ctx->agent_pid, SIGTERM);
            sleep(2);
            kill(ctx->agent_pid, SIGKILL);
            waitpid(ctx->agent_pid, &status, 0);
            break;
        }
        
        // Show progress
        int remaining = ctx->duration - elapsed;
        printf("\r%s[MONITOR]%s Time remaining: %ds ", COLOR_CYAN, COLOR_RESET, remaining);
        fflush(stdout);
        
        sleep(1);
    }
    
    printf("\n");
    return 0;
}

// Cleanup sandbox
int cleanup_sandbox(agent_context_t* ctx) {
    char cmd[MAX_CMD];
    char msg[256];
    
    snprintf(msg, sizeof(msg), "Cleaning up sandbox: %s", ctx->sandbox_dir);
    log_info(msg);
    
    // Save logs before cleanup
    snprintf(cmd, sizeof(cmd), "mkdir -p ./logs/k2-sandbox");
    system(cmd);
    
    snprintf(cmd, sizeof(cmd), "cp %s/output.log ./logs/k2-sandbox/%s-output.log 2>/dev/null", 
            ctx->sandbox_dir, ctx->task_id);
    system(cmd);
    
    // Remove sandbox
    snprintf(cmd, sizeof(cmd), "rm -rf %s", ctx->sandbox_dir);
    system(cmd);
    
    return 0;
}

// FSM state transition
fsm_state_t transition_state(agent_context_t* ctx) {
    fsm_state_t next_state = ctx->current_state + 1;
    
    if (next_state > STATE_DONE) {
        next_state = STATE_DONE;
    }
    
    char msg[256];
    snprintf(msg, sizeof(msg), "Transitioning to: %s", state_names[next_state]);
    log_state(state_names[ctx->current_state], msg);
    
    ctx->current_state = next_state;
    return next_state;
}

// Execute with FSM
int execute_with_fsm(agent_context_t* ctx) {
    ctx->current_state = STATE_INIT;
    ctx->start_time = time(NULL);
    
    while (ctx->current_state != STATE_DONE) {
        switch (ctx->current_state) {
            case STATE_INIT:
                log_state("INIT", "Initializing launcher");
                transition_state(ctx);
                break;
                
            case STATE_VALIDATING:
                if (validate_sum() != 0) {
                    log_error("Validation failed!");
                    return -1;
                }
                transition_state(ctx);
                break;
                
            case STATE_SANDBOXING:
                if (create_sandbox(ctx) != 0) {
                    log_error("Failed to create sandbox!");
                    return -1;
                }
                transition_state(ctx);
                break;
                
            case STATE_TASKING:
                if (launch_agent(ctx) != 0) {
                    log_error("Failed to launch agent!");
                    return -1;
                }
                transition_state(ctx);
                break;
                
            case STATE_MONITORING:
                monitor_agent(ctx);
                transition_state(ctx);
                break;
                
            case STATE_COMPLETING:
                log_info("Task completed, gathering results...");
                transition_state(ctx);
                break;
                
            case STATE_CLEANUP:
                cleanup_sandbox(ctx);
                transition_state(ctx);
                break;
                
            default:
                log_error("Invalid state!");
                return -1;
        }
    }
    
    log_success("FSM execution completed successfully");
    return 0;
}

// Show usage
void show_usage(const char* prog) {
    printf("%sK2Script Native Launcher with FSM%s\n\n", COLOR_CYAN, COLOR_RESET);
    printf("Usage: %s <role> <task> [duration]\n\n", prog);
    printf("Roles:\n");
    printf("  analyzer     - Code analysis and stacktrace processing\n");
    printf("  generator    - Code generation and fixes\n");
    printf("  validator    - Result validation and testing\n");
    printf("  orchestrator - Multi-agent coordination\n\n");
    printf("Tasks:\n");
    printf("  fix-stacktrace - Process and fix compilation errors\n");
    printf("  analyze-code   - Analyze codebase with NVIDIA AI\n");
    printf("  generate-tests - Generate test cases\n");
    printf("  validate-build - Validate build integrity\n\n");
    printf("Duration: Time limit in seconds (default: 300)\n\n");
    printf("Example:\n");
    printf("  %s analyzer fix-stacktrace 600\n", prog);
}

// Main
int main(int argc, char* argv[]) {
    if (argc < 3) {
        show_usage(argv[0]);
        return 1;
    }
    
    agent_context_t ctx = {0};
    
    // Parse arguments
    strncpy(ctx.role, argv[1], sizeof(ctx.role) - 1);
    strncpy(ctx.task, argv[2], sizeof(ctx.task) - 1);
    ctx.duration = (argc > 3) ? atoi(argv[3]) : 300;
    
    // Validate role
    if (strcmp(ctx.role, "analyzer") != 0 && 
        strcmp(ctx.role, "generator") != 0 &&
        strcmp(ctx.role, "validator") != 0 &&
        strcmp(ctx.role, "orchestrator") != 0) {
        log_error("Invalid role!");
        show_usage(argv[0]);
        return 1;
    }
    
    // Log startup
    printf("%sK2Script Native Launcher%s\n", COLOR_CYAN, COLOR_RESET);
    char msg[256];
    snprintf(msg, sizeof(msg), "Role: %s, Task: %s, Duration: %ds", 
            ctx.role, ctx.task, ctx.duration);
    log_info(msg);
    
    // Execute with FSM
    return execute_with_fsm(&ctx) == 0 ? 0 : 1;
}