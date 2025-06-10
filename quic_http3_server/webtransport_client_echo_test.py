import asyncio
import ssl
from typing import Optional, Dict, List, cast, Union # Added Dict, cast, Union
import time # For timeouts or delays if needed

from aioquic.asyncio.client import connect
from aioquic.asyncio.protocol import ClientQuicConnectionProtocol
from aioquic.quic.configuration import QuicConfiguration
from aioquic.quic.events import QuicEvent, ConnectionTerminated, StreamDataReceived, HandshakeCompleted, DatagramReceived as QuicDatagramReceived
from aioquic.h3.connection import H3Connection
from aioquic.h3.events import H3Event, HeadersReceived, DataReceived as H3DataReceived, WebTransportStreamDataReceived, DatagramReceived as H3DatagramReceived, StreamReset as H3StreamReset

# Global event to signal WebTransport session establishment
SESSION_ESTABLISHED_EVENT = asyncio.Event()
# Global event to signal that echoed stream data has been received
ECHOED_STREAM_DATA_RECEIVED_EVENT = asyncio.Event()
ECHOED_STREAM_DATA_BUFFER: List[bytes] = []

# Global event for datagram echo
ECHOED_DATAGRAM_RECEIVED_EVENT = asyncio.Event()
ECHOED_DATAGRAM_BUFFER: List[bytes] = []


class WebTransportClientEchoProtocol(ClientQuicConnectionProtocol):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._http: Optional[H3Connection] = None
        self.target_path = "/webtransport_echo" # Path to request WebTransport session
        self.webtransport_session_id: Optional[int] = None # Stream ID of the CONNECT request
        self.client_bidirectional_stream_id: Optional[int] = None
        self.sent_test_payload = b"Hello WebTransport Stream!"
        self.sent_datagram_payload = b"Datagram Test!"

    def _ensure_http_connection(self):
        if self._http is None:
            self._http = H3Connection(self._quic, enable_webtransport=True)

    def quic_event_received(self, event: QuicEvent):
        # print(f"Client QUIC Event: {event}")
        if isinstance(event, HandshakeCompleted):
            print("Client: QUIC handshake completed. Initiating WebTransport session.")
            self._ensure_http_connection()

            # Create WebTransport session (sends CONNECT request)
            # The stream ID used for CONNECT becomes the session identifier for some H3Connection operations.
            # For client-initiated CONNECT, the client picks a bidirectional stream ID.
            self.webtransport_session_id = self._quic.get_next_available_stream_id(is_bidirectional=True)
            if self._http is None: # Should be initialized by _ensure_http_connection
                print("Client Error: _http not initialized before creating WT session")
                return

            headers = [
                (b":method", b"CONNECT"),
                (b":scheme", b"https"), # Required for CONNECT to establish authority
                (b":authority", b"localhost:4433"), # Replace with actual server authority
                (b":path", self.target_path.encode('utf-8')),
                (b":protocol", b"webtransport"),
                (b"user-agent", b"aioquic-webtransport-client/0.1"),
                (b"origin", b"https://localhost:4433") # Example origin
            ]
            self._http.send_headers(stream_id=self.webtransport_session_id, headers=headers, end_stream=False) # CONNECT stream is not ended by client here
            self.transmit() # Send the CONNECT request
            print(f"Client: Sent WebTransport CONNECT request on stream {self.webtransport_session_id} to {self.target_path}")

        elif isinstance(event, StreamDataReceived):
            # This might be data on the CONNECT stream if server sends early data, or other streams.
            # H3Connection.handle_event will process this and emit H3Events.
            pass # Let H3Connection handle it below

        elif isinstance(event, QuicDatagramReceived):
            print(f"Client: QUIC Datagram received: {event.data}")
            # Pass to H3Connection to see if it's a WebTransport datagram
            if self._http:
                for h3_event in self._http.handle_event(event):
                    self._h3_event_received(h3_event)

        # Ensure _http is initialized for subsequent event processing
        # This might be redundant if HandshakeCompleted always comes first and initializes,
        # but good for safety if other QUIC events can trigger H3 processing.
        self._ensure_http_connection()


        if self._http:
            # Pass all QUIC events that H3Connection might be interested in
            # (especially those that are not specific H3Events yet, like StreamDataReceived on control stream)
            for h3_event in self._http.handle_event(event):
                self._h3_event_received(h3_event)


    def _h3_event_received(self, event: H3Event):
        # print(f"Client H3 Event: {event}")
        if isinstance(event, HeadersReceived):
            print(f"Client: HeadersReceived on stream {event.stream_id}: {event.headers}")
            if event.stream_id == self.webtransport_session_id:
                status_code = None
                for name, value in event.headers:
                    if name == b":status":
                        status_code = int(value.decode('utf-8'))
                        break
                if status_code is not None and 200 <= status_code < 300:
                    print(f"Client: WebTransport session established successfully (status {status_code}) on stream {self.webtransport_session_id}!")
                    SESSION_ESTABLISHED_EVENT.set() # Signal that session is up
                else:
                    print(f"Client: WebTransport session failed. Status: {status_code}, Headers: {event.headers}")
                    self.close_connection() # Close on failure

        elif isinstance(event, WebTransportStreamDataReceived):
            print(f"Client: WebTransportStreamDataReceived on stream {event.stream_id}, session {event.session_id}, data: {event.data}, ended: {event.stream_ended}")
            if event.stream_id == self.client_bidirectional_stream_id:
                ECHOED_STREAM_DATA_BUFFER.append(event.data)
                if b"".join(ECHOED_STREAM_DATA_BUFFER) == self.sent_test_payload: # Simple check for full echo
                    print("Client: Full stream echo received correctly!")
                    ECHOED_STREAM_DATA_RECEIVED_EVENT.set()
                if event.stream_ended:
                    print(f"Client: Echo stream {event.stream_id} ended by server.")
                    if not ECHOED_STREAM_DATA_RECEIVED_EVENT.is_set(): # If ended before full echo
                        print("Client Warning: Stream ended before full echo was verified.")
                        ECHOED_STREAM_DATA_RECEIVED_EVENT.set() # Unblock main
            else:
                print(f"Client: Received WebTransport data on unexpected stream {event.stream_id}")

        elif isinstance(event, H3DataReceived): # Data on the HTTP/3 CONNECT stream itself after headers
            if event.stream_id == self.webtransport_session_id:
                print(f"Client: Data on WebTransport CONNECT stream {event.stream_id}: {event.data} (ended={event.stream_ended})")
                # Typically the CONNECT stream is just for handshake, not arbitrary data by default from server after 200 OK.
                # If server sends data here, it might be an error or custom protocol.

        elif isinstance(event, H3DatagramReceived): # This is the H3-level event
            print(f"Client: H3DatagramReceived on flow {event.flow_id} (session {self.webtransport_session_id}), data: {event.data}")
            if event.flow_id == self.webtransport_session_id and event.data == self.sent_datagram_payload:
                print("Client: Datagram echo received correctly!")
                ECHOED_DATAGRAM_BUFFER.append(event.data)
                ECHOED_DATAGRAM_RECEIVED_EVENT.set()
            else:
                print(f"Client: Received unexpected H3 datagram: flow_id={event.flow_id}, data={event.data}")

        elif isinstance(event, H3StreamReset):
            print(f"Client: Stream {event.stream_id} was reset by server. Error code: {event.error_code}")
            if event.stream_id == self.client_bidirectional_stream_id:
                print("Client: Test stream was reset, unblocking main.")
                ECHOED_STREAM_DATA_RECEIVED_EVENT.set() # Unblock if our test stream is reset
            elif event.stream_id == self.webtransport_session_id:
                print("Client: WebTransport session stream was reset. Unblocking main.")
                SESSION_ESTABLISHED_EVENT.set() # Unblock if session fails this way
                ECHOED_STREAM_DATA_RECEIVED_EVENT.set()
                ECHOED_DATAGRAM_RECEIVED_EVENT.set()


    def close_connection(self):
        if self._quic and not self._quic.is_closed:
            print("Client: Closing connection.")
            self._quic.close()

    # --- Client actions after session established ---
    async def perform_echo_test(self):
        if not self._http or self.webtransport_session_id is None:
            print("Client Error: HTTP connection or WebTransport session not ready for echo test.")
            return False

        # 1. Test Bidirectional Stream Echo
        print("Client: --- Testing Bidirectional Stream Echo ---")
        # Client initiates a bidirectional stream for WebTransport
        self.client_bidirectional_stream_id = self._quic.get_next_available_stream_id(is_bidirectional=True)
        print(f"Client: Creating client-initiated bidirectional stream: {self.client_bidirectional_stream_id}")

        # Associate with WebTransport session. H3Connection does this via stream_type for WebTransport.
        # For client-initiated streams, it seems you just use the stream ID directly with send_webtransport_stream_data.
        # The H3Connection on the server side will know it's a WT stream based on session.
        # The HTTP/3 spec indicates new streams for WebTransport are created using specific frame types (WEBTRANSPORT_STREAM)
        # which aioquic's H3Connection abstracts. When we call send_webtransport_stream_data on a *new* client-initiated
        # stream ID, aioquic's H3Connection should frame this correctly for WebTransport.

        self._http.send_webtransport_stream_data(
            stream_id=self.client_bidirectional_stream_id,
            data=self.sent_test_payload,
            end_stream=True # Send payload and close write side
        )
        self.transmit()
        print(f"Client: Sent '{self.sent_test_payload.decode()}' on stream {self.client_bidirectional_stream_id}")

        try:
            await asyncio.wait_for(ECHOED_STREAM_DATA_RECEIVED_EVENT.wait(), timeout=5.0)
            if b"".join(ECHOED_STREAM_DATA_BUFFER) == self.sent_test_payload:
                 print("Client: Bidirectional stream echo SUCCESS!")
            else:
                 print(f"Client: Bidirectional stream echo MISMATCH! Got: {b''.join(ECHOED_STREAM_DATA_BUFFER)}")
                 return False
        except asyncio.TimeoutError:
            print("Client: Timeout waiting for stream echo.")
            return False

        # 2. Test Datagram Echo (Optional based on server implementation)
        print("\nClient: --- Testing Datagram Echo ---")
        if hasattr(self._http, 'send_datagram'): # Check if method exists
            # The flow_id for send_datagram in H3Connection is the WebTransport session ID,
            # which is the stream ID of the initial CONNECT request.
            self._http.send_datagram(flow_id=self.webtransport_session_id, data=self.sent_datagram_payload)
            self.transmit()
            print(f"Client: Sent datagram '{self.sent_datagram_payload.decode()}' on session {self.webtransport_session_id}")
            try:
                await asyncio.wait_for(ECHOED_DATAGRAM_RECEIVED_EVENT.wait(), timeout=2.0)
                if ECHOED_DATAGRAM_BUFFER and ECHOED_DATAGRAM_BUFFER[0] == self.sent_datagram_payload:
                    print("Client: Datagram echo SUCCESS!")
                else:
                    print(f"Client: Datagram echo MISMATCH or not received! Got: {ECHOED_DATAGRAM_BUFFER}")
                    # Allow test to pass if datagrams weren't the primary test, or make it strict
            except asyncio.TimeoutError:
                print("Client: Timeout waiting for datagram echo. (This might be OK if server doesn't echo datagrams)")
        else:
            print("Client: Datagram sending not available on H3Connection. Skipping datagram test.")

        return True


async def main_client(host='localhost', port=4433):
    print(f"Client: Attempting to connect to {host}:{port} for WebTransport echo test.")
    configuration = QuicConfiguration(
        is_client=True,
        alpn_protocols=["h3"] # Specify h3 for ALPN
    )
    # Load trusted CA certificates (replace with your CA if not using default)
    # For self-signed certs, this is tricky. `aioquic` typically requires proper certs.
    # For testing with self-signed server cert, you might need to load server's cert as CA
    # or disable certificate verification (NOT recommended for production).
    # Let's try to load the generated cert as CA for testing.
    try:
        # Assuming client script is run from repository root or quic_http3_server parent
        script_dir = Path(__file__).parent
        cert_path = script_dir / "cert.pem" # Path relative to this script
        if not cert_path.exists(): # Try relative to current working directory if not found
            cert_path = Path("cert.pem")

        if cert_path.exists():
            configuration.load_verify_locations(cafile=str(cert_path))
            print(f"Client: Loaded server cert {cert_path.resolve()} as CA for verification.")
        else:
            print(f"Client Warning: Server cert {cert_path.resolve()} not found. TLS verification might fail.")
            # If server cert isn't CA, or this fails, connection will fail if server presents self-signed
            # unless verify_mode is changed. For simplicity in this test, we assume cert.pem is the one to trust.
    except Exception as e:
        print(f"Client: Error loading CA cert: {e}. Proceeding without custom CA for verify_locations.")

    # To bypass verification for local testing with self-signed cert not in trust store (use with caution):
    # configuration.verify_mode = ssl.CERT_NONE
    # print("Client Warning: TLS certificate verification is disabled (CERT_NONE).")


    async with connect(
        host,
        port,
        configuration=configuration,
        create_protocol=WebTransportClientEchoProtocol,
        wait_connected=True # Wait for QUIC handshake
    ) as client_protocol:
        client_protocol = cast(WebTransportClientEchoProtocol, client_protocol) # For type hinting

        print("Client: QUIC connection established. Waiting for WebTransport session...")
        try:
            await asyncio.wait_for(SESSION_ESTABLISHED_EVENT.wait(), timeout=5.0)
            print("Client: WebTransport session confirmed by client.")

            success = await client_protocol.perform_echo_test()
            if success:
                print("\nClient: WebTransport echo tests completed successfully.")
            else:
                print("\nClient: WebTransport echo tests completed with failures.")

        except asyncio.TimeoutError:
            print("Client: Timeout waiting for WebTransport session to establish.")
        except Exception as e:
            print(f"Client: An error occurred during WebTransport interaction: {e}")
        finally:
            print("Client: Closing connection after tests.")
            client_protocol.close_connection()
            # Ensure QUIC connection has time to send CONNECTION_CLOSE if it initiated
            await asyncio.sleep(0.1)


if __name__ == "__main__":
    # Reset global events for multiple runs if this were part of a test suite
    # For a single run, it's fine.
    from pathlib import Path # Ensure Path is available for __main__ scope if cert logic is adjusted
    try:
        asyncio.run(main_client())
    except KeyboardInterrupt:
        print("Client stopped by user.")
    except ConnectionRefusedError:
        print(f"Client Error: Connection refused. Is the server running at localhost:4433?")
    except Exception as e:
        print(f"Client main error: {e}")
