import asyncio
import unittest
from unittest.mock import MagicMock, AsyncMock, patch, call # Added patch
from typing import List, Tuple, Any, Callable, Awaitable # Added Callable, Awaitable

from h2.connection import H2Connection
from h2.events import RequestReceived, DataReceived, StreamEnded, ConnectionTerminated
from h2.errors import ErrorCodes

from .core_types import (
    HttpMethod, HttpPath, HttpHeaderKey, HttpHeaderValue, HttpHeaders,
    HttpStatusCode, ParsedHttpRequest, ServerHttpResponse, HttpBody
)
from .http2_protocol import Http2Protocol # Assuming http2_protocol.py is in the same dir

# A mock transport for asyncio.Protocol
class MockTransport(asyncio.Transport):
    def __init__(self):
        super().__init__()
        self.written_data = bytearray()
        self._is_closing = False

    def write(self, data: bytes):
        self.written_data.extend(data)

    def close(self):
        self._is_closing = True

    def is_closing(self) -> bool:
        return self._is_closing

    def get_extra_info(self, name, default=None):
        if name == 'peername':
            return ('127.0.0.1', 12345)
        if name == 'sslcontext': # Http2Protocol might check this implicitly via h2
            return MagicMock() # Return a mock SSL context
        return default

class TestHttp2Protocol(unittest.TestCase):
    def setUp(self):
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)

        self.mock_app_handler = AsyncMock(spec=Callable[[ParsedHttpRequest], Awaitable[ServerHttpResponse]])
        self.protocol = Http2Protocol(self.mock_app_handler)
        self.transport = MockTransport()

        # Simulate connection_made:
        # H2Connection.initiate_connection() sends a SETTINGS frame.
        # We need to capture that initial data.
        self.protocol.connection_made(self.transport)
        self.transport.written_data.clear() # Clear initial settings frame for cleaner test assertions

    def tearDown(self):
        self.loop.close()

    def _run_handler_tasks(self):
        # Allow tasks scheduled by data_received (like handle_request_async) to run
        self.loop.run_until_complete(asyncio.sleep(0))

    def _simulate_incoming_h2_request(self, stream_id: int, headers: List[Tuple[str, str]], data: Optional[bytes] = None, end_stream: bool = True):
        # This is a simplified way to generate H2 frames.
        # For more complex scenarios, direct H2Connection manipulation on client side might be needed.
        client_conn = H2Connection(client_side=True)

        # Client sends request headers
        client_conn.send_headers(stream_id, headers)
        if data:
            client_conn.send_data(stream_id, data, end_stream=end_stream)
        elif end_stream: # No data, but stream ends (e.g. GET)
             client_conn.send_data(stream_id, b'', end_stream=True)

        data_to_feed_to_server = client_conn.data_to_send()
        self.protocol.data_received(data_to_feed_to_server)


    def test_handle_simple_get_request_h2(self):
        stream_id = 1
        request_headers = [
            (':method', 'GET'),
            (':path', '/testget'),
            (':scheme', 'https'),
            (':authority', 'example.com')
        ]

        # Prepare mock response from app_handler
        mock_response_body = HttpBody(b"Test H2 GET response")
        mock_response_headers = HttpHeaders([
            (HttpHeaderKey("content-type"), HttpHeaderValue("text/plain")),
            (HttpHeaderKey("x-custom-h2"), HttpHeaderValue("value-h2"))
        ])
        self.mock_app_handler.return_value = ServerHttpResponse(
            status_code=HttpStatusCode(200),
            headers=mock_response_headers,
            body=mock_response_body
        )

        self._simulate_incoming_h2_request(stream_id, request_headers, end_stream=True)
        self._run_handler_tasks()

        # Assert app_handler was called correctly
        self.mock_app_handler.assert_called_once()
        called_request: ParsedHttpRequest = self.mock_app_handler.call_args[0][0]
        self.assertEqual(called_request.method, HttpMethod.GET)
        self.assertEqual(called_request.path, HttpPath("/testget"))
        self.assertIn((HttpHeaderKey(":scheme"), HttpHeaderValue("https")), called_request.headers)
        self.assertIn((HttpHeaderKey(":authority"), HttpHeaderValue("example.com")), called_request.headers)

        # Assert response frames were sent (simplified check)
        # This involves checking what H2Connection on the server side would send.
        # We check the transport for data.
        # The actual data on transport includes H2 framing.
        # A more robust test would parse self.transport.written_data with a client H2Connection.

        # Rough check: look for status and body in the output frames
        # This is not a perfect test of H2 framing but gives some confidence.
        # HPACK encoding can make direct byte matching tricky.
        # Look for status "200"
        status_bytes = b':status\x03200' # Common HPACK for :status: 200
        alt_status_bytes = b':status200' # Less common but possible if not indexed
        self.assertTrue(status_bytes in self.transport.written_data or alt_status_bytes in self.transport.written_data)
        self.assertTrue(b"Test H2 GET response" in self.transport.written_data)
        self.assertTrue(b"x-custom-h2" in self.transport.written_data) # Header keys are sent as is (lowercase by convention)
        self.assertTrue(b"value-h2" in self.transport.written_data)


    def test_handle_post_request_with_body_h2(self):
        stream_id = 3
        request_headers = [
            (':method', 'POST'),
            (':path', '/testpost'),
            (':scheme', 'https'),
            (':authority', 'example.com'),
            ('content-type', 'application/json')
        ]
        request_body = b'{"key": "value"}'

        self.mock_app_handler.return_value = ServerHttpResponse(
            status_code=HttpStatusCode(201),
            body=HttpBody(b"H2 POST received")
        )

        self._simulate_incoming_h2_request(stream_id, request_headers, data=request_body, end_stream=True)
        self._run_handler_tasks()

        self.mock_app_handler.assert_called_once()
        called_request: ParsedHttpRequest = self.mock_app_handler.call_args[0][0]
        self.assertEqual(called_request.method, HttpMethod.POST)
        self.assertEqual(called_request.path, HttpPath("/testpost"))
        self.assertEqual(called_request.body, request_body)
        self.assertEqual(called_request.get_header_value("content-type"), HttpHeaderValue("application/json"))

        status_bytes = b':status\x03201' # Common HPACK for :status: 201
        alt_status_bytes = b':status201'
        self.assertTrue(status_bytes in self.transport.written_data or alt_status_bytes in self.transport.written_data)
        self.assertTrue(b"H2 POST received" in self.transport.written_data)


    def test_app_handler_exception_sends_500(self):
        stream_id = 5
        request_headers = [(':method', 'GET'), (':path', '/error'), (':scheme', 'https'), (':authority', 'example.com')]

        self.mock_app_handler.side_effect = Exception("App error!")

        self._simulate_incoming_h2_request(stream_id, request_headers, end_stream=True)
        self._run_handler_tasks()

        self.mock_app_handler.assert_called_once()
        # Check for 500 status in sent frames
        status_bytes = b':status\x03500' # Common HPACK for :status: 500
        alt_status_bytes = b':status500'
        self.assertTrue(status_bytes in self.transport.written_data or alt_status_bytes in self.transport.written_data)
        self.assertTrue(b"Internal Server Error" in self.transport.written_data)


    def test_missing_pseudo_header_resets_stream(self):
        stream_id = 7
        # Missing :path
        request_headers = [(':method', 'GET'), (':scheme', 'https'), (':authority', 'example.com')]

        # We need to inspect calls to conn.reset_stream or data_to_send for RST_STREAM frame
        # This is harder to directly assert on transport.written_data without full H2 parsing.
        # For now, we'll trust the Http2Protocol's print statement and that it calls reset_stream.
        # A more advanced test would mock self.protocol.conn.reset_stream

        with patch.object(self.protocol.conn, 'reset_stream') as mock_reset_stream:
            self._simulate_incoming_h2_request(stream_id, request_headers, end_stream=True)
            # self._run_handler_tasks() # data_received itself should trigger reset if headers are bad
            # No, data_received calls request_received, which calls reset_stream and send_data_to_transport.
            # The _run_handler_tasks is for the app_handler coro, which isn't reached here.

            # Check if reset_stream was called.
            # The actual sending of RST_STREAM frame happens via conn.data_to_send()
            # which is called by protocol.send_data_to_transport()
            mock_reset_stream.assert_called_with(stream_id, ErrorCodes.PROTOCOL_ERROR)

        self.mock_app_handler.assert_not_called()


if __name__ == "__main__":
    unittest.main()
