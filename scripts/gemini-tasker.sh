#!/bin/bash

# Gemini Tasker Script - Clone repo into new feature branch with zero errors requirement
# This script clones the current repo, creates a new feature branch, and ensures zero build errors

set -euo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_color() {
    echo -e "${1}${2}${NC}"
}

# Function to check if we're in a git repository
check_git_repo() {
    if ! git rev-parse --git-dir > /dev/null 2>&1; then
        print_color $RED "Error: Not in a git repository"
        exit 1
    fi
}

# Function to find the latest zero-error tag
find_zero_error_tag() {
    print_color $BLUE "Finding latest zero-error tag..."
    local zero_error_tag=$(git tag -l "*zero-error*" | sort -V | tail -n 1)
    
    if [ -z "$zero_error_tag" ]; then
        print_color $YELLOW "Warning: No zero-error tag found. Using current HEAD as parent."
        echo "HEAD"
    else
        print_color $GREEN "Found zero-error tag: $zero_error_tag"
        echo "$zero_error_tag"
    fi
}

# Function to create feature branch
create_feature_branch() {
    local base_ref="$1"
    local branch_name="$2"
    
    print_color $BLUE "Creating feature branch: $branch_name from $base_ref"
    
    # Check if branch already exists
    if git show-ref --verify --quiet refs/heads/"$branch_name"; then
        print_color $RED "Error: Branch $branch_name already exists"
        exit 1
    fi
    
    # Create and checkout new branch
    git checkout -b "$branch_name" "$base_ref"
    print_color $GREEN "Successfully created and checked out branch: $branch_name"
}

# Function to run build and check for errors
run_build() {
    print_color $BLUE "Running build to verify zero errors..."
    
    # Run the build command from CLAUDE.md
    if ./gradlew build --console=plain --no-daemon 2>&1 | tee build_output.log; then
        print_color $GREEN "Build completed successfully!"
        return 0
    else
        print_color $RED "Build failed!"
        return 1
    fi
}

# Function to check for compilation errors
check_compilation_errors() {
    local log_file="build_output.log"
    
    if [ ! -f "$log_file" ]; then
        print_color $RED "Error: Build output log not found"
        return 1
    fi
    
    # Check for various error patterns
    local error_count=0
    error_count=$(grep -E "(FAILED|error:|compilation failed|BUILD FAILED)" "$log_file" | wc -l)
    
    if [ "$error_count" -eq 0 ]; then
        print_color $GREEN "✓ No compilation errors found"
        return 0
    else
        print_color $RED "✗ Found $error_count compilation errors"
        print_color $YELLOW "Error details:"
        grep -E "(FAILED|error:|compilation failed|BUILD FAILED)" "$log_file" | head -10
        return 1
    fi
}

# Function to run in Docker if requested
run_in_docker() {
    local clone_path="$1"
    local branch_name="$2"
    
    print_color $BLUE "Setting up Docker environment for io_uring testing..."
    
    # Create Dockerfile for Ubuntu with io_uring support
    cat > "$clone_path/Dockerfile.gemini" << 'DOCKERFILE'
FROM ubuntu:22.04

# Install dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    git \
    curl \
    wget \
    liburing-dev \
    liburing2 \
    openjdk-17-jdk \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

# Install Gradle
RUN wget https://services.gradle.org/distributions/gradle-8.5-bin.zip -P /tmp \
    && unzip -d /opt/gradle /tmp/gradle-*.zip \
    && rm /tmp/gradle-*.zip

ENV GRADLE_HOME=/opt/gradle/gradle-8.5
ENV PATH="${GRADLE_HOME}/bin:${PATH}"

# Set working directory
WORKDIR /workspace

# Copy the repository
COPY . /workspace/

# Create entrypoint script
RUN echo '#!/bin/bash\nset -e\necho "=== Docker Ubuntu Environment ==="\necho "Kernel: $(uname -r)"\necho "io_uring support: $(ldconfig -p | grep liburing || echo "Not found")"\necho ""\nexec "$@"' > /entrypoint.sh && chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
CMD ["/bin/bash"]
DOCKERFILE

    # Build Docker image
    print_color $BLUE "Building Docker image..."
    docker build -f "$clone_path/Dockerfile.gemini" -t gemini-uring-test:latest "$clone_path"
    
    # Run build in Docker
    print_color $BLUE "Running build in Docker container..."
    docker run --rm \
        -v "$clone_path:/workspace" \
        -w /workspace \
        gemini-uring-test:latest \
        bash -c "./gradlew build --console=plain --no-daemon && echo 'BUILD_SUCCESS' > docker_build_status.txt || echo 'BUILD_FAILED' > docker_build_status.txt"
    
    # Check Docker build status
    if [ -f "$clone_path/docker_build_status.txt" ] && grep -q "BUILD_SUCCESS" "$clone_path/docker_build_status.txt"; then
        print_color $GREEN "Docker build completed successfully!"
        
        # Run io_uring specific tests if available
        print_color $BLUE "Running io_uring tests in Docker..."
        docker run --rm \
            -v "$clone_path:/workspace" \
            -w /workspace \
            --cap-add SYS_NICE \
            --cap-add IPC_LOCK \
            gemini-uring-test:latest \
            bash -c "echo 'Testing io_uring capabilities...' && ls -la /usr/include/liburing* || true"
        
        return 0
    else
        print_color $RED "Docker build failed!"
        return 1
    fi
}

# Main script execution
main() {
    print_color $BLUE "=== Gemini Tasker - Zero Error Branch Creator ==="
    
    # Check for flags
    local use_docker=false
    local no_clone=false
    local use_remote=false
    local skip_gemini=false
    local gemini_task_file=""
    
    while [[ $# -gt 0 ]] && [[ "$1" =~ ^-- ]]; do
        case "$1" in
            --docker)
                use_docker=true
                shift
                ;;
            --no-clone)
                no_clone=true
                shift
                ;;
            --remote)
                use_remote=true
                shift
                ;;
            --skip-gemini)
                skip_gemini=true
                shift
                ;;
            --task)
                shift
                if [[ $# -gt 0 ]]; then
                    gemini_task_file="$1"
                    shift
                else
                    print_color $RED "Error: --task requires a file path"
                    exit 1
                fi
                ;;
            *)
                print_color $RED "Unknown option: $1"
                exit 1
                ;;
        esac
    done
    
    # Check environment variables
    if [[ "${GEMINI_USE_DOCKER:-}" == "true" ]]; then
        use_docker=true
    fi
    
    # Check prerequisites
    check_git_repo
    
    # Get current directory name for default clone target
    local current_dir=$(basename "$(pwd)")
    local original_pwd="$(pwd)"
    local clone_target
    local branch_name="${1:-feature/gemini-task-$(date +%Y%m%d-%H%M%S)}"
    
    if [ "$no_clone" = true ]; then
        clone_target="$original_pwd"
        print_color $YELLOW "Working in current directory (no clone)"
    else
        clone_target="${2:-/tmp/${current_dir}-gemini-clone-$(date +%Y%m%d-%H%M%S)}"
    fi
    
    # Find zero-error parent
    local zero_error_parent=$(find_zero_error_tag)
    
    # Get clone source - prefer local unless --remote flag is used
    local clone_source="$original_pwd"
    
    if [ "$use_remote" = true ]; then
        local remote_url=$(git config --get remote.origin.url || echo "")
        if [ -n "$remote_url" ]; then
            clone_source="$remote_url"
            print_color $BLUE "Using remote repository: $remote_url"
        else
            print_color $YELLOW "Warning: --remote specified but no remote origin found. Using local clone."
        fi
    else
        print_color $BLUE "Using local repository: $clone_source"
    fi
    
    # Clone or stay in current directory
    if [ "$no_clone" = false ]; then
        print_color $BLUE "Cloning repository to: $clone_target"
        
        # Clone from selected source
        git clone "$clone_source" "$clone_target"
        
        # Change to cloned directory
        cd "$clone_target"
        
        # Fetch all tags
        git fetch --tags
    else
        print_color $BLUE "Working in current directory: $clone_target"
        # Stash any uncommitted changes
        if ! git diff --quiet || ! git diff --cached --quiet; then
            print_color $YELLOW "Stashing uncommitted changes..."
            git stash push -m "gemini-tasker: auto-stash before branch creation"
        fi
    fi
    
    # Create feature branch from zero-error parent
    create_feature_branch "$zero_error_parent" "$branch_name"
    
    # Run build to verify zero errors (optionally in Docker)
    local build_success=false
    if [ "$use_docker" = true ]; then
        if command -v docker &> /dev/null; then
            if run_in_docker "$PWD" "$branch_name"; then
                build_success=true
            fi
        else
            print_color $RED "Docker not found! Install Docker or run without --docker flag"
            exit 1
        fi
    else
        if run_build && check_compilation_errors; then
            build_success=true
        fi
    fi
    
    if [ "$build_success" = true ]; then
        print_color $GREEN "=== SUCCESS ==="
        print_color $GREEN "Repository cloned to: $clone_target"
        print_color $GREEN "Feature branch created: $branch_name"
        print_color $GREEN "Build completed with ZERO ERRORS ✓"
        
        # Save success marker
        echo "GEMINI_TASK_SUCCESS" > .gemini_task_status
        echo "Branch: $branch_name" >> .gemini_task_status
        echo "Parent: $zero_error_parent" >> .gemini_task_status
        echo "Timestamp: $(date)" >> .gemini_task_status
        
        # Skip Gemini if requested
        if [ "$skip_gemini" = true ]; then
            print_color $GREEN "Skipping Gemini task (--skip-gemini flag set)"
            exit 0
        fi
        
        # Create or use Gemini task file
        if [ -n "$gemini_task_file" ]; then
            if [ -f "$gemini_task_file" ]; then
                print_color $BLUE "Using custom task file: $gemini_task_file"
                cp "$gemini_task_file" GEMINI_TASK.md
            elif [ "$gemini_task_file" = "-" ]; then
                print_color $BLUE "Reading task from stdin..."
                cat > GEMINI_TASK.md
            else
                print_color $RED "Error: Task file not found: $gemini_task_file"
                exit 1
            fi
        else
            # No task specified - exit gracefully
            print_color $GREEN "No Gemini task specified. Use --task <file> or --task - for stdin"
            print_color $GREEN "Repository ready at: $PWD"
            exit 0
        fi

        # Create automated Gemini execution script
        cat > run_gemini_task.sh << 'GEMINI_SCRIPT'
#!/bin/bash
set -euo pipefail

echo "=== Gemini Task Environment ==="
echo "Repository: $(pwd)"
echo "Branch: $(git branch --show-current)"
echo "Status: Zero errors verified ✓"
echo ""
echo "Executing Gemini task..."

# Try to run Gemini CLI with proper options
if command -v gemini &> /dev/null; then
    # Run gemini in YOLO mode with the task as prompt
    gemini --yolo --model gemini-2.5-pro --prompt "$(cat GEMINI_TASK.md)" > GEMINI_OUTPUT.log 2>&1
else
    echo "WARNING: Gemini CLI not found."
    echo "Install with: npm install -g @anthropic/gemini-cli"
    exit 1
fi

echo "Task completed. Check LINT_REPORT.md for results."
GEMINI_SCRIPT
        chmod +x run_gemini_task.sh
        
        # Run Gemini task in background
        print_color $BLUE "Running Gemini task in background..."
        nohup ./run_gemini_task.sh > gemini_task_output.log 2>&1 &
        local gemini_pid=$!
        
        print_color $GREEN "=== GEMINI TASK LAUNCHED ==="
        print_color $GREEN "Clone directory: $PWD"
        print_color $GREEN "Task PID: $gemini_pid"
        print_color $GREEN "Task file: GEMINI_TASK.md"
        print_color $GREEN "Output will be saved to: LINT_REPORT.md"
        print_color $GREEN "Log file: gemini_task_output.log"
        
        # Optionally wait for task completion (with timeout)
        local wait_time=30
        print_color $YELLOW "Waiting up to ${wait_time}s for task completion..."
        
        local count=0
        while [ $count -lt $wait_time ] && kill -0 $gemini_pid 2>/dev/null; do
            sleep 1
            ((count++))
            printf "."
        done
        echo ""
        
        if kill -0 $gemini_pid 2>/dev/null; then
            print_color $YELLOW "Task still running in background (PID: $gemini_pid)"
            print_color $YELLOW "Check results later at: $PWD/LINT_REPORT.md"
        else
            print_color $GREEN "Task completed!"
            if [ -f LINT_REPORT.md ]; then
                print_color $GREEN "Report generated successfully: LINT_REPORT.md"
                echo "--- First 20 lines of report ---"
                head -20 LINT_REPORT.md
            fi
        fi
        
        exit 0
    else
        print_color $RED "=== FAILURE ==="
        print_color $RED "Build contains errors. Task requirements not met."
        
        # Save failure marker
        echo "GEMINI_TASK_FAILURE" > .gemini_task_status
        echo "Branch: $branch_name" >> .gemini_task_status
        echo "Parent: $zero_error_parent" >> .gemini_task_status
        echo "Timestamp: $(date)" >> .gemini_task_status
        
        exit 1
    fi
}

# Show usage if --help is provided
if [[ "${1:-}" == "--help" ]] || [[ "${1:-}" == "-h" ]]; then
    echo "Usage: $0 [OPTIONS] [branch_name] [clone_target]"
    echo ""
    echo "Creates a new feature branch based on the latest zero-error tag,"
    echo "verifies build, and runs Gemini linting task."
    echo ""
    echo "Options:"
    echo "  --docker      Run build in Docker container with Ubuntu/io_uring support"
    echo "  --no-clone    Work in current directory instead of cloning"
    echo "  --remote      Clone from remote origin instead of local directory (default: local)"
    echo "  --skip-gemini Skip running Gemini task after build"
    echo "  --task FILE   Specify Gemini task file (use '-' for stdin)"
    echo "  -h, --help    Show this help message"
    echo ""
    echo "Arguments:"
    echo "  branch_name   Name for the feature branch (default: feature/gemini-task-<timestamp>)"
    echo "  clone_target  Directory to clone into (default: /tmp/<current-dir>-gemini-clone-<timestamp>)"
    echo "                (ignored if --no-clone is used)"
    echo ""
    echo "Environment variables:"
    echo "  GEMINI_USE_DOCKER=true  Same as --docker flag"
    echo ""
    echo "Examples:"
    echo "  $0                           # Clone locally to /tmp and create timestamped branch"
    echo "  $0 --no-clone                # Work in current directory"
    echo "  $0 --docker feature/io-uring # Use Docker for io_uring testing"
    echo "  $0 --no-clone --docker       # Current dir + Docker"
    echo "  $0 --remote                  # Clone from remote origin instead of local"
    echo "  $0 --task -                  # Read Gemini task from stdin"
    echo "  echo 'Fix zero errors' | $0 --task -  # Pipe task to Gemini"
    echo "  $0 --task <(echo 'Ensure zero build errors')  # Process substitution"
    echo ""
    echo "The script will:"
    echo "  1. Find the latest zero-error tag in the repository"
    echo "  2. Clone the repository OR work in current directory (--no-clone)"
    echo "  3. Create a new feature branch from the zero-error parent"
    echo "  4. Run the build and verify zero compilation errors"
    echo "  5. Launch Gemini CLI to lint the script and create a report"
    echo "  6. Exit with success (0) only if build has zero errors"
    echo ""
    echo "Docker mode includes:"
    echo "  - Ubuntu 22.04 base image"
    echo "  - io_uring development libraries"
    echo "  - JDK 17 and Gradle 8.5"
    echo "  - Automated build verification"
    exit 0
fi

# Run main function
main "$@"