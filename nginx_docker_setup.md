# Nginx Docker Setup for HTTP/1.1, HTTP/2, and HTTP/3 (QUIC)

This document describes how to run an Nginx server using Docker with the provided `nginx.conf` configuration, which supports HTTP/1.1, HTTP/2, and HTTP/3 (QUIC).

## Prerequisites

1.  **Docker Installed**: Ensure you have Docker installed on your system.
2.  **Nginx QUIC Image**: You need an Nginx Docker image that has HTTP/3 and QUIC support. The official `nginx:latest` image may not have this built-in. Consider using:
    *   `nginxinc/nginx-quic:latest` (official image from Nginx, Inc. with QUIC)
    *   A custom-built Nginx image compiled with BoringSSL or another SSL library that supports QUIC.
3.  **Configuration Files**:
    *   `nginx.conf`: Should be in the root of your project directory. This file is configured to listen for HTTP/1.1 on port 8080 (TCP), HTTP/2 on port 8081 (TCP/SSL), and HTTP/3 on port 8081 (UDP/QUIC).
    *   `index.html`: A simple HTML file, should be in the root of your project directory.
    *   `ssl_certs/nginx.crt` and `ssl_certs/nginx.key`: SSL certificate and private key. Placeholders were used in the previous step; for actual HTTP/3 and secure HTTP/2, you would need valid (or locally trusted for testing) certificates. These should be in an `ssl_certs` subdirectory in your project root.

## Docker Run Command

To run the Nginx server, use the following Docker command from the root of your project directory (where `nginx.conf`, `index.html`, and the `ssl_certs` directory are located):

```bash
# Replace 'nginxinc/nginx-quic:latest' if you are using a different QUIC-enabled image
IMAGE_NAME="nginxinc/nginx-quic:latest"
# Get the absolute path to the current directory for volume mounting
ABS_PWD=$(pwd)

docker run -d --name nginx_http3_server \
    -p 8080:8080/tcp \
    -p 8081:8081/tcp \
    -p 8081:8081/udp \
    -v "${ABS_PWD}/nginx.conf:/etc/nginx/nginx.conf:ro" \
    -v "${ABS_PWD}/index.html:/usr/share/nginx/html/index.html:ro" \
    -v "${ABS_PWD}/ssl_certs:/etc/nginx/ssl:ro" \
    ${IMAGE_NAME}
```

**Explanation of mounts and ports:**

*   `-d`: Run the container in detached mode.
*   `--name nginx_http3_server`: Assign a name to the container for easier management.
*   `-p 8080:8080/tcp`: Publish TCP port 8080 on the host to port 8080 in the container (for HTTP/1.1).
*   `-p 8081:8081/tcp`: Publish TCP port 8081 on the host to port 8081 in the container (for HTTP/2 over TLS).
*   `-p 8081:8081/udp`: Publish UDP port 8081 on the host to port 8081 in the container (for HTTP/3 over QUIC).
*   `-v "${ABS_PWD}/nginx.conf:/etc/nginx/nginx.conf:ro"`: Mount your custom `nginx.conf` into the container (read-only).
*   `-v "${ABS_PWD}/index.html:/usr/share/nginx/html/index.html:ro"`: Mount your `index.html` into the default Nginx HTML root directory (read-only).
*   `-v "${ABS_PWD}/ssl_certs:/etc/nginx/ssl:ro"`: Mount your SSL certificates directory (read-only).

## Testing (Conceptual - Requires a QUIC-enabled Nginx running)

**Note**: Actual testing requires a running Nginx instance with QUIC support and appropriate client tools. The placeholder SSL certificates will cause issues for browsers and standard curl; use `-k` or `--insecure` for curl if testing with self-signed/placeholder certs.

*   **HTTP/1.1 (`/hello` or `/`)**:
    ```bash
    curl http://localhost:8080/
    ```
*   **HTTP/1.1 (`/http_version`)**:
    ```bash
    curl http://localhost:8080/http_version
    # Expected: Request HTTP Version: HTTP/1.1 (or similar)
    ```
*   **HTTP/2 (`/hello` or `/`)**:
    ```bash
    curl --http2 -k https://localhost:8081/
    ```
*   **HTTP/2 (`/http_version`)**:
    ```bash
    curl --http2 -k https://localhost:8081/http_version
    # Expected: Request HTTP Version: HTTP/2.0 (or similar)
    ```
*   **HTTP/3 (`/http_version` - requires a curl build with HTTP/3 support)**:
    ```bash
    # Ensure your curl version supports HTTP/3 (--http3)
    curl --http3 -k https://localhost:8081/http_version
    # Expected: Request HTTP Version: HTTP/3.0 (or similar if connected via QUIC)
    # Note: The Alt-Svc header configured in nginx.conf helps clients discover the HTTP/3 service.
    ```

## Stopping the Container

```bash
docker stop nginx_http3_server
docker rm nginx_http3_server
```
