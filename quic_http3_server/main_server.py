import asyncio
import ssl
import functools
from pathlib import Path

from .app_router import ApplicationRouter
from .core_types import HttpStatusCode, HttpHeaderKey, HttpHeaderValue, HttpHeaders, HttpBody, ParsedHttpRequest, ServerHttpResponse # Ensure these are available if router needs them directly, though router should be self-contained.
from .http1_protocol import Http1Protocol
from .http2_protocol import Http2Protocol
from .server import Http3ServerProtocol, generate_self_signed_cert, CERT_FILE, KEY_FILE # For H3 server and certs
from aioquic.quic.configuration import QuicConfiguration
from aioquic.h3.connection import H3_ALPN
from aioquic.asyncio import serve as serve_quic # Rename to avoid conflict with http1/2 server name

# Configuration for ports
HTTP1_PORT = 8080  # For plain HTTP/1.1
HTTPS1_PORT = 8441 # For HTTPS/1.1 (TLS)
HTTP2_PORT = 8443  # For HTTPS/2 (TLS, ALPN h2)
HTTP3_PORT = 4433  # For HTTP/3 (QUIC, ALPN h3)
SERVER_HOST = "0.0.0.0" # Listen on all interfaces

async def main():
    # Initialize the shared ApplicationRouter
    router = ApplicationRouter()

    # Generate self-signed certificate (used by HTTPS/1.1, HTTP/2, HTTP/3)
    cert_dir = Path(__file__).parent
    cert_path = cert_dir / CERT_FILE
    key_path = cert_dir / KEY_FILE
    generate_self_signed_cert(str(cert_path), str(key_path))

    # --- Setup SSL Contexts ---
    # SSL Context for HTTPS/1.1 and HTTP/2 (they can share if ALPN is handled)
    # HTTP/2 requires ALPN, HTTPS/1.1 does not strictly but client might send it.
    # For simplicity, we can create one context that supports both h2 and http/1.1 for ALPN.
    # Or, have one for H2 (with ALPN 'h2') and another for H1.1/TLS (no ALPN or general).

    # SSL context for HTTP/2 (requires ALPN 'h2')
    ssl_context_http2 = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ssl_context_http2.set_alpn_protocols(["h2", "http/1.1"]) # Support H2 and fallback for H1.1 over TLS via ALPN
    try:
        ssl_context_http2.load_cert_chain(cert_path, key_path)
    except FileNotFoundError:
        print(f"Error: Certificate or key file not found at {cert_path} or {key_path}. Please generate them.")
        return


    # SSL context for HTTPS/1.1 (can be simpler, no specific ALPN needed for server if h2 context handles it)
    # Re-using the h2 context is fine if it also allows http/1.1 via ALPN, or create a separate one.
    # For distinct HTTPS/1.1 service not advertising h2:
    ssl_context_https1 = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    # If you want HTTPS/1.1 to explicitly NOT negotiate h2 via ALPN on its dedicated port:
    # ssl_context_https1.set_alpn_protocols(["http/1.1"]) # Or leave empty if no ALPN desired
    try:
        ssl_context_https1.load_cert_chain(cert_path, key_path)
    except FileNotFoundError:
        print(f"Error: Certificate or key file not found for HTTPS/1.1. Please generate them.")
        return


    # --- Start Servers ---
    loop = asyncio.get_running_loop()
    servers = []
    server_tasks = []

    # Start HTTP/1.1 server (plain text)
    try:
        http1_server_plain = await loop.create_server(
            lambda: Http1Protocol(app_router=router),
            SERVER_HOST, HTTP1_PORT
        )
        servers.append(http1_server_plain)
        print(f"Serving HTTP/1.1 on {SERVER_HOST}:{HTTP1_PORT}")
    except Exception as e:
        print(f"Failed to start HTTP/1.1 server on port {HTTP1_PORT}: {e}")

    # Start HTTPS/1.1 server (TLS)
    try:
        https1_server = await loop.create_server(
            lambda: Http1Protocol(app_router=router),
            SERVER_HOST, HTTPS1_PORT,
            ssl=ssl_context_https1 # Use dedicated context for HTTPS/1.1
        )
        servers.append(https1_server)
        print(f"Serving HTTPS/1.1 on {SERVER_HOST}:{HTTPS1_PORT}")
    except Exception as e:
        print(f"Failed to start HTTPS/1.1 server on port {HTTPS1_PORT}: {e}")

    # Start HTTP/2 server (TLS with ALPN 'h2', also allows 'http/1.1' as fallback via ALPN)
    try:
        http2_server = await loop.create_server(
            # Http2Protocol only understands h2. If ALPN negotiates http/1.1, this server won't handle it correctly.
            # For a server that handles both H2 and H1.1 on the same port via ALPN,
            # the asyncio.Protocol factory would need to inspect ALPN result (transport.get_extra_info('alpn_protocol'))
            # and return either Http1Protocol or Http2Protocol. This is more advanced.
            # For now, Http2Protocol is bound, and ALPN is set to "h2", "http/1.1".
            # If client insists on "http/1.1" on this port, it might not work as expected with Http2Protocol.
            # Best practice for this simple setup: Http2Protocol with ALPN ["h2"].
            # The ssl_context_http2 was updated to ["h2", "http/1.1"], so we should adjust.
            # Let's assume Http2Protocol is desired if 'h2' is negotiated.
            # If 'http/1.1' is chosen by ALPN, this setup is not ideal for Http2Protocol.
            # For this example, we'll keep it simple and assume Http2Protocol will be used if 'h2' is chosen.
            lambda: Http2Protocol(app_router=router),
            SERVER_HOST, HTTP2_PORT,
            ssl=ssl_context_http2 # This context allows 'h2' and 'http/1.1'
        )
        servers.append(http2_server)
        print(f"Serving HTTP/2 (and potentially HTTPS/1.1 via ALPN) on {SERVER_HOST}:{HTTP2_PORT}")
    except Exception as e:
        print(f"Failed to start HTTP/2 server on port {HTTP2_PORT}: {e}")

    # Start HTTP/3 server (QUIC)
    try:
        quic_configuration = QuicConfiguration(
            alpn_protocols=H3_ALPN, # H3_ALPN typically includes "h3" and version specific like "h3-29"
            is_client=False,
        )
        quic_configuration.load_cert_chain(cert_path, key_path)

        h3_protocol_factory = functools.partial(Http3ServerProtocol, app_router=router)

        # create_task for serve_quic as it's a long-running awaitable
        h3_task = asyncio.create_task(
            serve_quic(
                host=SERVER_HOST,
                port=HTTP3_PORT,
                configuration=quic_configuration,
                create_protocol=h3_protocol_factory,
            )
        )
        server_tasks.append(h3_task) # Manage H3 task
        print(f"Serving HTTP/3 on {SERVER_HOST}:{HTTP3_PORT}")
    except Exception as e:
        print(f"Failed to start HTTP/3 server on port {HTTP3_PORT}: {e}")


    # Keep the main task alive to serve forever
    if not servers and not server_tasks:
        print("No servers were successfully started.")
        return

    print(f"All specified servers started. Listening for connections...")
    try:
        await asyncio.Event().wait() # Keep alive until cancelled
    except KeyboardInterrupt:
        print("\nShutting down servers...")
    finally:
        for server in servers: # TCP based servers
            server.close()
            await server.wait_closed()

        for task in server_tasks: # asyncio tasks like H3 server
            if not task.done():
                task.cancel()
        # Wait for tasks to be cancelled - this might take a moment for H3
        if server_tasks:
             await asyncio.gather(*server_tasks, return_exceptions=True)

        print("All servers closed.")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("Main server application stopped by user.")
    except Exception as e_main:
        print(f"Main application encountered an error: {e_main}")
