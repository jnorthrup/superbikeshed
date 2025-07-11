#ifndef TRIKESHED_URING_H
#define TRIKESHED_URING_H

#include <liburing.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>

// Opaque type for Kotlin
typedef void* UringContextNative;

struct uring_iovec {
    void *iov_base;
    size_t iov_len;
};

// Operation types matching Kotlin sealed classes
#define OP_READ      0
#define OP_READV     1
#define OP_WRITE     2
#define OP_WRITEV    3
#define OP_FSYNC     4
#define OP_ACCEPT    5
#define OP_CONNECT   6
#define OP_SEND      7
#define OP_RECV      8
#define OP_CLOSE     9

struct uring_operation {
    int fd;
    int type;
    long user_data;
    long offset;
    
    // For ReadV/WriteV
    struct uring_iovec* iovecs;
    int iovec_count;
    
    // For Read/Write/Send/Recv
    void* buffer;
    size_t buffer_len;
    int flags;
    
    // For Fsync
    int fsync_data_only;
    
    // For Connect
    void* addr;
    socklen_t addr_len;
};

struct uring_completion {
    long user_data;
    int result;
};

// Main API functions
UringContextNative uring_setup_context(unsigned int entries, unsigned int flags);
void uring_cleanup_context(UringContextNative ctx);

int uring_submit_operations(
    UringContextNative ctx,
    struct uring_operation* ops,
    int op_count
);

int uring_poll_completions(
    UringContextNative ctx,
    struct uring_completion* completions,
    int max_completions,
    long timeout_ms
);

// Buffer and file registration
int uring_register_buffers(
    UringContextNative ctx,
    void** buffers,
    int* buffer_lens,
    int buffer_count
);

int uring_register_files(
    UringContextNative ctx,
    int* fds,
    int fd_count
);

#endif // TRIKESHED_URING_H