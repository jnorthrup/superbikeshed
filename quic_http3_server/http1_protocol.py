import asyncio
import httptools # type: ignore
import ssl
from pathlib import Path
from typing import Optional, List, Tuple, Callable, Awaitable

from .core_types import (
    HttpMethod, HttpPath, HttpHeaderKey, HttpHeaderValue, HttpHeaders,
    HttpStatusCode, ParsedHttpRequest, ServerHttpResponse, HttpBody
)
# Assuming generate_self_signed_cert, CERT_FILE, KEY_FILE are accessible
# For now, let's assume they will be imported from server.py or a shared utils module.
# To make this self-contained for the subtask, we can duplicate them or simplify.
# For this subtask, we will assume they are imported from server.py for cert generation.
from .server import generate_self_signed_cert, CERT_FILE, KEY_FILE
from .app_router import ApplicationRouter # Import ApplicationRouter

# Old RequestHandler type alias is removed


class Http1Protocol(asyncio.Protocol):
    def __init__(self, app_router: ApplicationRouter): # Updated constructor
        self.transport: Optional[asyncio.Transport] = None
        self.parser = httptools.HttpRequestParser(self) # type: ignore
        self.app_router = app_router # Store app_router

        # For assembling the request
        self._current_request: Optional[ParsedHttpRequest] = None
        self._current_url: Optional[bytes] = None
        self._current_headers: List[Tuple[HttpHeaderKey, HttpHeaderValue]] = []
        self._current_body: bytearray = bytearray()

    def connection_made(self, transport: asyncio.Transport):
        self.transport = transport
        peername = transport.get_extra_info('peername')
        print(f"HTTP/1.1 connection from {peername}")

    def data_received(self, data: bytes):
        try:
            self.parser.feed_data(data)
        except httptools.HttpParserError as e:
            print(f"HTTP/1.1 Parser error: {e}")
            # Send a basic 400 response
            if self.transport and not self.transport.is_closing():
                error_response = ServerHttpResponse(
                    status_code=HttpStatusCode(400),
                    headers=HttpHeaders([(HttpHeaderKey("Content-Type"), HttpHeaderValue("text/plain")),
                                         (HttpHeaderKey("Connection"), HttpHeaderValue("close"))]),
                    body=HttpBody(b"Bad Request")
                )
                self.send_response(error_response)
            if self.transport:
                self.transport.close()


    # httptools parser callbacks
    def on_message_begin(self):
        self._current_headers = []
        self._current_body = bytearray()
        self._current_url = None

    def on_url(self, url: bytes):
        self._current_url = url

    def on_header(self, name: bytes, value: bytes):
        self._current_headers.append(
            (HttpHeaderKey(name.decode('utf-8')), HttpHeaderValue(value.decode('utf-8')))
        )

    def on_headers_complete(self):
        # Method is available via self.parser.get_method().decode('utf-8')
        # URL is self._current_url
        # Headers are self._current_headers
        # Body will come in on_body
        pass # Actual request object creation will be in on_message_complete

    def on_body(self, body: bytes):
        self._current_body.extend(body)

    def on_message_complete(self):
        if self._current_url is None:
            # This case should ideally be prevented by parser or earlier checks
            print("Error: URL not set in on_message_complete")
            if self.transport: self.transport.close()
            return

        method_str = self.parser.get_method().decode('utf-8')
        try:
            method = HttpMethod.from_string(method_str)
        except ValueError:
            print(f"Unsupported HTTP method: {method_str}")
            # Send 405 or 501
            if self.transport and not self.transport.is_closing():
                 error_response = ServerHttpResponse(
                    status_code=HttpStatusCode(405), # Method Not Allowed
                    headers=HttpHeaders([(HttpHeaderKey("Content-Type"), HttpHeaderValue("text/plain")),
                                         (HttpHeaderKey("Connection"), HttpHeaderValue("close"))]),
                    body=HttpBody(f"Method {method_str} Not Allowed".encode('utf-8'))
                )
                 self.send_response(error_response)
            if self.transport: self.transport.close()
            return

        # For now, assuming path is everything in the URL. Robust parsing might be needed.
        # httptools url is the raw path and query string.
        path = HttpPath(self._current_url.decode('utf-8'))

        self._current_request = ParsedHttpRequest(
            method=method,
            path=path,
            headers=HttpHeaders(self._current_headers), # Ensure it's cast to HttpHeaders
            body=HttpBody(bytes(self._current_body))
        )

        # Schedule the app_handler to be called
        asyncio.create_task(self._handle_request(self._current_request))

    async def _handle_request(self, request: ParsedHttpRequest):
        if not self.transport or self.transport.is_closing():
            print("Transport closed before handling request.")
            return

        response = await self.app_router.route_request(request) # Use app_router
        self.send_response(response)

        # Basic keep-alive check (very simplified)
        connection_header = request.get_header_value("Connection")
        if connection_header and connection_header.lower() == "close":
            if self.transport: self.transport.close()
        elif self.parser.should_keep_alive():
            # Reset parser for next request if keep-alive
            # Note: httptools parser is recreated per connection in this simple model,
            # so for true keep-alive within one connection, parser state needs management
            # or a new parser instance for each message if connection persists.
            # For now, this example implies one request per connection or simple close.
            # To properly support keep-alive, on_message_complete would need to reset parser state
            # and not close transport immediately.
            # Simplified: this example will likely close after one response unless client forces keep-alive
            # and server explicitly supports it by not closing.
            # Let's assume for now it processes one request and then might be closed or reused.
            # For this simple implementation, we will close after each response.
             if self.transport: self.transport.close()

        else:
            if self.transport: self.transport.close() # Default to close


    def send_response(self, response: ServerHttpResponse):
        if not self.transport or self.transport.is_closing():
            print("Cannot send response, transport is closed or closing.")
            return

        # HTTP/1.1 Status Line
        status_line = f"HTTP/{self.parser.get_http_version()} {response.status_code} {self._get_status_message(response.status_code)}\r\n"
        self.transport.write(status_line.encode('utf-8'))

        # Headers
        # Ensure Content-Length if body is present and not chunked (chunking not implemented here)
        has_content_length = any(k.lower() == "content-length" for k, v in response.headers)
        if response.body and not has_content_length:
            response.add_header(HttpHeaderKey("Content-Length"), HttpHeaderValue(str(len(response.body))))

        # Default connection header (simplified)
        # has_connection_header = any(k.lower() == "connection" for k,v in response.headers)
        # if not has_connection_header:
        #    response.add_header(HttpHeaderKey("Connection"), HttpHeaderValue("close"))


        for key, value in response.headers:
            header_line = f"{key}: {value}\r\n"
            self.transport.write(header_line.encode('utf-8'))

        self.transport.write(b"\r\n") # End of headers

        if response.body:
            self.transport.write(response.body)

        print(f"Sent HTTP/1.1 response: {response.status_code}")


    def _get_status_message(self, status_code: int) -> str:
        # Basic status messages, can be expanded
        # httptools.responses.get_status_message(status_code) could be an option if available
        # For now, a minimal map:
        messages = {
            200: "OK", 201: "Created", 204: "No Content",
            400: "Bad Request", 404: "Not Found", 405: "Method Not Allowed",
            500: "Internal Server Error"
        }
        return messages.get(status_code, "Unknown Status")

    def connection_lost(self, exc: Optional[Exception]):
        print("HTTP/1.1 connection lost.")
        if self.transport and not self.transport.is_closing():
            self.transport.close()


# --- Example Application Logic & Server Setup ---
# simple_app_handler function is removed

async def main_http1(host="127.0.0.1", port=8080, use_tls=False):
    # Ensure certs are generated (using functions from server.py)
    cert_dir = Path(__file__).parent
    cert_path = cert_dir / CERT_FILE
    key_path = cert_dir / KEY_FILE
    if use_tls:
        generate_self_signed_cert(str(cert_path), str(key_path))
        ssl_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ssl_context.load_cert_chain(cert_path, key_path)
    else:
        ssl_context = None

    router = ApplicationRouter() # Create ApplicationRouter instance
    loop = asyncio.get_running_loop()
    server = await loop.create_server(
        lambda: Http1Protocol(router), # Pass router to protocol
        host, port,
        ssl=ssl_context
    )

    protocol_name = "HTTPS/1.1" if use_tls else "HTTP/1.1"
    print(f"Serving {protocol_name} on {host}:{port}")

    async with server:
        await server.serve_forever()

if __name__ == "__main__":
    # To run this HTTP/1.1 server independently:
    # python -m quic_http3_server.http1_protocol
    # Test with: curl http://127.0.0.1:8080/
    # Test POST: curl -X POST -d "test" http://127.0.0.1:8080/
    #
    # To run with TLS:
    # python -m quic_http3_server.http1_protocol --tls
    # Test with: curl --insecure https://127.0.0.1:8080/

    # Basic argument parsing for --tls
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--tls", action="store_true", help="Enable TLS for HTTPS/1.1")
    parser.add_argument("--port", type=int, default=8080, help="Port to listen on")
    args = parser.parse_args()

    try:
        asyncio.run(main_http1(port=args.port, use_tls=args.tls))
    except KeyboardInterrupt:
        print("HTTP/1.1 server stopped.")
