#include "async_io_linux.h"
#include <errno.h>
#include <string.h> // For memset, strerror
#include <stdio.h>  // For fprintf, stderr, perror
#include <stdlib.h> // For malloc, free
#include <sys/uio.h> // For struct iovec (native)
// liburing.h is included via async_io_linux.h

// Wrapper struct to hold the ring, as we can't directly return struct io_uring by value to Kotlin.
// Kotlin will see this as an opaque pointer (AsyncIoContextLinux).
struct io_uring_context_wrapper {
    struct io_uring ring;
    // We might need to store other context-specific data here later
};

AsyncIoContextLinux setup_io_uring_context(unsigned int entries, unsigned int flags) {
    struct io_uring_context_wrapper* ctx_wrapper = (struct io_uring_context_wrapper*)malloc(sizeof(struct io_uring_context_wrapper));
    if (!ctx_wrapper) {
        perror("Failed to allocate memory for io_uring_context_wrapper");
        return NULL;
    }

    // Initialize io_uring. flags can be 0 or IORING_SETUP_SQPOLL, IORING_SETUP_IOPOLL etc.
    int ret = io_uring_queue_init(entries, &ctx_wrapper->ring, flags);
    if (ret < 0) {
        perror("io_uring_queue_init failed");
        fprintf(stderr, "io_uring_queue_init error: %s (code %d)\n", strerror(-ret), -ret);
        free(ctx_wrapper);
        return NULL;
    }
    return (AsyncIoContextLinux)ctx_wrapper;
}

void cleanup_io_uring_context(AsyncIoContextLinux ctx) {
    if (ctx) {
        struct io_uring_context_wrapper* ctx_wrapper = (struct io_uring_context_wrapper*)ctx;
        io_uring_queue_exit(&ctx_wrapper->ring);
        free(ctx_wrapper);
    }
}

int submit_io_operations_linux(AsyncIoContextLinux ctx, struct submitted_op_linux ops[], int count) {
    if (!ctx || (!ops && count > 0) || count < 0) { // Allow ops to be NULL if count is 0
        return -EINVAL;
    }
    if (count == 0) {
        return 0;
    }

    struct io_uring_context_wrapper* ctx_wrapper = (struct io_uring_context_wrapper*)ctx;
    struct io_uring_sqe *sqe;
    int submitted_this_batch = 0;
    int total_submitted = 0;
    int ret;

    for (int i = 0; i < count; ++i) {
        sqe = io_uring_get_sqe(&ctx_wrapper->ring);
        if (!sqe) {
            // SQ ring is full, submit what we have and try again for the rest
            if (submitted_this_batch > 0) {
                ret = io_uring_submit(&ctx_wrapper->ring);
                if (ret < 0) {
                    fprintf(stderr, "io_uring_submit error in submit_ops (batch): %s\n", strerror(-ret));
                    // Potentially return error, or try to continue? For now, let's return error.
                    return ret;
                }
                total_submitted += ret; // ret should be submitted_this_batch
                submitted_this_batch = 0;
            }
            // Try getting an SQE again after submission
            sqe = io_uring_get_sqe(&ctx_wrapper->ring);
            if (!sqe) {
                fprintf(stderr, "Failed to get SQE even after submit, ring may be too small or too many ops at once.\n");
                break; // Cannot submit more
            }
        }

        struct submitted_op_linux* current_op = &ops[i];
        struct iovec* native_iovecs = (struct iovec*)current_op->iovecs; // Used by ReadV/WriteV

        switch (current_op->type) {
            case KOTLIN_OP_READV:
                if (current_op->iovecs && current_op->iovec_count > 0) {
                    io_uring_prep_readv(sqe, current_op->fd, native_iovecs, current_op->iovec_count, current_op->offset);
                } else {
                    fprintf(stderr, "ReadV operation with no iovecs for fd %d\n", current_op->fd);
                    io_uring_prep_nop(sqe); // Or handle error appropriately
                }
                break;
            case KOTLIN_OP_WRITEV:
                if (current_op->iovecs && current_op->iovec_count > 0) {
                    io_uring_prep_writev(sqe, current_op->fd, native_iovecs, current_op->iovec_count, current_op->offset);
                } else {
                    fprintf(stderr, "WriteV operation with no iovecs for fd %d\n", current_op->fd);
                    io_uring_prep_nop(sqe);
                }
                break;
            case KOTLIN_OP_FSYNC:
                io_uring_prep_fsync(sqe, current_op->fd, current_op->fsync_data_only ? IORING_FSYNC_DATASYNC : 0);
                break;
            case KOTLIN_OP_RECV:
                if (current_op->buffer && current_op->buffer_len > 0) {
                    io_uring_prep_recv(sqe, current_op->fd, current_op->buffer, current_op->buffer_len, current_op->flags);
                } else {
                    fprintf(stderr, "Recv operation with no buffer for fd %d\n", current_op->fd);
                    io_uring_prep_nop(sqe);
                }
                break;
            case KOTLIN_OP_SEND:
                if (current_op->buffer && current_op->buffer_len > 0) {
                    io_uring_prep_send(sqe, current_op->fd, current_op->buffer, current_op->buffer_len, current_op->flags);
                } else {
                    fprintf(stderr, "Send operation with no buffer for fd %d\n", current_op->fd);
                    io_uring_prep_nop(sqe);
                }
                break;
            default:
                fprintf(stderr, "Unsupported operation type: %d for fd %d\n", current_op->type, current_op->fd);
                io_uring_prep_nop(sqe); // Prepare a no-op for unsupported types
                break;
        }
        io_uring_sqe_set_data(sqe, (void*)current_op->user_data);
        submitted_this_batch++;
    }

    if (submitted_this_batch > 0) {
        ret = io_uring_submit(&ctx_wrapper->ring);
        if (ret < 0) {
            fprintf(stderr, "io_uring_submit error in submit_ops (final): %s\n", strerror(-ret));
            return ret; // Or total_submitted if some were processed before error.
        }
        total_submitted += ret;
    }
    return total_submitted;
}

int poll_io_completions_linux(AsyncIoContextLinux ctx, struct completion_result_linux* completions_out, int max_completions, int min_completions, int timeout_millis) {
    if (!ctx || !completions_out || max_completions <= 0 || min_completions < 0) {
        return -EINVAL;
    }
    // Ensure min_completions is not greater than max_completions
    if (min_completions > max_completions) min_completions = max_completions;


    struct io_uring_context_wrapper* ctx_wrapper = (struct io_uring_context_wrapper*)ctx;
    struct io_uring_cqe *cqe;
    int completed_count = 0;
    int ret;

    if (timeout_millis == -1 && min_completions > 0) {
        // Blocking wait for at least min_completions
        for (int i = 0; i < min_completions; ++i) {
            ret = io_uring_wait_cqe(&ctx_wrapper->ring, &cqe);
            if (ret < 0) {
                fprintf(stderr, "io_uring_wait_cqe error: %s\n", strerror(-ret));
                return ret; // Propagate error
            }
            if (completed_count < max_completions) {
                completions_out[completed_count].user_data = (long)io_uring_cqe_get_data(cqe);
                completions_out[completed_count].result = cqe->res;
                completed_count++;
            }
            io_uring_cqe_seen(&ctx_wrapper->ring, cqe); // Mark this one as seen
        }
        // Try to peek for more if available, up to max_completions
        unsigned head;
        int peek_count = 0;
        io_uring_for_each_cqe(&ctx_wrapper->ring, head, cqe) {
            if (completed_count >= max_completions) break;
            completions_out[completed_count].user_data = (long)io_uring_cqe_get_data(cqe);
            completions_out[completed_count].result = cqe->res;
            completed_count++;
            peek_count++;
        }
        if (peek_count > 0) {
            io_uring_cq_advance(&ctx_wrapper->ring, peek_count);
        }

    } else if (timeout_millis >= 0) {
        struct __kernel_timespec ts;
        if (timeout_millis > 0) {
            ts.tv_sec = timeout_millis / 1000;
            ts.tv_nsec = (timeout_millis % 1000) * 1000000;
        }

        if (min_completions > 0 && timeout_millis > 0) {
             // Wait for min_completions with a timeout
            ret = io_uring_submit_and_wait_timeout(&ctx_wrapper->ring, &cqe, min_completions, &ts, NULL);
             if (ret < 0 && ret != -ETIME) { // -ETIME means timeout expired
                fprintf(stderr, "io_uring_submit_and_wait_timeout error: %s\n", strerror(-ret));
                return ret;
            }
        } else if (timeout_millis > 0 && min_completions == 0) { // Timed wait for any completion
            ret = io_uring_wait_cqe_timeout(&ctx_wrapper->ring, &cqe, &ts);
            if (ret < 0 && ret != -ETIME) {
                 fprintf(stderr, "io_uring_wait_cqe_timeout error: %s\n", strerror(-ret));
                 return ret;
            }
        }
        // Non-blocking peek or after a wait
        unsigned head;
        int peek_count = 0;
        io_uring_for_each_cqe(&ctx_wrapper->ring, head, cqe) {
            if (completed_count >= max_completions) break;
            completions_out[completed_count].user_data = (long)io_uring_cqe_get_data(cqe);
            completions_out[completed_count].result = cqe->res;
            completed_count++;
            peek_count++;
        }
        if (peek_count > 0) {
            io_uring_cq_advance(&ctx_wrapper->ring, peek_count);
        }
    }
    // If timeout_millis == 0 (non-blocking) and min_completions == 0, the above peek loop handles it.

    return completed_count;
}
