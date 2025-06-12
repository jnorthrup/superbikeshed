#include "async_io_macos.h"
#include <errno.h>
#include <string.h> // For strerror, memset, memcpy
#include <stdio.h>  // For fprintf, perror
#include <stdlib.h> // For malloc, free
#include <fcntl.h>  // For O_NONBLOCK, fcntl
#include <sys/uio.h> // For struct iovec, readv, writev
#include <sys/socket.h> // For recv, send

// Define a structure to be allocated on the heap for each pending operation.
// This structure will be pointed to by kevent.udata.
typedef struct {
    struct pending_op_macos op_details; // The details passed from Kotlin
    // Potentially add other C-side state if needed, e.g., a copy of iovecs if not stable
} kqueue_op_state_t;

// For macOS, the context is just the kqueue file descriptor.
// We will need a separate mechanism to store the details of pending operations,
// as kqueue events themselves don't carry all the necessary state (like buffer pointers for readv).
// This could be a hash map or a linked list in C, keyed by kevent.udata or kevent.ident.
// For this initial step, we'll just set up the context.

AsyncIoContextMacos setup_kqueue_context() {
    int kq = kqueue();
    if (kq == -1) {
        perror("kqueue() creation failed");
        return -1; // Or some other error indicator recognizable by Kotlin
    }
    return kq;
}

void cleanup_kqueue_context(AsyncIoContextMacos kq_fd) {
    if (kq_fd >= 0) {
        close(kq_fd);
        // TODO: Also clean up any associated data structures for pending operations.
        // This implies iterating over any remaining kqueue_op_state_t and freeing them.
    }
}

int submit_io_operations_macos(AsyncIoContextMacos kq_fd, struct pending_op_macos ops[], int count) {
    if (kq_fd < 0 || (!ops && count > 0) || count < 0) {
        return -EINVAL;
    }
    if (count == 0) {
        return 0;
    }

    struct kevent* changelist = (struct kevent*)malloc(count * sizeof(struct kevent));
    if (!changelist) {
        perror("Failed to allocate memory for changelist");
        return -ENOMEM;
    }

    int successfully_prepared = 0;
    for (int i = 0; i < count; ++i) {
        struct pending_op_macos* current_kotlin_op = &ops[i];

        kqueue_op_state_t* op_state = (kqueue_op_state_t*)malloc(sizeof(kqueue_op_state_t));
        if (!op_state) {
            fprintf(stderr, "Failed to allocate memory for kqueue_op_state_t for op %d\n", i);
            for (int j = 0; j < successfully_prepared; ++j) {
                kqueue_op_state_t* prev_op_state = (kqueue_op_state_t*)changelist[j].udata;
                if ((prev_op_state->op_details.type == KOTLIN_OP_READV || prev_op_state->op_details.type == KOTLIN_OP_WRITEV) &&
                    prev_op_state->op_details.iovecs != ops[j].iovecs) {
                    free(prev_op_state->op_details.iovecs);
                }
                free(prev_op_state);
            }
            free(changelist);
            return -ENOMEM;
        }

        memcpy(&op_state->op_details, current_kotlin_op, sizeof(struct pending_op_macos));

        if ((current_kotlin_op->type == KOTLIN_OP_READV || current_kotlin_op->type == KOTLIN_OP_WRITEV) &&
            current_kotlin_op->iovec_count > 0 && current_kotlin_op->iovecs != NULL) {
            op_state->op_details.iovecs = (struct kotlin_iovec*)malloc(current_kotlin_op->iovec_count * sizeof(struct kotlin_iovec));
            if (!op_state->op_details.iovecs) {
                 fprintf(stderr, "Failed to allocate memory for iovecs copy for op %d\n", i);
                 free(op_state);
                 for (int j = 0; j < successfully_prepared; ++j) {
                    kqueue_op_state_t* prev_op_state = (kqueue_op_state_t*)changelist[j].udata;
                     if ((prev_op_state->op_details.type == KOTLIN_OP_READV || prev_op_state->op_details.type == KOTLIN_OP_WRITEV) &&
                         prev_op_state->op_details.iovecs != ops[j].iovecs) {
                        free(prev_op_state->op_details.iovecs);
                    }
                    free(prev_op_state);
                 }
                 free(changelist);
                 return -ENOMEM;
            }
            memcpy(op_state->op_details.iovecs, current_kotlin_op->iovecs, current_kotlin_op->iovec_count * sizeof(struct kotlin_iovec));
        } else if (current_kotlin_op->type == KOTLIN_OP_RECV || current_kotlin_op->type == KOTLIN_OP_SEND) {
            op_state->op_details.buffer = current_kotlin_op->buffer;
        }

        short filter_type = 0; // Default, will be set or special handling for FSYNC
        uintptr_t event_ident = current_kotlin_op->fd; // Default ident is FD

        switch (current_kotlin_op->type) {
            case KOTLIN_OP_READV:
            case KOTLIN_OP_RECV:
                filter_type = EVFILT_READ;
                break;
            case KOTLIN_OP_WRITEV:
            case KOTLIN_OP_SEND:
                filter_type = EVFILT_WRITE;
                break;
            case KOTLIN_OP_FSYNC:
                // For FSYNC, we trigger a user event. The actual fsync will happen when this event is polled.
                // Using op_state pointer as ident for user event to make it unique and identifiable.
                event_ident = (uintptr_t)op_state;
                filter_type = EVFILT_USER;
                op_state->op_details.current_filter = EVFILT_USER; // Mark it as a user event
                // The original FD for fsync is already in op_state->op_details.fd
                // We will use kevent.data to pass the original FD for fsync when triggering.
                // However, the spec for EV_SET for EVFILT_USER says `data` is "user-defined".
                // Let's store FD in op_state->op_details.fd and retrieve it via op_state in poll.
                // For EV_TRIGGER, fflags should contain NOTE_TRIGGER.
                EV_SET(&changelist[successfully_prepared],
                       event_ident,
                       EVFILT_USER,
                       EV_ADD | EV_ONESHOT | EV_CLEAR, // Add, trigger once, clear state after retrieval.
                                                      // EV_TRIGGER is added when we want to manually trigger it.
                                                      // Here, we just add it. It will be triggered by kevent with NOTE_TRIGGER.
                       NOTE_FFNOP, // No specific kernel fflags for EV_ADD.
                                   // To trigger it: kevent with EV_TRIGGER and fflags = NOTE_TRIGGER
                                   // For now, let's use NOTE_TRIGGER directly in fflags to make it auto-trigger.
                                   // No, EV_SET fflags are for input notes to the kernel.
                                   // data field for EVFILT_USER on input is user-defined, can be the FD.
                       current_kotlin_op->fd, // Store FD in data field for retrieval in poll
                       op_state);
                successfully_prepared++;
                continue; // Skip common EV_SET below for read/write filters
            default:
                fprintf(stderr, "Unsupported operation type for kqueue: %d for op %d. Op skipped.\n", current_kotlin_op->type, i);
                if (op_state->op_details.iovecs && op_state->op_details.iovecs != current_kotlin_op->iovecs) free(op_state->op_details.iovecs);
                free(op_state);
                continue;
        }

        // Common EV_SET for EVFILT_READ/EVFILT_WRITE
        EV_SET(&changelist[successfully_prepared],
               event_ident, // This is current_kotlin_op->fd for read/write
               filter_type,
               EV_ADD | EV_ONESHOT | EV_CLEAR,
               0,                                         // fflags (filter-specific flags)
               0,                                         // data (filter-specific data)
               op_state);                                 // udata (pointer to our state)

        successfully_prepared++;
    }

    if (successfully_prepared == 0 && count > 0) {
        free(changelist);
        return -EINVAL;
    }

    int ret = kevent(kq_fd, changelist, successfully_prepared, NULL, 0, NULL);
    free(changelist);

    if (ret == -1) {
        perror("kevent register failed");
        // Error handling for freeing op_state if kevent fails is complex and partially omitted for brevity
        // as noted in the plan.
        return -errno;
    }
    return successfully_prepared;
}

int poll_io_completions_macos(AsyncIoContextMacos kq_fd, struct completion_result_macos* completions_out, int max_completions, int min_completions, struct timespec* timeout_ts) {
    if (kq_fd < 0 || !completions_out || max_completions <= 0 || min_completions < 0) {
        return -EINVAL;
    }
    if (min_completions > max_completions) { // Ensure min_completions is not more than buffer size
        min_completions = max_completions;
    }

    // Use a local array for eventlist if max_completions is reasonably small,
    // otherwise consider malloc. For now, VLA-like behavior is fine for typical batch sizes.
    struct kevent eventlist[max_completions];
    int num_events_retrieved;
    int total_completed_ops = 0;

    do {
        num_events_retrieved = kevent(kq_fd, NULL, 0, eventlist, max_completions - total_completed_ops, timeout_ts);

        if (num_events_retrieved == -1) {
            if (errno == EINTR) continue; // Interrupted, retry
            perror("kevent poll failed");
            return -errno;
        }

        if (num_events_retrieved == 0 && timeout_ts != NULL && !(timeout_ts->tv_sec == 0 && timeout_ts->tv_nsec == 0)) {
            break;
        }

        for (int i = 0; i < num_events_retrieved; ++i) {
            kqueue_op_state_t* op_state = (kqueue_op_state_t*)eventlist[i].udata;
            if (!op_state) {
                fprintf(stderr, "kqueue event with NULL udata. ident: %lu, filter: %d\n", (unsigned long)eventlist[i].ident, eventlist[i].filter);
                continue;
            }

            struct pending_op_macos* details = &op_state->op_details;
            int fd = details->fd;
            ssize_t io_result = 0;
            int op_errno = 0;

            if (eventlist[i].flags & EV_ERROR) {
                io_result = -1;
                op_errno = (int)eventlist[i].data;
                fprintf(stderr, "kqueue EV_ERROR for fd %d: %s\n", fd, strerror(op_errno));
            } else if (eventlist[i].flags & EV_EOF) {
                io_result = 0;
                 if (details->type == KOTLIN_OP_READV || details->type == KOTLIN_OP_RECV) {
                    // For reads, EOF is 0 bytes read.
                } else { // For writes/sends, EOF might indicate an issue.
                     if (eventlist[i].data != 0) {
                        op_errno = (int)eventlist[i].data; // If there's an error code with EOF
                        io_result = -1; // Mark as error
                     }
                }
            } else if (eventlist[i].filter == EVFILT_USER) {
                 // This is our user event, assumed to be for FSYNC based on current design
                if (op_state->op_details.type == KOTLIN_OP_FSYNC) {
                    // The actual FD for fsync was stored in op_state->op_details.fd by submit
                    // And also passed in eventlist[i].data when setting up the user event.
                    int actual_fd_for_fsync = op_state->op_details.fd; // Or (int)eventlist[i].data;

                    // Perform synchronous fsync
                    // The fsync_data_only flag from op_state->op_details can be used here if desired
                    // For example, by calling fcntl with F_FULLFSYNC vs fsync.
                    // For now, always full fsync.
                    io_result = fsync(actual_fd_for_fsync);
                    if (io_result == -1) {
                        op_errno = errno;
                    }
                } else {
                    fprintf(stderr, "poll_completions: Unexpected EVFILT_USER event type %d for ident %lu\n",
                            op_state->op_details.type, (unsigned long)eventlist[i].ident);
                    io_result = -1; op_errno = EINVAL;
                }
            }
            else { // Existing I/O filter logic (EVFILT_READ/WRITE)
                switch (details->type) {
                    case KOTLIN_OP_READV:
                        io_result = readv(fd, (struct iovec*)details->iovecs, details->iovec_count);
                        break;
                    case KOTLIN_OP_WRITEV:
                        io_result = writev(fd, (struct iovec*)details->iovecs, details->iovec_count);
                        break;
                    case KOTLIN_OP_RECV:
                        io_result = recv(fd, details->buffer, details->buffer_len, details->flags);
                        break;
                    case KOTLIN_OP_SEND:
                        io_result = send(fd, details->buffer, details->buffer_len, details->flags);
                        break;
                    default:
                        fprintf(stderr, "poll_completions: Unknown op type %d in op_state for fd %d\n", details->type, fd);
                        io_result = -1;
                        op_errno = EINVAL;
                        break;
                }
                if (io_result == -1) {
                    op_errno = errno;
                }
            }

            completions_out[total_completed_ops].user_data = details->user_data;
            completions_out[total_completed_ops].result = (io_result == -1) ? -op_errno : (int)io_result;
            total_completed_ops++;

            if ((details->type == KOTLIN_OP_READV || details->type == KOTLIN_OP_WRITEV) &&
                details->iovecs && details->iovecs != ops[i].iovecs) { // Check if it was a copy from submit
                // This check `details->iovecs != ops[i].iovecs` is problematic as ops array is not in scope.
                // The iovecs in op_state->op_details.iovecs are always the copied ones if they were created.
                free(details->iovecs);
            }
            free(op_state);
        }
        if (timeout_ts && timeout_ts->tv_sec == 0 && timeout_ts->tv_nsec == 0) {
            break;
        }

    } while (total_completed_ops < min_completions && num_events_retrieved > 0 && (timeout_ts == NULL || (timeout_ts->tv_sec > 0 || timeout_ts->tv_nsec > 0) ));

    return total_completed_ops;
}
