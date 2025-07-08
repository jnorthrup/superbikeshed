# RemoteFileAttention: Intelligent Remote File Handling with QUIC

This document describes the `RemoteFileAttention` component within the TrikeShed project, focusing on its design principles, leveraging QUIC for efficient remote file access, and the concepts of "attention" and "concurrency" in its operation. It also introduces `GzipFileAttention` for handling gzipped files.

## Purpose

The primary goal of `RemoteFileAttention` is to enable efficient interaction with large remote files (like `.zip` or `.gz` archives) without requiring the full download of the entire file. Traditional methods often necessitate downloading the complete file, which is inefficient for large files, especially when only specific metadata or small portions of the content are needed.

## How it Works: Leveraging QUIC for "Attention"

`RemoteFileAttention` achieves its efficiency by focusing its "attention" on the critical parts of the remote file. Instead of a full download, it performs targeted HTTP range requests over a QUIC connection.

`RemoteFileAttention` relies on the `RemoteFileAccessor` interface, which defines the core operations for interacting with a remote file:

*   **`getFileSize()`**: Fetches the total size of the remote file. This is typically done via an HTTP `HEAD` request.
*   **`readRange(startByte: Long, endByte: Long)`**: Reads a specific byte range from the remote file. This is typically done via an HTTP `GET` request with a `Range` header.

An implementation like `QuicRemoteFileAccessor` uses TrikeShed's internal QUIC client capabilities to perform these HTTP requests.

For `.zip` files, `RemoteFileAttention` uses these range requests to:

1.  **File Size Discovery**: Determine the total size of the zip file.
2.  **Central Directory Location**: Zip files store their "central directory" (an index of all files within the archive) at the *end* of the file. `RemoteFileAttention` leverages this by calculating the probable location of this directory.
3.  **Index Parsing**: Once the central directory bytes are received, `RemoteFileAttention` parses this binary data to extract metadata about the files within the archive (e.g., filenames, uncompressed sizes, and their offsets within the zip file).

This selective fetching demonstrates "attention" by precisely targeting and retrieving only the necessary information, significantly reducing network overhead and processing time.

## GzipFileAttention: Specialized Handling for Gzipped Files

`GzipFileAttention` builds upon `RemoteFileAttention` to provide specialized random access capabilities for gzipped files. It integrates with the `KzranGzipReader` (which implements the KZRAN algorithm) to efficiently read data from gzipped streams.

`GzipFileAttention` uses `RemoteFileAttention`'s `readRange` method to fetch chunks of the gzipped file and then feeds these chunks to `KzranGzipReader` to build an index (`KzranIndex`) of sync points. This index allows for seeking to specific positions within the compressed data without decompressing the entire file from the beginning.

## Concurrency for Enhanced Performance

Both `RemoteFileAttention` and `GzipFileAttention` leverage the underlying QUIC transport and Kotlin's coroutine capabilities (`kotlinx.coroutines`) for powerful "concurrency".

*   **Parallel Chunk Downloads**: For extracting specific files or ranges from an archive, multiple, non-contiguous chunks can be downloaded concurrently using separate QUIC streams. This "scatter-gather" approach can drastically improve download speeds, especially over high-latency networks.
*   **Stream Multiplexing**: QUIC's inherent stream multiplexing allows multiple data streams to operate independently over a single connection, preventing head-of-line blocking and ensuring that a slow download of one part of the file doesn't impede the retrieval of another.

## Integration within TrikeShed

`RemoteFileAttention` and `GzipFileAttention` are designed to integrate seamlessly with TrikeShed's existing QUIC networking components (`borg.trikeshed.net.quic.QuicConnection`, `QuicStream`, etc.). They serve as higher-level utilities for interacting with remote data, building upon the low-level QUIC transport.

## Future Work

Future development will focus on:

*   Refining the `KzranGzipReader` integration within `GzipFileAttention` for optimal random access performance.
*   Adding robust error handling for network failures and malformed archives.
*   Extending the components to support concurrent extraction of individual files or directories from remote archives.
*   Integrating these components with other TrikeShed modules that could benefit from efficient remote file access.