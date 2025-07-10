/*
 * liburing compatible interface for Darwin/macOS
 * 
 * This header provides the EXACT same API as Linux liburing,
 * but backed by kqueue on Darwin. Any code written for liburing
 * can include this header and link against our Kotlin Native library!
 */

#ifndef LIBURING_COMPAT_H
#define LIBURING_COMPAT_H

#ifdef __cplusplus
extern "C" {
#endif

#include <sys/types.h>
#include <sys/socket.h>
#include <stdint.h>

/*
 * IO operation opcodes - match Linux exactly
 */
#define IORING_OP_NOP           0
#define IORING_OP_READV         1
#define IORING_OP_WRITEV        2
#define IORING_OP_FSYNC         3
#define IORING_OP_READ_FIXED    4
#define IORING_OP_WRITE_FIXED   5
#define IORING_OP_POLL_ADD      6
#define IORING_OP_POLL_REMOVE   7
#define IORING_OP_SYNC_FILE_RANGE 8
#define IORING_OP_SENDMSG       9
#define IORING_OP_RECVMSG       10
#define IORING_OP_TIMEOUT       11
#define IORING_OP_TIMEOUT_REMOVE 12
#define IORING_OP_ACCEPT        13
#define IORING_OP_ASYNC_CANCEL  14
#define IORING_OP_LINK_TIMEOUT  15
#define IORING_OP_CONNECT       16
#define IORING_OP_FALLOCATE     17
#define IORING_OP_OPENAT        18
#define IORING_OP_CLOSE         19
#define IORING_OP_FILES_UPDATE  20
#define IORING_OP_STATX         21
#define IORING_OP_READ          22
#define IORING_OP_WRITE         23

/*
 * sqe->flags
 */
#define IOSQE_FIXED_FILE        (1U << 0)
#define IOSQE_IO_DRAIN          (1U << 1)
#define IOSQE_IO_LINK           (1U << 2)
#define IOSQE_IO_HARDLINK       (1U << 3)
#define IOSQE_ASYNC             (1U << 4)
#define IOSQE_BUFFER_SELECT     (1U << 5)

/*
 * io_uring_setup() flags
 */
#define IORING_SETUP_IOPOLL     (1U << 0)
#define IORING_SETUP_SQPOLL     (1U << 1)
#define IORING_SETUP_SQ_AFF     (1U << 2)
#define IORING_SETUP_CQSIZE     (1U << 3)
#define IORING_SETUP_CLAMP      (1U << 4)
#define IORING_SETUP_ATTACH_WQ  (1U << 5)
#define IORING_SETUP_R_DISABLED (1U << 6)

/*
 * IO submission data structure (Submission Queue Entry)
 */
struct io_uring_sqe {
    uint8_t  opcode;        /* type of operation for this sqe */
    uint8_t  flags;         /* IOSQE_ flags */
    uint16_t ioprio;        /* ioprio for the request */
    int32_t  fd;            /* file descriptor to do IO on */
    union {
        uint64_t off;       /* offset into file */
        uint64_t addr2;
    };
    union {
        uint64_t addr;      /* pointer to buffer or iovecs */
        uint64_t splice_off_in;
    };
    uint32_t len;           /* buffer size or number of iovecs */
    union {
        int32_t  rw_flags;
        uint32_t fsync_flags;
        uint16_t poll_events;
        uint32_t poll32_events;
        uint32_t sync_range_flags;
        uint32_t msg_flags;
        uint32_t timeout_flags;
        uint32_t accept_flags;
        uint32_t cancel_flags;
        uint32_t open_flags;
        uint32_t statx_flags;
        uint32_t fadvise_advice;
        uint32_t splice_flags;
        uint32_t rename_flags;
        uint32_t unlink_flags;
        uint32_t hardlink_flags;
    };
    uint64_t user_data;     /* data to be passed back at completion */
    union {
        uint16_t buf_index;
        uint16_t buf_group;
    };
    uint16_t personality;
    union {
        int32_t splice_fd_in;
        uint32_t file_index;
    };
    uint64_t __pad2[2];
};

/*
 * IO completion data structure (Completion Queue Entry)
 */
struct io_uring_cqe {
    uint64_t user_data;     /* sqe->data submission passed back */
    int32_t  res;           /* result code for this event */
    uint32_t flags;
};

/*
 * Ring buffer offsets for SQ/CQ
 */
struct io_sqring_offsets {
    uint32_t head;
    uint32_t tail;
    uint32_t ring_mask;
    uint32_t ring_entries;
    uint32_t flags;
    uint32_t dropped;
    uint32_t array;
    uint32_t resv1;
    uint64_t resv2;
};

struct io_cqring_offsets {
    uint32_t head;
    uint32_t tail;
    uint32_t ring_mask;
    uint32_t ring_entries;
    uint32_t overflow;
    uint32_t cqes;
    uint32_t flags;
    uint32_t resv1;
    uint64_t resv2;
};

/*
 * io_uring setup parameters
 */
struct io_uring_params {
    uint32_t sq_entries;
    uint32_t cq_entries;
    uint32_t flags;
    uint32_t sq_thread_cpu;
    uint32_t sq_thread_idle;
    uint32_t features;
    uint32_t wq_fd;
    uint32_t resv[3];
    struct io_sqring_offsets sq_off;
    struct io_cqring_offsets cq_off;
};

/*
 * Library internal representation of SQ/CQ rings
 */
struct io_uring_sq {
    unsigned *khead;
    unsigned *ktail;
    unsigned *kring_mask;
    unsigned *kring_entries;
    unsigned *kflags;
    unsigned *kdropped;
    unsigned *array;
    struct io_uring_sqe *sqes;
    
    unsigned sqe_head;
    unsigned sqe_tail;
    
    size_t ring_sz;
    void *ring_ptr;
};

struct io_uring_cq {
    unsigned *khead;
    unsigned *ktail;
    unsigned *kring_mask;
    unsigned *kring_entries;
    unsigned *kflags;
    unsigned *koverflow;
    struct io_uring_cqe *cqes;
    
    size_t ring_sz;
    void *ring_ptr;
};

/*
 * Main io_uring structure
 */
struct io_uring {
    struct io_uring_sq sq;
    struct io_uring_cq cq;
    unsigned flags;
    int ring_fd;
    unsigned features;
    int *id;  /* Internal state ID for Darwin */
    unsigned pad[3];
};

/*
 * Library interface functions - EXACT liburing API
 */

/* Setup and teardown */
int io_uring_queue_init(unsigned entries, struct io_uring *ring, unsigned flags);
int io_uring_queue_init_params(unsigned entries, struct io_uring *ring,
                               struct io_uring_params *p);
void io_uring_queue_exit(struct io_uring *ring);

/* Submission */
struct io_uring_sqe *io_uring_get_sqe(struct io_uring *ring);
int io_uring_submit(struct io_uring *ring);
int io_uring_submit_and_wait(struct io_uring *ring, unsigned wait_nr);

/* Completion */
int io_uring_wait_cqe(struct io_uring *ring, struct io_uring_cqe **cqe_ptr);
int io_uring_peek_cqe(struct io_uring *ring, struct io_uring_cqe **cqe_ptr);
int io_uring_wait_cqes(struct io_uring *ring, struct io_uring_cqe **cqe_ptr,
                       unsigned wait_nr, struct __kernel_timespec *ts,
                       sigset_t *sigmask);

/* Advanced */
void io_uring_cqe_seen(struct io_uring *ring, struct io_uring_cqe *cqe);
unsigned io_uring_cq_ready(struct io_uring *ring);
unsigned io_uring_sq_ready(struct io_uring *ring);
unsigned io_uring_sq_space_left(struct io_uring *ring);

/* Helpers for preparing SQEs */
void io_uring_prep_nop(struct io_uring_sqe *sqe);
void io_uring_prep_read(struct io_uring_sqe *sqe, int fd, void *buf,
                        unsigned nbytes, uint64_t offset);
void io_uring_prep_write(struct io_uring_sqe *sqe, int fd, const void *buf,
                         unsigned nbytes, uint64_t offset);
void io_uring_prep_readv(struct io_uring_sqe *sqe, int fd,
                         const struct iovec *iovecs, unsigned nr_vecs,
                         uint64_t offset);
void io_uring_prep_writev(struct io_uring_sqe *sqe, int fd,
                          const struct iovec *iovecs, unsigned nr_vecs,
                          uint64_t offset);
void io_uring_prep_accept(struct io_uring_sqe *sqe, int fd,
                          struct sockaddr *addr, socklen_t *addrlen,
                          int flags);
void io_uring_prep_connect(struct io_uring_sqe *sqe, int fd,
                           const struct sockaddr *addr, socklen_t addrlen);
void io_uring_prep_timeout(struct io_uring_sqe *sqe,
                           struct __kernel_timespec *ts, unsigned count,
                           unsigned flags);
void io_uring_prep_fsync(struct io_uring_sqe *sqe, int fd, unsigned flags);

/* Data association */
void io_uring_sqe_set_data(struct io_uring_sqe *sqe, void *data);
void *io_uring_cqe_get_data(const struct io_uring_cqe *cqe);

/* Advanced features */
int io_uring_register_buffers(struct io_uring *ring, const struct iovec *iovecs,
                              unsigned nr_iovecs);
int io_uring_unregister_buffers(struct io_uring *ring);
int io_uring_register_files(struct io_uring *ring, const int *files,
                            unsigned nr_files);
int io_uring_unregister_files(struct io_uring *ring);

#ifdef __cplusplus
}
#endif

#endif /* LIBURING_COMPAT_H */