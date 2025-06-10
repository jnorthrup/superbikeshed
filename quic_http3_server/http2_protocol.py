import asyncio
import ssl
from pathlib import Path
from typing import Optional, Dict, List, Callable, Awaitable, Tuple

from h2.connection import H2Connection
from h2.events import RequestReceived, DataReceived, StreamEnded, ConnectionTerminated, StreamReset
from h2.errors import ErrorCodes
from h2.exceptions import ProtocolError

from .core_types import (
    HttpMethod, HttpPath, HttpHeaderKey, HttpHeaderValue, HttpHeaders,
    HttpStatusCode, ParsedHttpRequest, ServerHttpResponse, HttpBody
)
from .server import generate_self_signed_cert, CERT_FILE, KEY_FILE # For certs
from .app_router import ApplicationRouter # Import ApplicationRouter

# Old RequestHandler type alias is removed

class Http2Protocol(asyncio.Protocol):
    def __init__(self, app_router: ApplicationRouter): # Updated constructor
        self.app_router = app_router # Store app_router
        self.conn = H2Connection(client_side=False)
        self.transport: Optional[asyncio.Transport] = None
        self.stream_data: Dict[int, bytearray] = {}
        self.stream_headers: Dict[int, HttpHeaders] = {} # Store initial headers for each stream

    def connection_made(self, transport: asyncio.Transport):
        self.transport = transport
        self.conn.initiate_connection()
        self.send_data_to_transport()
        peername = transport.get_extra_info('peername')
        print(f"HTTP/2 connection from {peername}")

    def connection_lost(self, exc: Optional[Exception]):
        print(f"HTTP/2 connection lost. Exception: {exc}")
        if self.transport and not self.transport.is_closing():
            self.transport.close()
        # Clean up stream data for this connection
        self.stream_data.clear()
        self.stream_headers.clear()


    def data_received(self, data: bytes):
        if not self.transport or self.transport.is_closing():
            return
        try:
            events = self.conn.receive_data(data)
        except ProtocolError as e:
            print(f"H2 Protocol Error: {e}, code: {e.error_code}")
            # A GOAWAY frame might have already been sent by H2Connection
            # or we might need to send one if appropriate.
            # For now, ensure data is sent and close.
            self.send_data_to_transport()
            if self.transport: self.transport.close()
            return

        self.send_data_to_transport() # Send any pending frames (e.g. SETTINGS ACK)

        for event in events:
            if isinstance(event, RequestReceived):
                self.request_received(event)
            elif isinstance(event, DataReceived):
                self.receive_stream_data(event)
            elif isinstance(event, StreamEnded):
                self.stream_ended(event.stream_id)
            elif isinstance(event, ConnectionTerminated):
                if self.transport: self.transport.close()
            elif isinstance(event, StreamReset):
                print(f"Stream {event.stream_id} reset by client. Error code: {event.error_code}")
                if event.stream_id in self.stream_data:
                    del self.stream_data[event.stream_id]
                if event.stream_id in self.stream_headers:
                    del self.stream_headers[event.stream_id]
            # Handle other events as needed (SettingsAcknowledged, PING, etc.)

        self.send_data_to_transport() # Important to send responses or ACKs


    def request_received(self, event: RequestReceived):
        stream_id = event.stream_id

        # Store headers temporarily, full request object created when stream ends or all data received
        # h2 library gives headers as List[Tuple[bytes, bytes]]
        decoded_headers = HttpHeaders([])
        path_str: Optional[str] = None
        method_str: Optional[str] = None

        for name_bytes, value_bytes in event.headers:
            name = name_bytes.decode('utf-8')
            value = value_bytes.decode('utf-8')
            decoded_headers.append((HttpHeaderKey(name), HttpHeaderValue(value)))
            if name == ':path':
                path_str = value
            elif name == ':method':
                method_str = value

        if not method_str or not path_str:
            print(f"Stream {stream_id}: Missing :method or :path pseudo-header.")
            self.conn.reset_stream(stream_id, ErrorCodes.PROTOCOL_ERROR)
            self.send_data_to_transport()
            return

        self.stream_headers[stream_id] = decoded_headers
        self.stream_data[stream_id] = bytearray() # Initialize body storage

        # If stream is already ended (e.g. GET request with no body), process immediately
        if event.stream_ended:
            self.stream_ended(stream_id)


    def receive_stream_data(self, event: DataReceived):
        stream_id = event.stream_id
        if stream_id in self.stream_data: # Should always be true if request_received was processed
            self.stream_data[stream_id].extend(event.data)
            # Flow control
            self.conn.acknowledge_received_data(event.flow_controlled_length, stream_id)
            self.send_data_to_transport() # Send window update
        else:
            # This means data arrived for a stream we are not tracking. This is a protocol error.
            print(f"Warning: Received data for unknown stream {stream_id}")
            self.conn.reset_stream(stream_id, ErrorCodes.PROTOCOL_ERROR)
            self.send_data_to_transport()


    def stream_ended(self, stream_id: int):
        if stream_id not in self.stream_headers:
            # Could happen if stream was reset or encountered an error before headers were fully processed
            print(f"Stream {stream_id} ended but no headers found. Ignoring.")
            if stream_id in self.stream_data: del self.stream_data[stream_id]
            return

        headers = self.stream_headers.pop(stream_id)
        body_bytes = bytes(self.stream_data.pop(stream_id, bytearray()))

        # Extract :method and :path for ParsedHttpRequest constructor
        method_str: Optional[str] = None
        path_str: Optional[str] = None
        for k,v in headers: # headers is HttpHeaders (List[Tuple[HttpHeaderKey,HttpHeaderValue]])
            if k == HttpHeaderKey(":method"): method_str = v
            if k == HttpHeaderKey(":path"): path_str = v

        if not method_str or not path_str:
            # This should have been caught in request_received, but as a safeguard:
            print(f"Stream {stream_id} ended: Critical error - :method or :path missing from stored headers.")
            # Don't try to send a response on this stream if it's already in a bad state.
            # The stream might have been reset by request_received.
            return

        try:
            method = HttpMethod.from_string(method_str)
        except ValueError:
            print(f"Stream {stream_id}: Unsupported HTTP method {method_str}")
            # Send 405 or 501. h2 requires :status to be a string.
            response_headers = [
                (':status', '405'),
                ('content-type', 'text/plain')
            ]
            self.conn.send_headers(stream_id, response_headers)
            self.conn.send_data(stream_id, f"Method {method_str} Not Allowed".encode('utf-8'), end_stream=True)
            self.send_data_to_transport()
            return

        request = ParsedHttpRequest(
            method=method,
            path=HttpPath(path_str), # path_str must be non-None here
            headers=headers,
            body=HttpBody(body_bytes)
        )
        asyncio.create_task(self.handle_request_async(stream_id, request))

    async def handle_request_async(self, stream_id: int, request: ParsedHttpRequest):
        try:
            response = await self.app_router.route_request(request) # Use app_router
        except Exception as e:
            print(f"Application handler error for stream {stream_id}: {e}")
            # Send a 500 Internal Server Error
            response_headers = [
                (':status', str(HttpStatusCode(500).value)),
                (HttpHeaderKey('content-type').lower(), HttpHeaderValue('text/plain'))
            ]
            error_body = b"Internal Server Error"
            self.conn.send_headers(stream_id, response_headers)
            self.conn.send_data(stream_id, error_body, end_stream=True)
        else:
            # Convert ServerHttpResponse to H2 headers and data
            h2_response_headers: List[Tuple[str, str]] = [
                (':status', str(response.status_code.value))
            ]
            # Add a server header
            h2_response_headers.append(('server', 'my-h2-server'))

            for key, value in response.headers:
                h2_response_headers.append((key.lower(), value)) # HTTP/2 header names are lowercase

            self.conn.send_headers(stream_id, h2_response_headers)
            if response.body:
                self.conn.send_data(stream_id, response.body, end_stream=True)
            else:
                self.conn.send_data(stream_id, b'', end_stream=True)

        self.send_data_to_transport()


    def send_data_to_transport(self):
        if not self.transport or self.transport.is_closing():
            return
        data_to_send = self.conn.data_to_send()
        if data_to_send:
            self.transport.write(data_to_send)


# --- Example Application Logic & Server Setup ---
# simple_app_handler_h2 function is removed

async def main_http2(host="127.0.0.1", port=8443):
    # HTTP/2 requires ALPN, so TLS is mandatory.
    cert_dir = Path(__file__).parent
    cert_path = cert_dir / CERT_FILE
    key_path = cert_dir / KEY_FILE
    generate_self_signed_cert(str(cert_path), str(key_path))

    ssl_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ssl_context.set_alpn_protocols(["h2"]) # ALPN for HTTP/2
    ssl_context.load_cert_chain(cert_path, key_path)

    router = ApplicationRouter() # Create ApplicationRouter instance
    loop = asyncio.get_running_loop()
    server = await loop.create_server(
        lambda: Http2Protocol(router), # Pass router to protocol
        host, port,
        ssl=ssl_context
    )
    print(f"Serving HTTPS/2 on {host}:{port}")
    async with server:
        await server.serve_forever()

if __name__ == "__main__":
    # To run this HTTP/2 server independently:
    # python -m quic_http3_server.http2_protocol
    # Test with: curl --http2 --insecure https://127.0.0.1:8443/
    # Test POST: curl --http2 --insecure -X POST -d "test h2" https://127.0.0.1:8443/
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8443, help="Port to listen on")
    args = parser.parse_args()

    try:
        asyncio.run(main_http2(port=args.port))
    except KeyboardInterrupt:
        print("HTTP/2 server stopped.")
