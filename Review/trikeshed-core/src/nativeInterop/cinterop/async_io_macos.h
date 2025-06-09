#ifndef ASYNC_IO_MACOS_H
#define ASYNC_IO_MACOS_H

#include <sys/event.h> // For kqueue
#include <stdlib.h>    // For malloc, free
#include <stdio.h>     // For perror
#include <unistd.h>    // For close

// Kqueue file descriptor will be the context for macOS
typedef int AsyncIoContextMacos;

// Shared structures (can be identical to Linux for Kotlin's view if appropriate)
struct kotlin_iovec {
    void *iov_base;
    size_t iov_len;
};

// Operation types (mirroring Linux for consistency from Kotlin)
#define KOTLIN_OP_READV 0
#define KOTLIN_OP_WRITEV 1
#define KOTLIN_OP_FSYNC 2
#define KOTLIN_OP_RECV 3
#define KOTLIN_OP_SEND 4
// ... add more as needed

#define KOTLIN_USER_EVENT_FSYNC_NOTE 0x0001 // Example fflag for our user event

// Structure to represent an operation to be submitted/tracked
// For kqueue, this might be more about what to do when an event fires
struct pending_op_macos {
    int fd;
    int type;
    long user_data;
    long offset;

    struct kotlin_iovec* iovecs; // For ReadV/WriteV
    int iovec_count;

    void* buffer; // For Recv/Send
    size_t buffer_len;
    int flags;    // Network flags

    int fsync_data_only; // For Fsync

    // kqueue specific state if needed (e.g. what filter is active)
    short current_filter; // e.g. EVFILT_READ or EVFILT_WRITE
    // We will likely need a way to link this struct to the kevent's udata field
};

struct completion_result_macos {
    long user_data;
    int result; // Bytes transferred or -errno
};

AsyncIoContextMacos setup_kqueue_context();
void cleanup_kqueue_context(AsyncIoContextMacos kq_fd);

// Placeholders for submission (registering events) and completion (handling events)
int submit_io_operations_macos(AsyncIoContextMacos kq_fd, struct pending_op_macos ops[], int count);
int poll_io_completions_macos(AsyncIoContextMacos kq_fd, struct completion_result_macos* completions, int max_completions, int min_completions, struct timespec* timeout_ts); // timeout_ts can be NULL

#endif // ASYNC_IO_MACOS_H
