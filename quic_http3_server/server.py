import asyncio
import os
from pathlib import Path
import ssl
from typing import Dict, Optional, Union, cast

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


class Http3ServerProtocol(QuicConnectionProtocol):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._http: Optional[H3Connection] = None

    def quic_event_received(self, event: QuicEvent) -> None:
        if isinstance(event, H3Event):
            if self._http is None:
                self._http = H3Connection(self._quic, enable_webtransport=False) # Corrected initialization

            # Pass H3 events to the H3 connection
            # print(f"Received H3 event: {event}")
            for h3_event in self._http.handle_event(event): # Corrected call
                self._h3_event_received(h3_event)


    def _h3_event_received(self, event: H3Event) -> None:
        # print(f"Handling H3 event: {event}")
        if isinstance(event, HeadersReceived):
            headers = {}
            for k, v in event.headers:
                headers[k.decode()] = v.decode()

            method = headers.get(":method")
            path = headers.get(":path")

            print(f"Received request: {method} {path}")

            if method == "GET" and path == "/":
                # Send response
                response_headers = [
                    (b":status", b"200"),
                    (b"server", b"aioquic-h3"),
                    (b"content-type", b"text/plain"),
                ]
                body = b"Hello HTTP/3 from aioquic server!"
                self._http.send_headers(stream_id=event.stream_id, headers=response_headers)
                self._http.send_data(stream_id=event.stream_id, data=body, end_stream=True)
                print(f"Sent response for stream {event.stream_id}")
            else:
                # Send 404
                response_headers = [
                    (b":status", b"404"),
                    (b"server", b"aioquic-h3"),
                ]
                self._http.send_headers(stream_id=event.stream_id, headers=response_headers, end_stream=True)
                print(f"Sent 404 for stream {event.stream_id}")

        elif isinstance(event, DataReceived):
            # Handle data received if necessary, for GET not much to do
            # print(f"Received data on stream {event.stream_id}, flow_id {event.flow_id}: {event.data}")
            if event.stream_ended:
                # print(f"Stream {event.stream_id} ended.")
                pass


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
