import asyncio
import os
from pathlib import Path
import ssl
from typing import Dict, Optional, Union, cast, Any

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from aioquic.asyncio import QuicConnectionProtocol, serve
from aioquic.h3.connection import H3_ALPN, H3Connection
from aioquic.h3.events import DataReceived, H3Event, HeadersReceived
from aioquic.quic.configuration import QuicConfiguration
from aioquic.quic.events import QuicEvent

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


class HttpRequest:
    def __init__(self, method: str, path: str, headers: Dict[str, str]):
        self.method = method
        self.path = path
        self.headers = headers
        self.body: bytes = b""

class HttpResponse:
    def __init__(self, status_code: int, headers: Optional[Dict[str, str]] = None, body: bytes = b""):
        self.status_code = status_code
        self.headers = headers if headers is not None else {}
        self.body = body

class Http3ServerProtocol(QuicConnectionProtocol):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._http: Optional[H3Connection] = None
        self._active_streams: Dict[int, HttpRequest] = {}
        self._routes = {
            "/": {
                "GET": self._handle_get_root,
                "POST": self._handle_post_root,
                "PUT": self._handle_put_root,
                "DELETE": self._handle_delete_root,
            }
        }

    def _handle_get_root(self, stream_id: int, request: HttpRequest) -> None:
        print(f"Handling GET for /: Headers: {request.headers}")
        response = HttpResponse(status_code=200, headers={"content-type": "text/plain"}, body=b"Hello HTTP/3 from aioquic server!")
        self._send_response(stream_id, response)

    def _handle_post_root(self, stream_id: int, request: HttpRequest) -> None:
        print(f"Handling POST for /: Headers: {request.headers}, Body: {request.body.decode()}")
        response = HttpResponse(status_code=201, headers={"content-type": "text/plain"}, body=b"Resource created.")
        self._send_response(stream_id, response)

    def _handle_put_root(self, stream_id: int, request: HttpRequest) -> None:
        print(f"Handling PUT for /: Headers: {request.headers}, Body: {request.body.decode()}")
        response = HttpResponse(status_code=200, headers={"content-type": "text/plain"}, body=b"Resource updated.")
        self._send_response(stream_id, response)

    def _handle_delete_root(self, stream_id: int, request: HttpRequest) -> None:
        print(f"Handling DELETE for /: Headers: {request.headers}")
        response = HttpResponse(status_code=200, headers={"content-type": "text/plain"}, body=b"Resource deleted.")
        self._send_response(stream_id, response)

    def _process_request(self, stream_id: int, request: HttpRequest) -> None:
        print(f"Processing request: {request.method} {request.path}")
        if request.path in self._routes:
            path_handlers = self._routes[request.path]
            if request.method in path_handlers:
                handler = path_handlers[request.method]
                handler(stream_id, request)
            else:
                response = HttpResponse(status_code=405, headers={"content-type": "text/plain"}, body=b"Method Not Allowed")
                self._send_response(stream_id, response)
        else:
            response = HttpResponse(status_code=404, headers={"content-type": "text/plain"}, body=b"Not Found")
            self._send_response(stream_id, response)

        if stream_id in self._active_streams:
            del self._active_streams[stream_id] # Clean up

    def quic_event_received(self, event: QuicEvent) -> None:
        if isinstance(event, H3Event):
            if self._http is None:
                self._http = H3Connection(self._quic, enable_webtransport=False)

            for h3_event in self._http.handle_event(event):
                self._h3_event_received(h3_event)

    def _send_response(self, stream_id: int, response: HttpResponse) -> None:
        response_headers = [
            (b":status", str(response.status_code).encode()),
            (b"server", b"aioquic-h3"),
        ]
        if response.headers:
            for k, v in response.headers.items():
                response_headers.append((k.encode(), v.encode()))

        self._http.send_headers(stream_id=stream_id, headers=response_headers)
        self._http.send_data(stream_id=stream_id, data=response.body, end_stream=True)
        print(f"Sent response for stream {stream_id} with status {response.status_code}")

    def _h3_event_received(self, event: H3Event) -> None:
        if isinstance(event, HeadersReceived):
            headers_dict = {k.decode(): v.decode() for k, v in event.headers}
            method = headers_dict.get(":method")
            path = headers_dict.get(":path")

            if not method or not path:
                # Malformed request, consider sending a 400 Bad Request
                print(f"Malformed request on stream {event.stream_id}: Missing :method or :path")
                # Potentially send a 400 response here, but _send_response needs a stream_id
                # and an HttpResponse object. This case needs careful handling.
                return

            request = HttpRequest(method=method, path=path, headers=headers_dict)
            self._active_streams[event.stream_id] = request

            print(f"Received headers for stream {event.stream_id}: {method} {path}")

            if event.stream_ended: # e.g., GET request with no body
                self._process_request(event.stream_id, request)

        elif isinstance(event, DataReceived):
            if event.stream_id in self._active_streams:
                request = self._active_streams[event.stream_id]
                request.body += event.data
                print(f"Received data for stream {event.stream_id}, size: {len(event.data)}, total body size: {len(request.body)}")

                if event.stream_ended:
                    self._process_request(event.stream_id, request)
            else:
                # This case should ideally not happen if HeadersReceived is always processed first
                print(f"Warning: DataReceived for unknown stream {event.stream_id}")


async def main(
    host: str = SERVER_HOST,
    port: int = SERVER_PORT,
    configuration: Optional[QuicConfiguration] = None,
) -> None:
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

    print(f"Starting QUIC HTTP/3 server on {host}:{port}")
    await serve(
        host,
        port,
        configuration=configuration,
        create_protocol=Http3ServerProtocol,
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
