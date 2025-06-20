package borg.trikeshed.io

/**
 * A platform-agnostic handle to an I/O resource.
 *
 * This will be implemented as a wrapper for a file descriptor on native platforms,
 * and a `java.nio.channels.AsynchronousSocketChannel` on the JVM.
 */
expect class IOHandle 