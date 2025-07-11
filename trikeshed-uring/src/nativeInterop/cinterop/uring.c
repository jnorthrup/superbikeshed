#include "uring.h"
#include <sys/uio.h>
#include <string.h>

typedef struct {
    struct io_uring ring;
    int initialized;
} uring_context_t;

UringContextNative uring_setup_context(unsigned int entries, unsigned int flags) {
    uring_context_t* ctx = (uring_context_t*)malloc(sizeof(uring_context_t));
    if (!ctx) {
        fprintf(stderr, "Failed to allocate memory for uring context\n");
        return NULL;
    }
    
    memset(ctx, 0, sizeof(uring_context_t));
    
    int ret = io_uring_queue_init(entries, &ctx->ring, flags);
    if (ret < 0) {
        fprintf(stderr, "io_uring_queue_init failed: %s\n", strerror(-ret));
        free(ctx);
        return NULL;
    }
    
    ctx->initialized = 1;
    return (UringContextNative)ctx;
}

void uring_cleanup_context(UringContextNative native_ctx) {
    if (!native_ctx) return;
    
    uring_context_t* ctx = (uring_context_t*)native_ctx;
    if (ctx->initialized) {
        io_uring_queue_exit(&ctx->ring);
    }
    free(ctx);
}

int uring_submit_operations(
    UringContextNative native_ctx,
    struct uring_operation* ops,
    int op_count
) {
    if (!native_ctx || !ops || op_count <= 0) return -EINVAL;
    
    uring_context_t* ctx = (uring_context_t*)native_ctx;
    int submitted = 0;
    
    for (int i = 0; i < op_count; i++) {
        struct uring_operation* op = &ops[i];
        struct io_uring_sqe* sqe = io_uring_get_sqe(&ctx->ring);
        if (!sqe) break;
        
        switch (op->type) {
            case OP_READ:
                io_uring_prep_read(sqe, op->fd, op->buffer, op->buffer_len, op->offset);
                break;
                
            case OP_READV: {
                struct iovec* iovecs = (struct iovec*)malloc(sizeof(struct iovec) * op->iovec_count);
                for (int j = 0; j < op->iovec_count; j++) {
                    iovecs[j].iov_base = op->iovecs[j].iov_base;
                    iovecs[j].iov_len = op->iovecs[j].iov_len;
                }
                io_uring_prep_readv(sqe, op->fd, iovecs, op->iovec_count, op->offset);
                // Store iovecs pointer for cleanup after completion
                sqe->flags |= IOSQE_ASYNC;
                break;
            }
                
            case OP_WRITE:
                io_uring_prep_write(sqe, op->fd, op->buffer, op->buffer_len, op->offset);
                break;
                
            case OP_WRITEV: {
                struct iovec* iovecs = (struct iovec*)malloc(sizeof(struct iovec) * op->iovec_count);
                for (int j = 0; j < op->iovec_count; j++) {
                    iovecs[j].iov_base = op->iovecs[j].iov_base;
                    iovecs[j].iov_len = op->iovecs[j].iov_len;
                }
                io_uring_prep_writev(sqe, op->fd, iovecs, op->iovec_count, op->offset);
                sqe->flags |= IOSQE_ASYNC;
                break;
            }
                
            case OP_FSYNC:
                io_uring_prep_fsync(sqe, op->fd, 
                    op->fsync_data_only ? IORING_FSYNC_DATASYNC : 0);
                break;
                
            case OP_ACCEPT:
                io_uring_prep_accept(sqe, op->fd, NULL, NULL, 0);
                break;
                
            case OP_CONNECT:
                io_uring_prep_connect(sqe, op->fd, 
                    (struct sockaddr*)op->addr, op->addr_len);
                break;
                
            case OP_SEND:
                io_uring_prep_send(sqe, op->fd, op->buffer, op->buffer_len, op->flags);
                break;
                
            case OP_RECV:
                io_uring_prep_recv(sqe, op->fd, op->buffer, op->buffer_len, op->flags);
                break;
                
            case OP_CLOSE:
                io_uring_prep_close(sqe, op->fd);
                break;
                
            default:
                fprintf(stderr, "Unknown operation type: %d\n", op->type);
                continue;
        }
        
        io_uring_sqe_set_data(sqe, (void*)op->user_data);
        submitted++;
    }
    
    if (submitted > 0) {
        int ret = io_uring_submit(&ctx->ring);
        if (ret < 0) {
            fprintf(stderr, "io_uring_submit failed: %s\n", strerror(-ret));
            return ret;
        }
        return ret;
    }
    
    return 0;
}

int uring_poll_completions(
    UringContextNative native_ctx,
    struct uring_completion* completions,
    int max_completions,
    long timeout_ms
) {
    if (!native_ctx || !completions || max_completions <= 0) return -EINVAL;
    
    uring_context_t* ctx = (uring_context_t*)native_ctx;
    struct __kernel_timespec ts;
    struct __kernel_timespec* pts = NULL;
    
    if (timeout_ms >= 0) {
        ts.tv_sec = timeout_ms / 1000;
        ts.tv_nsec = (timeout_ms % 1000) * 1000000;
        pts = &ts;
    }
    
    int completed = 0;
    
    while (completed < max_completions) {
        struct io_uring_cqe* cqe;
        int ret;
        
        if (timeout_ms == 0) {
            ret = io_uring_peek_cqe(&ctx->ring, &cqe);
        } else {
            ret = io_uring_wait_cqe_timeout(&ctx->ring, &cqe, pts);
        }
        
        if (ret < 0) {
            if (ret == -EAGAIN || ret == -ETIME) break;
            return ret;
        }
        
        if (!cqe) break;
        
        completions[completed].user_data = (long)io_uring_cqe_get_data(cqe);
        completions[completed].result = cqe->res;
        completed++;
        
        io_uring_cqe_seen(&ctx->ring, cqe);
        
        // For non-blocking, only check once
        if (timeout_ms == 0) break;
    }
    
    return completed;
}

int uring_register_buffers(
    UringContextNative native_ctx,
    void** buffers,
    int* buffer_lens,
    int buffer_count
) {
    if (!native_ctx || !buffers || !buffer_lens || buffer_count <= 0) return -EINVAL;
    
    uring_context_t* ctx = (uring_context_t*)native_ctx;
    struct iovec* iovecs = (struct iovec*)malloc(sizeof(struct iovec) * buffer_count);
    
    for (int i = 0; i < buffer_count; i++) {
        iovecs[i].iov_base = buffers[i];
        iovecs[i].iov_len = buffer_lens[i];
    }
    
    int ret = io_uring_register_buffers(&ctx->ring, iovecs, buffer_count);
    free(iovecs);
    
    return ret;
}

int uring_register_files(
    UringContextNative native_ctx,
    int* fds,
    int fd_count
) {
    if (!native_ctx || !fds || fd_count <= 0) return -EINVAL;
    
    uring_context_t* ctx = (uring_context_t*)native_ctx;
    return io_uring_register_files(&ctx->ring, fds, fd_count);
}