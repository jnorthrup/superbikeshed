#include "kqueue.h"
#include <sys/socket.h>
#include <string.h>
#include <pthread.h>

typedef struct {
    int kq;
    kqueue_op_state_t* operations;
    int operation_capacity;
    int operation_count;
    pthread_mutex_t mutex;
} kqueue_context_t;

KqueueContextNative kqueue_setup_context(unsigned int entries, unsigned int flags) {
    kqueue_context_t* ctx = (kqueue_context_t*)malloc(sizeof(kqueue_context_t));
    if (!ctx) {
        fprintf(stderr, "Failed to allocate memory for kqueue context\n");
        return NULL;
    }
    
    memset(ctx, 0, sizeof(kqueue_context_t));
    
    ctx->kq = kqueue();
    if (ctx->kq < 0) {
        fprintf(stderr, "kqueue() failed: %s\n", strerror(errno));
        free(ctx);
        return NULL;
    }
    
    ctx->operation_capacity = entries;
    ctx->operations = (kqueue_op_state_t*)calloc(entries, sizeof(kqueue_op_state_t));
    if (!ctx->operations) {
        close(ctx->kq);
        free(ctx);
        return NULL;
    }
    
    pthread_mutex_init(&ctx->mutex, NULL);
    
    return (KqueueContextNative)ctx;
}

void kqueue_cleanup_context(KqueueContextNative native_ctx) {
    if (!native_ctx) return;
    
    kqueue_context_t* ctx = (kqueue_context_t*)native_ctx;
    
    pthread_mutex_destroy(&ctx->mutex);
    
    if (ctx->kq >= 0) {
        close(ctx->kq);
    }
    
    if (ctx->operations) {
        free(ctx->operations);
    }
    
    free(ctx);
}

int kqueue_set_nonblocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) return -1;
    return fcntl(fd, F_SETFL, flags | O_NONBLOCK);
}

static kqueue_op_state_t* allocate_op_state(kqueue_context_t* ctx) {
    pthread_mutex_lock(&ctx->mutex);
    
    for (int i = 0; i < ctx->operation_capacity; i++) {
        if (ctx->operations[i].user_data == 0) {
            ctx->operation_count++;
            pthread_mutex_unlock(&ctx->mutex);
            return &ctx->operations[i];
        }
    }
    
    pthread_mutex_unlock(&ctx->mutex);
    return NULL;
}

static void free_op_state(kqueue_context_t* ctx, kqueue_op_state_t* op) {
    pthread_mutex_lock(&ctx->mutex);
    memset(op, 0, sizeof(kqueue_op_state_t));
    ctx->operation_count--;
    pthread_mutex_unlock(&ctx->mutex);
}

int kqueue_submit_operations(
    KqueueContextNative native_ctx,
    struct kqueue_operation* ops,
    int op_count
) {
    if (!native_ctx || !ops || op_count <= 0) return -EINVAL;
    
    kqueue_context_t* ctx = (kqueue_context_t*)native_ctx;
    struct kevent* changes = (struct kevent*)malloc(sizeof(struct kevent) * op_count * 2);
    int nchanges = 0;
    
    for (int i = 0; i < op_count; i++) {
        struct kqueue_operation* op = &ops[i];
        kqueue_op_state_t* state = allocate_op_state(ctx);
        if (!state) continue;
        
        // Copy operation data to state
        state->fd = op->fd;
        state->type = op->type;
        state->user_data = op->user_data;
        state->buffer = op->buffer;
        state->buffer_len = op->buffer_len;
        state->iovecs = op->iovecs;
        state->iovec_count = op->iovec_count;
        state->flags = op->flags;
        state->offset = op->offset;
        state->completed = 0;
        
        // Set file descriptor to non-blocking
        kqueue_set_nonblocking(op->fd);
        
        switch (op->type) {
            case OP_READ:
            case OP_READV:
            case OP_RECV:
            case OP_ACCEPT:
                EV_SET(&changes[nchanges++], op->fd, EVFILT_READ, 
                    EV_ADD | EV_ENABLE | EV_ONESHOT, 0, 0, state);
                break;
                
            case OP_WRITE:
            case OP_WRITEV:
            case OP_SEND:
            case OP_CONNECT:
                EV_SET(&changes[nchanges++], op->fd, EVFILT_WRITE,
                    EV_ADD | EV_ENABLE | EV_ONESHOT, 0, 0, state);
                break;
                
            case OP_FSYNC:
                // Use EVFILT_USER for fsync simulation
                EV_SET(&changes[nchanges++], (uintptr_t)state, EVFILT_USER,
                    EV_ADD | EV_ENABLE | EV_ONESHOT, NOTE_TRIGGER, 0, state);
                // Trigger it immediately
                EV_SET(&changes[nchanges++], (uintptr_t)state, EVFILT_USER,
                    0, NOTE_TRIGGER, 0, state);
                break;
                
            case OP_CLOSE:
                // Close can be done immediately
                state->result = close(op->fd);
                state->completed = 1;
                break;
                
            default:
                free_op_state(ctx, state);
                continue;
        }
    }
    
    if (nchanges > 0) {
        int ret = kevent(ctx->kq, changes, nchanges, NULL, 0, NULL);
        free(changes);
        if (ret < 0) {
            fprintf(stderr, "kevent() failed: %s\n", strerror(errno));
            return -errno;
        }
    } else {
        free(changes);
    }
    
    return op_count;
}

static int perform_io_operation(kqueue_op_state_t* op) {
    switch (op->type) {
        case OP_READ:
            return read(op->fd, op->buffer, op->buffer_len);
            
        case OP_READV:
            return readv(op->fd, op->iovecs, op->iovec_count);
            
        case OP_WRITE:
            return write(op->fd, op->buffer, op->buffer_len);
            
        case OP_WRITEV:
            return writev(op->fd, op->iovecs, op->iovec_count);
            
        case OP_RECV:
            return recv(op->fd, op->buffer, op->buffer_len, op->flags);
            
        case OP_SEND:
            return send(op->fd, op->buffer, op->buffer_len, op->flags);
            
        case OP_ACCEPT:
            return accept(op->fd, NULL, NULL);
            
        case OP_CONNECT:
            // Connect result is checked via getsockopt
            {
                int error = 0;
                socklen_t len = sizeof(error);
                if (getsockopt(op->fd, SOL_SOCKET, SO_ERROR, &error, &len) < 0) {
                    return -errno;
                }
                return error ? -error : 0;
            }
            
        case OP_FSYNC:
            return fsync(op->fd);
            
        default:
            return -EINVAL;
    }
}

int kqueue_poll_completions(
    KqueueContextNative native_ctx,
    struct kqueue_completion* completions,
    int max_completions,
    long timeout_ms
) {
    if (!native_ctx || !completions || max_completions <= 0) return -EINVAL;
    
    kqueue_context_t* ctx = (kqueue_context_t*)native_ctx;
    struct kevent* events = (struct kevent*)malloc(sizeof(struct kevent) * max_completions);
    struct timespec ts;
    struct timespec* pts = NULL;
    
    if (timeout_ms >= 0) {
        ts.tv_sec = timeout_ms / 1000;
        ts.tv_nsec = (timeout_ms % 1000) * 1000000;
        pts = &ts;
    }
    
    int completed = 0;
    
    // First check for already completed operations
    pthread_mutex_lock(&ctx->mutex);
    for (int i = 0; i < ctx->operation_capacity && completed < max_completions; i++) {
        kqueue_op_state_t* op = &ctx->operations[i];
        if (op->user_data != 0 && op->completed) {
            completions[completed].user_data = op->user_data;
            completions[completed].result = op->result;
            completed++;
            free_op_state(ctx, op);
        }
    }
    pthread_mutex_unlock(&ctx->mutex);
    
    if (completed >= max_completions) {
        free(events);
        return completed;
    }
    
    // Poll for new events
    int nevents = kevent(ctx->kq, NULL, 0, events, max_completions - completed, pts);
    if (nevents < 0) {
        free(events);
        return -errno;
    }
    
    for (int i = 0; i < nevents && completed < max_completions; i++) {
        struct kevent* ev = &events[i];
        kqueue_op_state_t* op = (kqueue_op_state_t*)ev->udata;
        
        if (!op) continue;
        
        // Perform the actual I/O operation
        op->result = perform_io_operation(op);
        
        completions[completed].user_data = op->user_data;
        completions[completed].result = op->result;
        completed++;
        
        free_op_state(ctx, op);
    }
    
    free(events);
    return completed;
}