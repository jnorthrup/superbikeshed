#!/bin/bash

# TrikeShed Protocol Launcher (trl1.3)
# Provides symlinked access to all channelized protocols:
# - SSH/SCP/rsync/SFTP (trl1.3)
# - OAuth 2.0/2.1 (authentication)
# - TLS 1.3 (security)
# - BitTorrent (TorrentKettle)
# - CouchDB (storage)
# - HTTP/QUIC (transport)

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="$PROJECT_ROOT/build"
BIN_DIR="$PROJECT_ROOT/bin"

# Protocol definitions
PROTOCOLS=(
    "ssh:SSH protocol with channelized operations"
    "scp:SCP file transfer with indexed channels"
    "rsync:rsync synchronization with channelized operations"
    "sftp:SFTP file operations with indexed channels"
    "oauth:OAuth 2.0/2.1 authentication with PKCE"
    "tls:TLS 1.3 security with channelized handshake"
    "bittorrent:BitTorrent peer wire with indexed channels"
    "couchdb:CouchDB storage with channelized operations"
    "http:HTTP transport with indexed channels"
    "quic:QUIC transport with stream multiplexing"
)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Help function
show_help() {
    echo "TrikeShed Protocol Launcher (trl1.3)"
    echo ""
    echo "Usage: $0 <protocol> [options]"
    echo ""
    echo "Available protocols:"
    for protocol in "${PROTOCOLS[@]}"; do
        IFS=':' read -r name description <<< "$protocol"
        printf "  %-12s %s\n" "$name" "$description"
    done
    echo ""
    echo "Examples:"
    echo "  $0 ssh user@hostname"
    echo "  $0 scp localfile user@hostname:/remote/path"
    echo "  $0 rsync -avz /local/dir/ user@hostname:/remote/dir/"
    echo "  $0 sftp user@hostname"
    echo "  $0 oauth --client-id=myapp --scope=openid,profile"
    echo "  $0 tls --cert=server.crt --key=server.key"
    echo "  $0 bittorrent --torrent=file.torrent"
    echo "  $0 couchdb --database=mydb --operation=get"
    echo "  $0 http --method=GET --url=https://api.example.com"
    echo "  $0 quic --host=example.com --port=443"
    echo ""
    echo "Global options:"
    echo "  --help, -h          Show this help message"
    echo "  --version, -v       Show version information"
    echo "  --debug, -d         Enable debug mode"
    echo "  --channelized, -c   Force channelized mode"
    echo "  --ccek, -k          Enable CCek FSM integration"
    echo "  --tdd, -t           Run TDD tests for protocol"
    echo ""
}

# Version function
show_version() {
    echo "TrikeShed Protocol Launcher v1.3.0"
    echo "Channelized protocols with CCek FSM integration"
    echo "TDD-driven development with indexed channels"
}

# Build function
build_protocols() {
    log_info "Building TrikeShed protocols..."
    
    cd "$PROJECT_ROOT"
    
    # Build all protocol modules
    ./gradlew clean build -x test
    
    # Build specific protocol modules
    ./gradlew :SSH:build
    ./gradlew :trikeshed-oauth:build
    ./gradlew :trikeshed-couchdb:build
    ./gradlew :trikeshed-torrent:build
    ./gradlew :trikeshed-http:build
    ./gradlew :trikeshed-quic:build
    
    log_success "Protocols built successfully"
}

# Create symlinks function
create_symlinks() {
    log_info "Creating protocol symlinks..."
    
    mkdir -p "$BIN_DIR"
    
    for protocol in "${PROTOCOLS[@]}"; do
        IFS=':' read -r name description <<< "$protocol"
        symlink_path="$BIN_DIR/$name"
        
        if [ ! -L "$symlink_path" ]; then
            ln -sf "$SCRIPT_DIR/trike-protocol-launcher.sh" "$symlink_path"
            log_success "Created symlink: $name"
        else
            log_info "Symlink already exists: $name"
        fi
    done
    
    log_success "Protocol symlinks created in $BIN_DIR"
}

# Protocol handlers
handle_ssh() {
    log_info "Executing SSH protocol with channelized operations..."
    
    # Build SSH context
    local user_host="${1:-}"
    if [ -z "$user_host" ]; then
        log_error "SSH requires user@hostname"
        exit 1
    fi
    
    # Execute SSH with channelized operations
    cd "$PROJECT_ROOT"
    ./gradlew :SSH:run --args="connect $user_host --channelized --ccek"
}

handle_scp() {
    log_info "Executing SCP protocol with indexed channels..."
    
    local source="${1:-}"
    local destination="${2:-}"
    
    if [ -z "$source" ] || [ -z "$destination" ]; then
        log_error "SCP requires source and destination"
        exit 1
    fi
    
    # Execute SCP with indexed channels
    cd "$PROJECT_ROOT"
    ./gradlew :SSH:run --args="scp $source $destination --channelized --indexed"
}

handle_rsync() {
    log_info "Executing rsync protocol with channelized operations..."
    
    # Execute rsync with channelized operations
    cd "$PROJECT_ROOT"
    ./gradlew :SSH:run --args="rsync $* --channelized --ccek"
}

handle_sftp() {
    log_info "Executing SFTP protocol with indexed channels..."
    
    local user_host="${1:-}"
    if [ -z "$user_host" ]; then
        log_error "SFTP requires user@hostname"
        exit 1
    fi
    
    # Execute SFTP with indexed channels
    cd "$PROJECT_ROOT"
    ./gradlew :SSH:run --args="sftp $user_host --channelized --indexed"
}

handle_oauth() {
    log_info "Executing OAuth 2.0/2.1 protocol with PKCE..."
    
    # Parse OAuth options
    local client_id=""
    local client_secret=""
    local redirect_uri=""
    local scope=""
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --client-id=*)
                client_id="${1#*=}"
                shift
                ;;
            --client-secret=*)
                client_secret="${1#*=}"
                shift
                ;;
            --redirect-uri=*)
                redirect_uri="${1#*=}"
                shift
                ;;
            --scope=*)
                scope="${1#*=}"
                shift
                ;;
            *)
                break
                ;;
        esac
    done
    
    # Execute OAuth with channelized operations
    cd "$PROJECT_ROOT"
    ./gradlew :trikeshed-oauth:run --args="auth --client-id=$client_id --client-secret=$client_secret --redirect-uri=$redirect_uri --scope=$scope --channelized --pkce"
}

handle_tls() {
    log_info "Executing TLS 1.3 protocol with channelized handshake..."
    
    # Parse TLS options
    local cert=""
    local key=""
    local port="443"
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --cert=*)
                cert="${1#*=}"
                shift
                ;;
            --key=*)
                key="${1#*=}"
                shift
                ;;
            --port=*)
                port="${1#*=}"
                shift
                ;;
            *)
                break
                ;;
        esac
    done
    
    # Execute TLS with channelized operations
    cd "$PROJECT_ROOT"
    ./gradlew :trikeshed-http:run --args="tls --cert=$cert --key=$key --port=$port --channelized --tls13"
}

handle_bittorrent() {
    log_info "Executing BitTorrent protocol with indexed channels..."
    
    # Parse BitTorrent options
    local torrent=""
    local port="6881"
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --torrent=*)
                torrent="${1#*=}"
                shift
                ;;
            --port=*)
                port="${1#*=}"
                shift
                ;;
            *)
                break
                ;;
        esac
    done
    
    # Execute BitTorrent with indexed channels
    cd "$PROJECT_ROOT"
    ./gradlew :trikeshed-torrent:run --args="download --torrent=$torrent --port=$port --channelized --indexed"
}

handle_couchdb() {
    log_info "Executing CouchDB protocol with channelized operations..."
    
    # Parse CouchDB options
    local database=""
    local operation=""
    local document=""
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --database=*)
                database="${1#*=}"
                shift
                ;;
            --operation=*)
                operation="${1#*=}"
                shift
                ;;
            --document=*)
                document="${1#*=}"
                shift
                ;;
            *)
                break
                ;;
        esac
    done
    
    # Execute CouchDB with channelized operations
    cd "$PROJECT_ROOT"
    ./gradlew :trikeshed-couchdb:run --args="$operation --database=$database --document=$document --channelized --ccek"
}

handle_http() {
    log_info "Executing HTTP protocol with indexed channels..."
    
    # Parse HTTP options
    local method="GET"
    local url=""
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --method=*)
                method="${1#*=}"
                shift
                ;;
            --url=*)
                url="${1#*=}"
                shift
                ;;
            *)
                break
                ;;
        esac
    done
    
    # Execute HTTP with indexed channels
    cd "$PROJECT_ROOT"
    ./gradlew :trikeshed-http:run --args="request --method=$method --url=$url --channelized --indexed"
}

handle_quic() {
    log_info "Executing QUIC protocol with stream multiplexing..."
    
    # Parse QUIC options
    local host=""
    local port="443"
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --host=*)
                host="${1#*=}"
                shift
                ;;
            --port=*)
                port="${1#*=}"
                shift
                ;;
            *)
                break
                ;;
        esac
    done
    
    # Execute QUIC with stream multiplexing
    cd "$PROJECT_ROOT"
    ./gradlew :trikeshed-quic:run --args="connect --host=$host --port=$port --channelized --multiplexed"
}

# Run TDD tests function
run_tdd_tests() {
    log_info "Running TDD tests for all protocols..."
    
    cd "$PROJECT_ROOT"
    
    # Run protocol TDD tests
    ./gradlew test --tests "tests.tdd.ProtocolChannelizedTDDTest" --info
    
    log_success "TDD tests completed"
}

# Main function
main() {
    # Parse global options
    local debug=false
    local channelized=false
    local ccek=false
    local tdd=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --help|-h)
                show_help
                exit 0
                ;;
            --version|-v)
                show_version
                exit 0
                ;;
            --debug|-d)
                debug=true
                shift
                ;;
            --channelized|-c)
                channelized=true
                shift
                ;;
            --ccek|-k)
                ccek=true
                shift
                ;;
            --tdd|-t)
                tdd=true
                shift
                ;;
            --build|-b)
                build_protocols
                exit 0
                ;;
            --symlinks|-s)
                create_symlinks
                exit 0
                ;;
            *)
                break
                ;;
        esac
    done
    
    # Set debug mode
    if [ "$debug" = true ]; then
        set -x
    fi
    
    # Get protocol name
    local protocol="${1:-}"
    
    if [ -z "$protocol" ]; then
        log_error "No protocol specified"
        show_help
        exit 1
    fi
    
    # Check if protocol exists
    local protocol_found=false
    for p in "${PROTOCOLS[@]}"; do
        IFS=':' read -r name description <<< "$p"
        if [ "$name" = "$protocol" ]; then
            protocol_found=true
            break
        fi
    done
    
    if [ "$protocol_found" = false ]; then
        log_error "Unknown protocol: $protocol"
        show_help
        exit 1
    fi
    
    # Run TDD tests if requested
    if [ "$tdd" = true ]; then
        run_tdd_tests
    fi
    
    # Execute protocol handler
    case $protocol in
        ssh)
            handle_ssh "${@:2}"
            ;;
        scp)
            handle_scp "${@:2}"
            ;;
        rsync)
            handle_rsync "${@:2}"
            ;;
        sftp)
            handle_sftp "${@:2}"
            ;;
        oauth)
            handle_oauth "${@:2}"
            ;;
        tls)
            handle_tls "${@:2}"
            ;;
        bittorrent)
            handle_bittorrent "${@:2}"
            ;;
        couchdb)
            handle_couchdb "${@:2}"
            ;;
        http)
            handle_http "${@:2}"
            ;;
        quic)
            handle_quic "${@:2}"
            ;;
        *)
            log_error "Protocol handler not implemented: $protocol"
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@" 