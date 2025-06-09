# QUIC HTTP/3 Server with asyncio

This project is an `asyncio`-based server demonstrating HTTP/3 capabilities using the `aioquic` library. It currently implements basic HTTP/3 request handling and serves as a foundation for exploring QUIC protocols. Conceptual outlines for future support of HTTP/2 and HTTP/1.x are also part of the project's design, aiming for a shared application logic across different HTTP versions.

## Features (Implemented for HTTP/3)

*   Handles GET, POST, PUT, DELETE requests to the root path (`/`).
*   Returns simple plain text responses for these methods (e.g., "Hello HTTP/3", "Resource created.").
*   Responds with "404 Not Found" for unhandled paths.
*   Responds with "405 Method Not Allowed" for unsupported HTTP methods on defined paths.
*   Uses internal `HttpRequest` and `HttpResponse` data structures for organized request and response processing.
*   Automatic self-signed certificate generation for `localhost`.

## Setup Instructions

### Prerequisites

*   Python 3.8 or newer (as recommended by `aioquic` and for modern `asyncio` features).
*   OpenSSL (for `cryptography` and `aioquic`).

### Dependencies

The project relies on the following Python libraries:

*   `aioquic`: For QUIC and HTTP/3 protocol implementation.
*   `cryptography`: For cryptographic operations needed by TLS and QUIC.

These dependencies are listed in `requirements.txt`.

### Installation

1.  **Clone the repository (if you haven't already):**
    ```bash
    git clone <repository-url>
    cd <repository-directory>/quic_http3_server
    ```

2.  **Create and activate a virtual environment (recommended):**
    ```bash
    python -m venv venv
    source venv/bin/activate  # On Windows: venv\Scripts\activate
    ```

3.  **Install dependencies:**
    ```bash
    pip install -r requirements.txt
    ```

## Certificate Generation

The server is configured to automatically generate a self-signed TLS certificate and private key if they are not found in the same directory as `server.py`.
*   Certificate file: `cert.pem`
*   Key file: `key.pem`

These files are generated for `localhost` and are intended for development and testing purposes. When you first run the server, you might see messages indicating these files are being created.

## Running the Server

To start the server, run the following command from within the `quic_http3_server` directory:

```bash
python server.py
```

By default, the server listens on:
*   **Host:** `0.0.0.0` (all available network interfaces)
*   **Port:** `4433`

You should see output indicating the server has started, e.g., `Starting QUIC HTTP/3 server on 0.0.0.0:4433`.

## Testing with Curl

To test the HTTP/3 server, you'll need a version of `curl` that is compiled with HTTP/3 support.

**Example GET request:**

```bash
curl --http3 https://localhost:4433/ -k
```

*   `--http3`: Tells curl to attempt HTTP/3 directly.
*   `https://localhost:4433/`: The URL for the server's root path.
*   `-k` (or `--insecure`): Allows curl to connect to a server using a self-signed certificate.

You should receive a response like: `Hello HTTP/3 from aioquic server!`

**Example POST request:**

```bash
curl --http3 -X POST https://localhost:4433/ -d "Test data" -k
```
This should return: `Resource created.`

**Example PUT request:**

```bash
curl --http3 -X PUT https://localhost:4433/ -d "Updated data" -k
```
This should return: `Resource updated.`

**Example DELETE request:**

```bash
curl --http3 -X DELETE https://localhost:4433/ -k
```
This should return: `Resource deleted.`

If your version of `curl` has issues with sending bodies with `--http3` for POST/PUT, you might need to explore other QUIC-enabled clients or tools for more extensive testing of these methods.

## Future Enhancements (Conceptual)

The server's design includes considerations for future expansion:

*   **HTTP/2 Support:** A conceptual outline exists for adding HTTP/2 support over TLS (TCP), leveraging the `h2` library. This would run alongside the HTTP/3 server, ideally sharing the same application logic.
*   **HTTP/1.1 Support:** Similarly, an outline for HTTP/1.1 support (plain TCP or TLS) has been considered, potentially using the `httptools` library for parsing. This would also aim to use the common application request handlers.

These enhancements would allow the server to handle multiple HTTP versions, making it a versatile tool for protocol experimentation.

## Contributing

Contributions and suggestions are welcome. Please feel free to open an issue or submit a pull request.
