#ifndef TRIKESHED_KQUEUE_H
#define TRIKESHED_KQUEUE_H

#include <sys/types.h>
#include <sys/event.h>
#include <sys/time.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>

// Opaque type for Kotlin
typedef void* KqueueContextNative;

// Operation types (matching Linux for consistency)
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

// Operation state for kqueue
typedef struct kqueue_op_state {
    int fd;
    int type;
    long user_data;
    void* buffer;
    size_t buffer_len;
    struct iovec* iovecs;
    int iovec_count;
    int flags;
    long offset;
    int completed;
    int result;
} kqueue_op_state_t;

struct kqueue_operation {
    int fd;
    int type;
    long user_data;
    long offset;
    
    // For ReadV/WriteV
    struct iovec* iovecs;
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

struct kqueue_completion {
    long user_data;
    int result;
};

// Main API functions
KqueueContextNative kqueue_setup_context(unsigned int entries, unsigned int flags);
void kqueue_cleanup_context(KqueueContextNative ctx);

int kqueue_submit_operations(
    KqueueContextNative ctx,
    struct kqueue_operation* ops,
    int op_count
);

int kqueue_poll_completions(
    KqueueContextNative ctx,
    struct kqueue_completion* completions,
    int max_completions,
    long timeout_ms
);

// Utility functions
int kqueue_set_nonblocking(int fd);

#endif // TRIKESHED_KQUEUE_H