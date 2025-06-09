import asyncio
import os
from pathlib import Path
import ssl
from typing import Dict, Optional, Union, cast, Any, List, Tuple # Added List, Tuple

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from aioquic.asyncio import QuicConnectionProtocol, serve
from aioquic.h3.connection import H3_ALPN, H3Connection
from aioquic.h3.events import DataReceived, H3Event, HeadersReceived
from aioquic.quic.configuration import QuicConfiguration
from aioquic.quic.events import QuicEvent

# Import new core types
from .core_types import HttpMethod, HttpPath, HttpHeaderKey, HttpHeaderValue, HttpHeaders, HttpStatusCode, ParsedHttpRequest, ServerHttpResponse, HttpBody # Added HttpBody
from .app_router import ApplicationRouter # Import ApplicationRouter
import functools # Import functools

CERT_FILE = "cert.pem"
KEY_FILE = "key.pem"
SERVER_HOST = "0.0.0.0"
SERVER_PORT = 4433

# Generate self-signed certificate if not present
def generate_self_signed_cert(cert_path_str: str, key_path_str: str):
    cert_path = Path(cert_path_str)
    key_path = Path(key_path_str)

    if cert_path.exists() and key_path.exists():
        print(f"Certificates {cert_path_str} and {key_path_str} already exist.")
        return

    print(f"Generating self-signed certificate {cert_path_str} and key {key_path_str}...")

    # Generate private key
    private_key = ec.generate_private_key(ec.SECP256R1())

    # Generate self-signed certificate
    subject = issuer = x509.Name([
        x509.NameAttribute(x509.oid.NameOID.COMMON_NAME, u"localhost")
    ])
    builder = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(private_key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(x509.datetime_utcnow())
        .not_valid_after(x509.datetime_utcnow() + x509.timedelta(days=30))
        .add_extension(
            x509.BasicConstraints(ca=True, path_length=None), critical=True
        )
        .add_extension( # Add SAN for localhost
            x509.SubjectAlternativeName([x509.DNSName(u"localhost")]),
            critical=False,
        )
    )
    certificate = builder.sign(private_key, hashes.SHA256())

    # Write private key to PEM file
    with open(key_path, "wb") as f:
        f.write(
            private_key.private_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PrivateFormat.PKCS8,
                encryption_algorithm=serialization.NoEncryption(),
            )
        )
    print(f"Private key saved to {key_path_str}")

    # Write certificate to PEM file
    with open(cert_path, "wb") as f:
        f.write(certificate.public_bytes(serialization.Encoding.PEM))
    print(f"Certificate saved to {cert_path_str}")

# Old HttpRequest and HttpResponse classes are removed as per instructions.

class Http3ServerProtocol(QuicConnectionProtocol):
    def __init__(self, *args, app_router: ApplicationRouter, **kwargs): # Added app_router
        super().__init__(*args, **kwargs)
        self._http: Optional[H3Connection] = None
        self._active_streams: Dict[int, ParsedHttpRequest] = {} # Updated type hint
        self.app_router = app_router # Store app_router
        # _routes dictionary and handler methods are removed

    async def _process_request(self, stream_id: int, request: ParsedHttpRequest) -> None: # Made async
        print(f"H3 Protocol: Processing request for stream {stream_id}: {request.method.value} {request.path}")
        try:
            response = await self.app_router.route_request(request)
        except Exception as e:
            # Log error, create a 500 response
            print(f"H3 Protocol: Error routing request for stream {stream_id}: {e}")
            response = ServerHttpResponse(
                status_code=HttpStatusCode(500),
                headers=HttpHeaders([(HttpHeaderKey("Content-Type"), HttpHeaderValue("text/plain; charset=utf-8"))]),
                body=HttpBody(b"Internal Server Error")
            )

        self._send_response(stream_id, response)

        # Clean up from _active_streams (if stream_id was added there by _h3_event_received)
        if stream_id in self._active_streams:
            del self._active_streams[stream_id]

    def quic_event_received(self, event: QuicEvent) -> None:
        if isinstance(event, H3Event):
            if self._http is None:
                self._http = H3Connection(self._quic, enable_webtransport=False)

            for h3_event in self._http.handle_event(event):
                self._h3_event_received(h3_event)

    def _send_response(self, stream_id: int, response: ServerHttpResponse) -> None: # Updated signature
        aioquic_resp_headers: List[Tuple[bytes, bytes]] = [(b":status", str(response.status_code).encode('utf-8'))]
        # Add a server header for identification
        aioquic_resp_headers.append((b"server", b"aioquic-h3-refactored")) # Corrected: direct bytes tuple

        for key_str, val_str in response.headers:
            aioquic_resp_headers.append((key_str.encode('utf-8'), val_str.encode('utf-8')))

        self._http.send_headers(stream_id=stream_id, headers=aioquic_resp_headers)
        if response.body: # Check if there is a body to send
            self._http.send_data(stream_id=stream_id, data=response.body, end_stream=True)
        else: # If no body, end stream with headers
            self._http.send_data(stream_id=stream_id, data=b'', end_stream=True)
        print(f"Sent response for stream {stream_id} with status {response.status_code}")

    def _h3_event_received(self, event: H3Event) -> None:
        if isinstance(event, HeadersReceived):
            method_str: Optional[str] = None
            path_str: Optional[str] = None
            received_headers = HttpHeaders([])

            for k_bytes, v_bytes in event.headers:
                k_str = k_bytes.decode('utf-8')
                v_str = v_bytes.decode('utf-8')
                received_headers.append((HttpHeaderKey(k_str), HttpHeaderValue(v_str)))
                if k_str == ":method":
                    method_str = v_str
                if k_str == ":path":
                    path_str = v_str

            if method_str is None or path_str is None:
                print(f"Malformed H3 request on stream {event.stream_id}: :method or :path pseudo-header missing.")
                # Consider sending a 400 Bad Request response
                # For now, just return to avoid processing a bad request
                return

            try:
                http_method = HttpMethod.from_string(method_str)
            except ValueError as e:
                print(f"Unsupported HTTP method '{method_str}' on stream {event.stream_id}: {e}")
                # Optionally send 405 Method Not Allowed or 501 Not Implemented
                # For now, just return
                # Example for sending 405:
                # error_response = ServerHttpResponse(status_code=HttpStatusCode(405), body=HttpBody(f"Method {method_str} Not Allowed".encode()))
                # self._send_response(event.stream_id, error_response)
                return

            http_path = HttpPath(path_str)
            request = ParsedHttpRequest(method=http_method, path=http_path, headers=received_headers)
            self._active_streams[event.stream_id] = request

            print(f"Received headers for stream {event.stream_id}: {http_method.value} {http_path}")

            if event.stream_ended:  # e.g., GET request with no body
                asyncio.create_task(self._process_request(event.stream_id, request)) # Schedule as task

        elif isinstance(event, DataReceived):
            if event.stream_id in self._active_streams:
                request = self._active_streams[event.stream_id]
                # Assuming body is bytes, directly append. HttpBody is NewType for bytes.
                request.body = HttpBody(request.body + event.data) # type: ignore
                print(f"Received data for stream {event.stream_id}, size: {len(event.data)}, total body size: {len(request.body)}")

                if event.stream_ended:
                    asyncio.create_task(self._process_request(event.stream_id, request)) # Schedule as task
            else:
                print(f"Warning: DataReceived for unknown stream {event.stream_id}")


async def main(
    host: str = SERVER_HOST,
    port: int = SERVER_PORT,
    configuration: Optional[QuicConfiguration] = None,
) -> None:
    router = ApplicationRouter() # Create ApplicationRouter instance

    if configuration is None:
        configuration = QuicConfiguration(
            alpn_protocols=H3_ALPN, is_client=False
        )

    # Generate and load SSL certificate
    cert_dir = Path(__file__).parent
    cert_path = cert_dir / CERT_FILE
    key_path = cert_dir / KEY_FILE

    generate_self_signed_cert(str(cert_path), str(key_path))
    configuration.load_cert_chain(cert_path, key_path)

    protocol_factory = functools.partial(Http3ServerProtocol, app_router=router)

    print(f"Starting QUIC HTTP/3 server on {host}:{port}")
    await serve(
        host,
        port,
        configuration=configuration,
        create_protocol=protocol_factory, # Use partial to pass router
    )
    print(f"Server listening on {host}:{port}. Press Ctrl+C to stop.")
    await asyncio.Future()  # Run forever


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("Server stopped by user.")
    except Exception as e:
        print(f"Server failed: {e}")
