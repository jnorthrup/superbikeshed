import unittest
from unittest.mock import MagicMock, call

from aioquic.h3.events import DataReceived, HeadersReceived, H3Event
# Assuming server.py is in the same directory or accessible via PYTHONPATH
from server import Http3ServerProtocol, HttpRequest, HttpResponse

# Dummy QuicConnection for protocol initialization
class DummyQuicConnection:
    def __init__(self):
        self._quic_logger = None # Http3ServerProtocol might access this
        self.configuration = MagicMock() # Http3ServerProtocol might access this
        self.configuration.quic_logger = None

class TestHttp3ServerProtocol(unittest.TestCase):

    def setUp(self):
        # Create a dummy QuicConnection object, as Http3ServerProtocol expects it.
        self.mock_quic_connection = DummyQuicConnection()

        # Instantiate the protocol.
        # The QUIC and H3Connection objects are internal to Http3ServerProtocol.
        # We will mock _http specifically after protocol initialization.
        self.protocol = Http3ServerProtocol(self.mock_quic_connection, stream_id=None) # stream_id might not be used by constructor

        # Mock the H3Connection (_http) that would be created internally.
        # Http3ServerProtocol initializes _http to None and then sets it.
        # We need to ensure _http is mocked before _h3_event_received is called.
        self.mock_http = MagicMock()
        self.protocol._http = self.mock_http # Directly assign the mock

        # Also mock the _quic attribute if it's used directly by methods we test,
        # though _http is the primary interface for sending H3 responses.
        self.protocol._quic = self.mock_quic_connection


    def _create_headers_event(self, stream_id: int, headers: list[tuple[bytes, bytes]], stream_ended: bool = False) -> HeadersReceived:
        event = HeadersReceived(stream_id=stream_id, headers=headers, stream_ended=stream_ended, push_id=None)
        return event

    def _create_data_event(self, stream_id: int, data: bytes, stream_ended: bool = False) -> DataReceived:
        event = DataReceived(stream_id=stream_id, data=data, stream_ended=stream_ended, flow_id=None) # flow_id deprecated
        return event

    def assert_response_sent(self, stream_id: int, status_code: int, expected_headers: dict, expected_body: bytes):
        # Check headers
        expected_h3_headers = [(b":status", str(status_code).encode()), (b"server", b"aioquic-h3")]
        for k, v in expected_headers.items():
            expected_h3_headers.append((k.encode(), v.encode()))

        self.mock_http.send_headers.assert_any_call(stream_id=stream_id, headers=expected_h3_headers)

        # Check body
        if expected_body is not None: # Allow for no body check if None
            self.mock_http.send_data.assert_any_call(stream_id=stream_id, data=expected_body, end_stream=True)
        else: # Ensure send_headers was called with end_stream=True if no body
             self.mock_http.send_headers.assert_any_call(stream_id=stream_id, headers=expected_h3_headers)


    def test_get_root(self):
        stream_id = 1
        event = self._create_headers_event(stream_id, [(b":method", b"GET"), (b":path", b"/")], stream_ended=True)

        # Simulate H3Connection.handle_event yielding our event
        # For unit testing _h3_event_received, we can call it directly.
        self.protocol._h3_event_received(event)

        self.assert_response_sent(stream_id, 200, {"content-type": "text/plain"}, b"Hello HTTP/3 from aioquic server!")

    def test_post_root(self):
        stream_id = 2
        headers_event = self._create_headers_event(stream_id, [(b":method", b"POST"), (b":path", b"/")])
        data_event = self._create_data_event(stream_id, b"Test data", stream_ended=True)

        self.protocol._h3_event_received(headers_event)
        self.protocol._h3_event_received(data_event)

        self.assert_response_sent(stream_id, 201, {"content-type": "text/plain"}, b"Resource created.")
        # Check if request body was logged (by checking print output or if we stored it on HttpRequest)
        # For now, we assume the print in the actual code is sufficient for "logging" in this context.

    def test_put_root(self):
        stream_id = 3
        headers_event = self._create_headers_event(stream_id, [(b":method", b"PUT"), (b":path", b"/")])
        data_event = self._create_data_event(stream_id, b"Updated data", stream_ended=True)

        self.protocol._h3_event_received(headers_event)
        self.protocol._h3_event_received(data_event)

        self.assert_response_sent(stream_id, 200, {"content-type": "text/plain"}, b"Resource updated.")

    def test_delete_root(self):
        stream_id = 4
        event = self._create_headers_event(stream_id, [(b":method", b"DELETE"), (b":path", b"/")], stream_ended=True)

        self.protocol._h3_event_received(event)

        self.assert_response_sent(stream_id, 200, {"content-type": "text/plain"}, b"Resource deleted.")

    def test_not_found(self):
        stream_id = 5
        event = self._create_headers_event(stream_id, [(b":method", b"GET"), (b":path", b"/unknown")], stream_ended=True)

        self.protocol._h3_event_received(event)

        self.assert_response_sent(stream_id, 404, {"content-type": "text/plain"}, b"Not Found")

    def test_method_not_allowed(self):
        stream_id = 6
        event = self._create_headers_event(stream_id, [(b":method", b"PATCH"), (b":path", b"/")], stream_ended=True)

        self.protocol._h3_event_received(event)

        self.assert_response_sent(stream_id, 405, {"content-type": "text/plain"}, b"Method Not Allowed")

    def test_request_body_accumulation(self):
        stream_id = 7
        headers_event = self._create_headers_event(stream_id, [(b":method", b"POST"), (b":path", b"/")])

        self.protocol._h3_event_received(headers_event) # Creates entry in _active_streams

        # Check if HttpRequest object was created
        self.assertIn(stream_id, self.protocol._active_streams)
        request_obj = self.protocol._active_streams[stream_id]
        self.assertEqual(request_obj.body, b"")

        data_event1 = self._create_data_event(stream_id, b"Part1", stream_ended=False)
        self.protocol._h3_event_received(data_event1)
        self.assertEqual(request_obj.body, b"Part1")

        data_event2 = self._create_data_event(stream_id, b"Part2", stream_ended=True)
        self.protocol._h3_event_received(data_event2)
        self.assertEqual(request_obj.body, b"Part1Part2") # Body fully accumulated before handler is called

        # Handler is called when stream_ended=True for DataReceived
        self.assert_response_sent(stream_id, 201, {"content-type": "text/plain"}, b"Resource created.")
        self.assertNotIn(stream_id, self.protocol._active_streams) # Check cleanup


if __name__ == "__main__":
    unittest.main()
