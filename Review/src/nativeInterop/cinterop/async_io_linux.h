#ifndef ASYNC_IO_LINUX_H
#define ASYNC_IO_LINUX_H

#include <liburing.h>
#include <stdlib.h> // For malloc, free
#include <stdio.h>  // For perror

// Opaque type for Kotlin, actual type is struct io_uring*
typedef void* AsyncIoContextLinux;

struct kotlin_iovec {
    void *iov_base;
    size_t iov_len;
};

// Operation types (to be expanded)
#define KOTLIN_OP_READV 0
#define KOTLIN_OP_WRITEV 1
#define KOTLIN_OP_FSYNC 2
#define KOTLIN_OP_RECV 3
#define KOTLIN_OP_SEND 4
// ... add more as needed

struct submitted_op_linux {
    int fd;
    int type;
    long user_data; // To match back with Kotlin IoOperation
    long offset;

    // For ReadV/WriteV
    struct kotlin_iovec* iovecs;
    int iovec_count;

    // For Recv/Send
    void* buffer;
    size_t buffer_len;
    int flags;

    // For Fsync
    int fsync_data_only;
};

struct completion_result_linux {
    long user_data;
    int result; // result from cqe.res
    // int flags; // cqe.flags - consider adding if needed by Kotlin layer
};

AsyncIoContextLinux setup_io_uring_context(unsigned int entries, unsigned int flags);
void cleanup_io_uring_context(AsyncIoContextLinux ctx);

// Placeholders for submission and completion logic
int submit_io_operations_linux(AsyncIoContextLinux ctx, struct submitted_op_linux ops[], int count);
int poll_io_completions_linux(AsyncIoContextLinux ctx, struct completion_result_linux* completions, int max_completions, int min_completions, int timeout_millis);

#endif // ASYNC_IO_LINUX_H
