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
# Updated imports for H3Event types
from aioquic.h3.events import DataReceived as H3DataReceived, H3Event, HeadersReceived, WebTransportStreamDataReceived, DatagramReceived as H3DatagramReceived
from aioquic.quic.configuration import QuicConfiguration
from aioquic.quic.events import QuicEvent, DatagramReceived as QuicDatagramReceived # Distinguish QUIC DatagramReceived

# Import new core types
from .core_types import HttpMethod, HttpPath, HttpHeaderKey, HttpHeaderValue, HttpHeaders, HttpStatusCode, ParsedHttpRequest, ServerHttpResponse, HttpBody # Added HttpBody
from .app_router import ApplicationRouter # Import ApplicationRouter
import functools # Import functools
from datetime import datetime # Import datetime

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
        ts = datetime.now().isoformat()
        print(f"{ts} [H3_PROTO] [HTTP_REQUEST stream={stream_id}] Processing: method={request.method.value}, path='{request.path}'")
        try:
            response = await self.app_router.route_request(request)
        except Exception as e:
            ts_err = datetime.now().isoformat()
            # Log error, create a 500 response
            print(f"{ts_err} [H3_PROTO] [HTTP_ERROR stream={stream_id}] Error routing request: {e}")
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
        # Initialize H3Connection on first relevant event if not already done.
        # This simplified check assumes any QuicEvent means H3 context is active.
        if self._http is None:
            self._http = H3Connection(self._quic, enable_webtransport=True)

        # Pass all QUIC events to H3Connection's handler.
        # H3Connection.handle_event() will then yield specific H3Events.
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
        ts = datetime.now().isoformat()
        print(f"{ts} [H3_PROTO] [HTTP_RESPONSE stream={stream_id}] Sent: status={response.status_code}, body_len={len(response.body if response.body else b'')}")

    def _h3_event_received(self, event: H3Event) -> None:
        ts = datetime.now().isoformat() # General timestamp for event received
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

            # Check for WebTransport session initiation
            is_webtransport_request = False
            if method_str == "CONNECT":
                protocol_header_value = None
                for key, value in received_headers: # received_headers is HttpHeaders
                    if key.lower() == ":protocol": # Check :protocol pseudo-header
                        protocol_header_value = value
                        break
                if protocol_header_value == "webtransport":
                    is_webtransport_request = True

            if is_webtransport_request:
                # ts defined at the start of the method
                print(f"{ts} [H3_PROTO] [WT_NEGOTIATE session_stream={event.stream_id}] Session negotiation started for path {path_str}")
                # H3Connection handles the WebTransport handshake internally.
                # This stream will now be managed by H3Connection for WebTransport events.
                # We don't treat this as a standard HTTP request for our app_router.
                if event.stream_id in self._active_streams: # Should not have been added yet
                    del self._active_streams[event.stream_id]
                return # Stop further processing of this event as an HTTP request by this method

            # Existing logic for regular HTTP requests:
            if method_str is None or path_str is None:
                # ts defined at the start of the method
                print(f"{ts} [H3_PROTO] [HTTP_ERROR stream={event.stream_id}] Malformed HTTP request: :method or :path pseudo-header missing.")
                # Consider sending a 400 Bad Request response if possible (though H3Connection might handle stream errors)
                # For example, by closing the stream or sending an error if appropriate via H3Connection API.
                # For now, just returning as the stream state might be problematic for sending a full app-level response.
                return

            try:
                http_method = HttpMethod.from_string(method_str)
            except ValueError as e:
                # ts defined at the start of the method
                print(f"{ts} [H3_PROTO] [HTTP_ERROR stream={event.stream_id}] Unsupported HTTP method '{method_str}': {e}")
                error_response = ServerHttpResponse(
                    status_code=HttpStatusCode(405), # Method Not Allowed
                    headers=HttpHeaders([(HttpHeaderKey("Content-Type"), HttpHeaderValue("text/plain; charset=utf-8"))]),
                    body=HttpBody(f"Unsupported method: {method_str}".encode('utf-8'))
                )
                self._send_response(event.stream_id, error_response)
                return

            http_path = HttpPath(path_str)
            # Create ParsedHttpRequest (ensure received_headers contains all original headers)
            request = ParsedHttpRequest(method=http_method, path=http_path, headers=received_headers)
            self._active_streams[event.stream_id] = request

            # ts defined at the start of the method
            print(f"{ts} [H3_PROTO] [HTTP_HEADERS stream={event.stream_id}] Received: method={http_method.value}, path='{http_path}'")

            if event.stream_ended:  # e.g., GET request with no body
                asyncio.create_task(self._process_request(event.stream_id, request)) # Schedule as task

        elif isinstance(event, H3DataReceived): # Renamed from DataReceived
            # ts defined at the start of the method
            if event.stream_id in self._active_streams: # Ensure it's an HTTP app stream, not WebTransport
                request = self._active_streams[event.stream_id]
                request.body = HttpBody(request.body + event.data) # type: ignore
                print(f"{ts} [H3_PROTO] [HTTP_DATA stream={event.stream_id}] Received: len={len(event.data)}, total_body_len={len(request.body)}")

                if event.stream_ended:
                    asyncio.create_task(self._process_request(event.stream_id, request)) # Schedule as task
            # else:
            #    print(f"{ts} [H3_PROTO] [HTTP_DATA_WARN stream={event.stream_id}] Data for unknown or non-HTTP stream.")

        elif isinstance(event, WebTransportStreamDataReceived):
            session_id = getattr(event, 'session_id', None)
            stream_id = event.stream_id
            data = event.data
            stream_ended = event.stream_ended
            # ts defined at the start of the method
            print(f"{ts} [H3_PROTO] [WT_STREAM session={session_id} stream={stream_id}] DataReceived: len={len(data)}, ended={stream_ended}")

            if self._http:
                try:
                    self._http.send_webtransport_stream_data(
                        stream_id=stream_id,
                        data=data,
                        end_stream=stream_ended
                    )
                    ts_echo = datetime.now().isoformat()
                    print(f"{ts_echo} [H3_PROTO] [WT_STREAM session={session_id} stream={stream_id}] EchoedData: len={len(data)}, ended={stream_ended}")
                except Exception as e:
                    ts_err = datetime.now().isoformat()
                    print(f"{ts_err} [H3_PROTO] [WT_STREAM_ERROR session={session_id} stream={stream_id}] Error sending data: {e}")

        elif isinstance(event, H3DatagramReceived):
            flow_id = getattr(event, 'flow_id', None)
            data = event.data
            # ts defined at the start of the method
            print(f"{ts} [H3_PROTO] [WT_DATAGRAM session={flow_id}] DatagramReceived: len={len(data)}")
            if self._http:
                try:
                    self._http.send_datagram(flow_id=flow_id, data=data)
                    ts_echo = datetime.now().isoformat()
                    print(f"{ts_echo} [H3_PROTO] [WT_DATAGRAM session={flow_id}] EchoedDatagram: len={len(data)}")
                except Exception as e:
                    ts_err = datetime.now().isoformat()
                    print(f"{ts_err} [H3_PROTO] [WT_DATAGRAM_ERROR session={flow_id}] Error sending datagram: {e}")


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
