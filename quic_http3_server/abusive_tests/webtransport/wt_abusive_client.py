import asyncio
import ssl
import argparse
import time
import os
from typing import Optional, List, Dict, cast, Union, Set # Added Set
from pathlib import Path # Added Path
from datetime import datetime # Import datetime

from aioquic.asyncio.client import connect
from aioquic.asyncio.protocol import ClientQuicConnectionProtocol
from aioquic.quic.configuration import QuicConfiguration
from aioquic.quic.events import QuicEvent, ConnectionTerminated, StreamDataReceived, HandshakeCompleted, StreamReset, StopSending
from aioquic.h3.connection import H3Connection
from aioquic.h3.events import H3Event, HeadersReceived, DataReceived as H3DataReceived, WebTransportStreamDataReceived, StreamReset as H3StreamReset
from aioquic.quic.stream import QuicStreamID # For type hinting

# --- Configuration ---
SERVER_HOST = 'localhost'
SERVER_PORT = 4433 # Default HTTP/3 port where WebTransport is enabled
WEBTRANSPORT_PATH = "/webtransport_echo" # Path for WebTransport session

# --- Client Protocol Definition ---
class AbusiveWebTransportClientProtocol(ClientQuicConnectionProtocol):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._http: Optional[H3Connection] = None
        self.session_id: Optional[int] = None # Stream ID of the CONNECT request for WT
        self.active_wt_streams: Set[QuicStreamID] = set() # Tracks client-initiated WT streams

        self.session_established_event = asyncio.Event()
        self.stream_echo_events: Dict[QuicStreamID, asyncio.Event] = {}
        self.stream_echo_buffers: Dict[QuicStreamID, bytearray] = {}
        self.stream_reset_events: Dict[QuicStreamID, asyncio.Event] = {}


    def _ensure_http_connection(self):
        if self._http is None:
            self._http = H3Connection(self._quic, enable_webtransport=True)

    async def establish_webtransport_session(self, timeout=5.0):
        self._ensure_http_connection()
        if self._http is None: raise ConnectionError("H3 connection not initialized")

        self.session_id = self._quic.get_next_available_stream_id(is_bidirectional=True)
        headers = [
            (b":method", b"CONNECT"), (b":scheme", b"https"),
            (b":authority", SERVER_HOST.encode('utf-8') + b":" + str(SERVER_PORT).encode('utf-8')),
            (b":path", WEBTRANSPORT_PATH.encode('utf-8')),
            (b":protocol", b"webtransport"),
            (b"user-agent", b"abusive-wt-client/0.1"),
            (b"origin", f"https://{SERVER_HOST}:{SERVER_PORT}".encode('utf-8'))
        ]
        self._http.send_headers(stream_id=self.session_id, headers=headers)
        self.transmit()
        ts = datetime.now().isoformat()
        print(f"{ts} [WT_CLIENT session_stream={self.session_id}] CONNECT sent to {WEBTRANSPORT_PATH}")

        try:
            await asyncio.wait_for(self.session_established_event.wait(), timeout=timeout)
            ts_ok = datetime.now().isoformat()
            print(f"{ts_ok} [WT_CLIENT session_stream={self.session_id}] Session ESTABLISHED.")
        except asyncio.TimeoutError:
            ts_err = datetime.now().isoformat()
            print(f"{ts_err} [WT_CLIENT session_stream={self.session_id}] Timeout establishing WebTransport session.")
            self.close() # Close QUIC connection
            raise TimeoutError("WebTransport session establishment timed out")


    def quic_event_received(self, event: QuicEvent):
        ts = datetime.now().isoformat()
        if isinstance(event, HandshakeCompleted):
            print(f"{ts} [WT_CLIENT] QUIC handshake completed.")
            # Session establishment will be triggered by test functions

        # Ensure H3 connection is available for event processing
        self._ensure_http_connection()
        if self._http:
            for h3_event in self._http.handle_event(event):
                self._h3_event_received(h3_event)

    def _h3_event_received(self, event: H3Event):
        # print(f"Client H3 Event: {event}")
        if isinstance(event, HeadersReceived):
            if event.stream_id == self.session_id: # Response to our CONNECT
                status_code = None
                for name, value in event.headers:
                    if name == b":status": status_code = int(value.decode())

                ts_status = datetime.now().isoformat()
                if status_code and 200 <= status_code < 300:
                    print(f"{ts_status} [WT_CLIENT session_stream={self.session_id}] CONNECT successful (status {status_code})")
                    self.session_established_event.set()
                else:
                    print(f"{ts_status} [WT_CLIENT session_stream={self.session_id}] CONNECT failed (status {status_code})")
                    self.session_established_event.set() # Unblock, but it failed
                    self.close() # Close on failure

        elif isinstance(event, WebTransportStreamDataReceived):
            ts_data = datetime.now().isoformat()
            # print(f"{ts_data} [WT_CLIENT session_stream={event.session_id} stream={event.stream_id}] WT Stream Data: len={len(event.data)}, ended={event.stream_ended}")
            if event.stream_id in self.stream_echo_buffers:
                self.stream_echo_buffers[event.stream_id].extend(event.data)
                if event.stream_ended:
                    if event.stream_id in self.stream_echo_events:
                        self.stream_echo_events[event.stream_id].set()
            # else: print(f"{ts_data} [WT_CLIENT session_stream={event.session_id} stream={event.stream_id}] Data on unexpected WT stream")

        elif isinstance(event, H3StreamReset): # Covers WT stream resets from server
            ts_reset = datetime.now().isoformat()
            print(f"{ts_reset} [WT_CLIENT session_stream={self.session_id} stream={event.stream_id}] Stream reset by server, code {event.error_code}")
            if event.stream_id in self.stream_reset_events:
                self.stream_reset_events[event.stream_id].set()
            if event.stream_id in self.stream_echo_events and not self.stream_echo_events[event.stream_id].is_set():
                self.stream_echo_events[event.stream_id].set() # Unblock if waiting for echo

    def close(self):
        if self._quic and not self._quic.is_closed:
            ts = datetime.now().isoformat()
            print(f"{ts} [WT_CLIENT session_stream={self.session_id}] Closing QUIC connection.")
            self._quic.close(error_code=0, reason_phrase=b"Client closing") # reason_phrase must be bytes
            self.transmit()

    # --- Test Primitives ---
    async def open_webtransport_stream(self, is_unidirectional=False) -> Optional[QuicStreamID]:
        self._ensure_http_connection() # Ensure _http is there
        ts = datetime.now().isoformat()
        if not self._http or not self.session_established_event.is_set() or not self.session_id:
            print(f"{ts} [WT_CLIENT_ERROR session_stream={self.session_id}] WebTransport session not ready or _http not initialized for stream opening.")
            return None

        stream_id = self._quic.get_next_available_stream_id(is_bidirectional=not is_unidirectional)

        self.active_wt_streams.add(stream_id)
        self.stream_echo_buffers[stream_id] = bytearray()
        self.stream_echo_events[stream_id] = asyncio.Event()
        self.stream_reset_events[stream_id] = asyncio.Event()
        type_str = 'unidirectional' if is_unidirectional else 'bidirectional'
        print(f"{ts} [WT_CLIENT session_stream={self.session_id} stream={stream_id}] Opened new {type_str} stream.")
        return stream_id

    async def send_on_wt_stream(self, stream_id: QuicStreamID, data: bytes, end_stream=False):
        if not self._http: return
        self._http.send_webtransport_stream_data(stream_id=stream_id, data=data, end_stream=end_stream)
        self.transmit()
        # ts = datetime.now().isoformat()
        # print(f"{ts} [WT_CLIENT session_stream={self.session_id} stream={stream_id}] Sent data, len={len(data)}, ended={end_stream}")


    async def reset_wt_stream(self, stream_id: QuicStreamID, error_code=0):
        if not self._quic: return
        self._quic.reset_stream(stream_id, error_code)
        ts = datetime.now().isoformat()
        print(f"{ts} [WT_CLIENT session_stream={self.session_id} stream={stream_id}] Sent RESET_STREAM, code={error_code}")
        self.transmit()

    async def wait_for_stream_echo(self, stream_id: QuicStreamID, expected_data: bytes, timeout=5.0):
        if stream_id not in self.stream_echo_events: return False
        try:
            await asyncio.wait_for(self.stream_echo_events[stream_id].wait(), timeout=timeout)
            return self.stream_echo_buffers.get(stream_id) == expected_data
        except asyncio.TimeoutError:
            ts = datetime.now().isoformat()
            print(f"{ts} [WT_CLIENT_WARN session_stream={self.session_id} stream={stream_id}] Timeout waiting for echo.")
            return False

    async def wait_for_stream_reset_by_server(self, stream_id: QuicStreamID, timeout=5.0):
        if stream_id not in self.stream_reset_events: return False
        try:
            await asyncio.wait_for(self.stream_reset_events[stream_id].wait(), timeout=timeout)
            ts = datetime.now().isoformat()
            print(f"{ts} [WT_CLIENT session_stream={self.session_id} stream={stream_id}] Detected server reset.")
            return True
        except asyncio.TimeoutError:
            ts = datetime.now().isoformat()
            print(f"{ts} [WT_CLIENT_WARN session_stream={self.session_id} stream={stream_id}] Timeout waiting for server reset.")
            return False

# --- Test Scenario Functions ---
async def test_concurrent_sessions(num_sessions: int):
    ts_start = datetime.now().isoformat()
    print(f"{ts_start} [TEST_CONCURRENT_SESSIONS] Starting test with {num_sessions} sessions.")
    tasks = []
    results = {"success": 0, "failure": 0}

    async def run_single_session(session_idx: int):
        protocol: Optional[AbusiveWebTransportClientProtocol] = None
        ts_sess_start = datetime.now().isoformat()
        try:
            configuration = QuicConfiguration(is_client=True, alpn_protocols=["h3"])
            try:
                cert_file_path = Path(__file__).parent.parent.parent / "cert.pem"
                configuration.load_verify_locations(cafile=str(cert_file_path))
                # print(f"{ts_sess_start} [SESS_{session_idx}] Loaded CA cert: {cert_file_path}")
            except FileNotFoundError:
                print(f"{ts_sess_start} [SESS_{session_idx}_WARN] cert.pem not found at {cert_file_path}, TLS verification may fail.")

            async with connect(SERVER_HOST, SERVER_PORT, configuration=configuration, create_protocol=AbusiveWebTransportClientProtocol) as proto:
                protocol = cast(AbusiveWebTransportClientProtocol, proto)
                await protocol.establish_webtransport_session(timeout=3.0)
                if protocol.session_established_event.is_set() and protocol.session_id is not None:
                    # Logged by establish_webtransport_session
                    results["success"] += 1
                    # Optional quick echo test
                    # stream_id = await protocol.open_webtransport_stream()
                    # if stream_id:
                    #     await protocol.send_on_wt_stream(stream_id, b"ping", end_stream=True)
                    #     await protocol.wait_for_stream_echo(stream_id, b"ping", timeout=1.0)
                else:
                    results["failure"] += 1
        except Exception as e:
            ts_ex = datetime.now().isoformat()
            print(f"{ts_ex} [SESS_{session_idx}_EXC] Exception: {e}")
            results["failure"] += 1
        finally:
            if protocol:
                protocol.close()

    for i in range(num_sessions):
        tasks.append(asyncio.create_task(run_single_session(i)))

    await asyncio.gather(*tasks, return_exceptions=True)
    ts_end = datetime.now().isoformat()
    print(f"{ts_end} [TEST_CONCURRENT_SESSIONS] Complete. Success: {results['success']}, Failure: {results['failure']}")


async def test_stream_churn(protocol: AbusiveWebTransportClientProtocol, num_streams: int, duration_sec: int):
    ts_start = datetime.now().isoformat()
    print(f"{ts_start} [TEST_STREAM_CHURN] Starting: target_streams_batch={num_streams}, duration={duration_sec}s, session_stream={protocol.session_id}")
    await protocol.establish_webtransport_session()
    if not protocol.session_established_event.is_set():
        print(f"{datetime.now().isoformat()} [TEST_STREAM_CHURN_FAIL] Session not established.")
        return

    start_time = time.time()
    streams_opened_total = 0
    echo_success_total = 0
    payload = b"churn_data"

    # Open initial batch of streams
    initial_streams_to_open = min(num_streams, 100) # Limit initial burst a bit
    print(f"{datetime.now().isoformat()} [TEST_STREAM_CHURN] Opening initial {initial_streams_to_open} streams...")
    for i in range(initial_streams_to_open):
        if time.time() - start_time >= duration_sec:
            print(f"{datetime.now().isoformat()} [TEST_STREAM_CHURN] Initial opening phase hit duration limit.")
            break
        stream_id = await protocol.open_webtransport_stream()
        if stream_id:
            streams_opened_total +=1
            asyncio.create_task(protocol.send_on_wt_stream(stream_id, payload, end_stream=True))
        else:
            print(f"{datetime.now().isoformat()} [TEST_STREAM_CHURN_WARN] Failed to open initial stream {i}, stopping phase.")
            break
        if i % 20 == 0: await asyncio.sleep(0.001)

    print(f"{datetime.now().isoformat()} [TEST_STREAM_CHURN] Opened {streams_opened_total} initial streams. Entering continuous churn for remaining time.")

    async def churn_one_stream():
        nonlocal echo_success_total, streams_opened_total
        stream_id = await protocol.open_webtransport_stream()
        if stream_id:
            streams_opened_total +=1
            await protocol.send_on_wt_stream(stream_id, payload, end_stream=True)
            if await protocol.wait_for_stream_echo(stream_id, payload, timeout=2.0):
                echo_success_total +=1
        # else: print(f"{datetime.now().isoformat()} [TEST_STREAM_CHURN_WARN] Failed to open new stream during churn.")

    while time.time() - start_time < duration_sec:
        await churn_one_stream()
        await asyncio.sleep(0.001)

    ts_end = datetime.now().isoformat()
    print(f"{ts_end} [TEST_STREAM_CHURN] Complete. Total streams processed: {streams_opened_total}. Successful echos: {echo_success_total}. Duration: {time.time() - start_time:.2f}s")


async def test_rapid_resets(protocol: AbusiveWebTransportClientProtocol, num_streams: int):
    ts_start = datetime.now().isoformat()
    print(f"{ts_start} [TEST_RAPID_RESETS] Starting: num_streams={num_streams}, session_stream={protocol.session_id}")
    await protocol.establish_webtransport_session()
    if not protocol.session_established_event.is_set():
        print(f"{datetime.now().isoformat()} [TEST_RAPID_RESETS_FAIL] Session not established.")
        return

    resets_sent = 0
    for i in range(num_streams):
        stream_id = await protocol.open_webtransport_stream()
        if stream_id:
            await protocol.send_on_wt_stream(stream_id, b"data_before_reset_" + str(i).encode())
            await protocol.reset_wt_stream(stream_id, error_code=123 + i % 100) # Vary error code slightly
            resets_sent +=1
        else:
            print(f"{datetime.now().isoformat()} [TEST_RAPID_RESETS_WARN] Failed to open stream {i}, stopping.")
            break
        if i % 10 == 0: await asyncio.sleep(0.01)
    ts_end = datetime.now().isoformat()
    print(f"{ts_end} [TEST_RAPID_RESETS] Complete. Client Resets Sent: {resets_sent}")


async def test_large_payload_echo(protocol: AbusiveWebTransportClientProtocol, payload_size_mb: int):
    ts_start = datetime.now().isoformat()
    print(f"{ts_start} [TEST_LARGE_PAYLOAD] Starting: payload_size={payload_size_mb}MB, session_stream={protocol.session_id}")
    await protocol.establish_webtransport_session()
    if not protocol.session_established_event.is_set():
        print(f"{datetime.now().isoformat()} [TEST_LARGE_PAYLOAD_FAIL] Session not established.")
        return

    stream_id = await protocol.open_webtransport_stream()
    if not stream_id:
        print(f"{datetime.now().isoformat()} [TEST_LARGE_PAYLOAD_FAIL] Failed to open stream.")
        return

    payload = os.urandom(payload_size_mb * 1024 * 1024)
    ts_send = datetime.now().isoformat()
    print(f"{ts_send} [WT_CLIENT session_stream={protocol.session_id} stream={stream_id}] Sending large payload: len={len(payload)}")

    await protocol.send_on_wt_stream(stream_id, payload, end_stream=True)

    print(f"{datetime.now().isoformat()} [WT_CLIENT session_stream={protocol.session_id} stream={stream_id}] Large payload sent. Waiting for echo...")
    if await protocol.wait_for_stream_echo(stream_id, payload, timeout=60.0 + payload_size_mb * 10): # Increased timeout
        print(f"{datetime.now().isoformat()} [TEST_LARGE_PAYLOAD] SUCCESS!")
    else:
        buffer_content = protocol.stream_echo_buffers.get(stream_id, b"")
        ts_fail = datetime.now().isoformat()
        if stream_id in protocol.stream_reset_events and protocol.stream_reset_events[stream_id].is_set():
            print(f"{ts_fail} [TEST_LARGE_PAYLOAD_FAIL] Stream was reset by server.")
        elif buffer_content != payload:
            print(f"{ts_fail} [TEST_LARGE_PAYLOAD_FAIL] Mismatch: Sent {len(payload)}, Got {len(buffer_content)} bytes.")
        else:
            print(f"{ts_fail} [TEST_LARGE_PAYLOAD_FAIL] Timeout or other issue.")


# --- Main Execution Logic ---
async def main():
    parser = argparse.ArgumentParser(description="WebTransport Abusive Test Client")
    parser.add_argument("test_name", choices=[
        "concurrent_sessions", "stream_churn", "rapid_resets", "large_payload"
    ], help="Name of the test to run")
    parser.add_argument("--num_sessions", type=int, default=10, help="Number of concurrent sessions (for concurrent_sessions test)")
    parser.add_argument("--num_streams", type=int, default=100, help="Number of streams (for stream_churn, rapid_resets tests)")
    parser.add_argument("--duration_sec", type=int, default=10, help="Duration in seconds (for stream_churn test)")
    parser.add_argument("--payload_mb", type=int, default=1, help="Payload size in MB (for large_payload test)")

    args = parser.parse_args()

    ts_main_start = datetime.now().isoformat()
    print(f"{ts_main_start} [MAIN_CLIENT] Starting test: {args.test_name}")

    if args.test_name in ["stream_churn", "rapid_resets", "large_payload"]:
        configuration = QuicConfiguration(is_client=True, alpn_protocols=["h3"])
        try:
            cert_file_path = Path(__file__).parent.parent.parent / "cert.pem"
            configuration.load_verify_locations(cafile=str(cert_file_path))
            # print(f"{datetime.now().isoformat()} [MAIN_CLIENT_SETUP] Loaded CA cert: {cert_file_path}")
        except FileNotFoundError:
            print(f"{datetime.now().isoformat()} [MAIN_CLIENT_SETUP_WARN] cert.pem not found at {cert_file_path}.")
        # configuration.verify_mode = ssl.CERT_NONE # Use with caution

        async with connect(SERVER_HOST, SERVER_PORT, configuration=configuration, create_protocol=AbusiveWebTransportClientProtocol) as protocol_instance_cast:
            protocol_instance = cast(AbusiveWebTransportClientProtocol, protocol_instance_cast)

            if args.test_name == "stream_churn":
                await test_stream_churn(protocol_instance, args.num_streams, args.duration_sec)
            elif args.test_name == "rapid_resets":
                await test_rapid_resets(protocol_instance, args.num_streams)
            elif args.test_name == "large_payload":
                await test_large_payload_echo(protocol_instance, args.payload_mb)

            protocol_instance.close()
            await asyncio.sleep(0.1)


    elif args.test_name == "concurrent_sessions":
        await test_concurrent_sessions(args.num_sessions)

    ts_main_end = datetime.now().isoformat()
    print(f"{ts_main_end} [MAIN_CLIENT] Script finished for test: {args.test_name}")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print(f"{datetime.now().isoformat()} [MAIN_CLIENT_ABORT] Abusive client stopped by user.")
    except ConnectionRefusedError:
        print(f"{datetime.now().isoformat()} [MAIN_CLIENT_ERROR] Connection refused. Is the server running at {SERVER_HOST}:{SERVER_PORT}?")
    except Exception as e:
        print(f"{datetime.now().isoformat()} [MAIN_CLIENT_CRASH] Client main error: {e}")
        import traceback
        traceback.print_exc()
