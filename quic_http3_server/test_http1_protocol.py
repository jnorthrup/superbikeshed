import asyncio
import unittest
from unittest.mock import MagicMock, AsyncMock, patch, call
from typing import Callable, Awaitable # Added for AsyncMock spec

from .core_types import (
    HttpMethod, HttpPath, HttpHeaderKey, HttpHeaderValue, HttpHeaders,
    HttpStatusCode, ParsedHttpRequest, ServerHttpResponse, HttpBody
)
from .http1_protocol import Http1Protocol # Assuming http1_protocol.py is in the same dir

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
        return default

    # Add other methods if Http1Protocol calls them and they need mocking
    # For example: abort(), can_write_eof(), get_write_buffer_size(), etc.
    # For now, these are not strictly needed by the current Http1Protocol implementation.


class TestHttp1Protocol(unittest.TestCase):
    def setUp(self):
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)

        self.mock_app_handler = AsyncMock(spec=Callable[[ParsedHttpRequest], Awaitable[ServerHttpResponse]])
        self.protocol = Http1Protocol(self.mock_app_handler)
        self.transport = MockTransport()
        self.protocol.connection_made(self.transport)

    def tearDown(self):
        self.loop.close()

    def _run_parser_for_request(self, http_request_bytes: bytes):
        self.protocol.data_received(http_request_bytes)
        # Allow tasks scheduled by data_received (like _handle_request) to run
        # This is a common way to wait for create_task events in asyncio testing
        self.loop.run_until_complete(asyncio.sleep(0))


    def test_handle_simple_get_request(self):
        # Prepare a mock response from the app_handler
        mock_response_body = HttpBody(b"Test GET response")
        mock_response_headers = HttpHeaders([(HttpHeaderKey("Content-Type"), HttpHeaderValue("text/plain"))])
        self.mock_app_handler.return_value = ServerHttpResponse(
            status_code=HttpStatusCode(200),
            headers=mock_response_headers,
            body=mock_response_body
        )

        http_request = b"GET /testpath HTTP/1.1\r\nHost: example.com\r\n\r\n"
        self._run_parser_for_request(http_request)

        # Assert app_handler was called correctly
        self.mock_app_handler.assert_called_once()
        called_request: ParsedHttpRequest = self.mock_app_handler.call_args[0][0]
        self.assertEqual(called_request.method, HttpMethod.GET)
        self.assertEqual(called_request.path, HttpPath("/testpath"))
        self.assertIn((HttpHeaderKey("Host"), HttpHeaderValue("example.com")), called_request.headers)

        # Assert response was written to transport
        expected_response_str = (
            "HTTP/1.1 200 OK\r\n"
            "Content-Type: text/plain\r\n"
            "Content-Length: 17\r\n" # Length of "Test GET response"
            "\r\n"
            "Test GET response"
        )
        self.assertEqual(self.transport.written_data, expected_response_str.encode('utf-8'))
        self.assertTrue(self.transport.is_closing()) # Default close behavior

    def test_handle_post_request_with_body(self):
        mock_response_body = HttpBody(b"Test POST response")
        self.mock_app_handler.return_value = ServerHttpResponse(
            status_code=HttpStatusCode(201),
            body=mock_response_body
            # No headers needed for this test of response, Http1Protocol adds Content-Length
        )

        http_request = (
            b"POST /submit HTTP/1.1\r\n"
            b"Host: example.com\r\n"
            b"Content-Type: application/json\r\n"
            b"Content-Length: 18\r\n"
            b"\r\n"
            b'{"data": "value"}'
        )
        self._run_parser_for_request(http_request)

        self.mock_app_handler.assert_called_once()
        called_request: ParsedHttpRequest = self.mock_app_handler.call_args[0][0]
        self.assertEqual(called_request.method, HttpMethod.POST)
        self.assertEqual(called_request.path, HttpPath("/submit"))
        self.assertEqual(called_request.body, HttpBody(b'{"data": "value"}'))
        self.assertEqual(called_request.get_header_value("Content-Type"), HttpHeaderValue("application/json"))


        expected_response_str = (
            "HTTP/1.1 201 Created\r\n"
            "Content-Length: 18\r\n" # Length of "Test POST response"
            "\r\n"
            "Test POST response"
        )
        self.assertEqual(self.transport.written_data, expected_response_str.encode('utf-8'))
        self.assertTrue(self.transport.is_closing())

    def test_parser_error_sends_400(self):
        http_request = b"GET /testpath HTTP/1.1\r\nInvalid Header Format\r\n\r\n"
        # No need to set mock_app_handler.return_value as it shouldn't be called

        self._run_parser_for_request(http_request) # This will trigger error in parser

        expected_response_str = (
            "HTTP/1.1 400 Bad Request\r\n"
            "Content-Type: text/plain\r\n"
            "Connection: close\r\n" # Http1Protocol adds this on error
            "Content-Length: 11\r\n" # Length of "Bad Request"
            "\r\n"
            "Bad Request"
        )
        self.assertEqual(self.transport.written_data, expected_response_str.encode('utf-8'))
        self.mock_app_handler.assert_not_called() # App handler should not be reached
        self.assertTrue(self.transport.is_closing())

    def test_unsupported_method_sends_405(self):
        # Http1Protocol's on_message_complete handles this
        http_request = b"MYMETHOD / HTTP/1.1\r\nHost: example.com\r\n\r\n"

        self._run_parser_for_request(http_request)

        expected_response_str = (
            "HTTP/1.1 405 Method Not Allowed\r\n"
            "Content-Type: text/plain\r\n"
            "Connection: close\r\n"
            "Content-Length: 25\r\n" # Length of "Method MYMETHOD Not Allowed"
            "\r\n"
            "Method MYMETHOD Not Allowed"
        )
        self.assertEqual(self.transport.written_data, expected_response_str.encode('utf-8'))
        self.mock_app_handler.assert_not_called()
        self.assertTrue(self.transport.is_closing())

    # TODO: Add test for keep-alive if that logic is made more robust in Http1Protocol
    # For now, it defaults to close.

if __name__ == "__main__":
    unittest.main()
