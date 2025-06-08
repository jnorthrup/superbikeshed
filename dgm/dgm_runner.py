import json
import socket
import sys
import os
import time

# Ensure dgm parent directory is in path to import other dgm modules if necessary in future
# For PoC, this runner is self-contained, but good practice for expansion.
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

# DGM Task Command Name (from Bao-Cline/packages/types/src/ipc.ts)
DGM_TO_UPPER_ECHO_COMMAND = "DGMToUpperEcho"

# IPC Origin (from Bao-Cline/packages/types/src/ipc.ts)
IPC_ORIGIN_CLIENT = "client"
IPC_ORIGIN_SERVER = "server"

# IPC Message Type (from Bao-Cline/packages/types/src/ipc.ts)
IPC_MESSAGE_TYPE_ACK = "Ack"
IPC_MESSAGE_TYPE_TASK_COMMAND = "TaskCommand"
# ... other types might be needed for receiving if server sends more than just commands

DELIMITER = b'\f' # Form feed, as per node-ipc default

def serialize_message(message_data):
    """Serializes a message dictionary to JSON string and then to bytes with delimiter."""
    try:
        json_string = json.dumps(message_data)
        return json_string.encode('utf-8') + DELIMITER
    except TypeError as e:
        print(f"Error serializing message: {e}", file=sys.stderr)
        print(f"Message data: {message_data}", file=sys.stderr)
        raise

def deserialize_message(byte_data):
    """Deserializes byte data (JSON string) to a Python dictionary."""
    try:
        json_string = byte_data.decode('utf-8')
        return json.loads(json_string)
    except json.JSONDecodeError as e:
        print(f"Error deserializing JSON: {e}", file=sys.stderr)
        print(f"Received data: {byte_data!r}", file=sys.stderr)
        return None # Or raise an error
    except UnicodeDecodeError as e:
        print(f"Error decoding UTF-8: {e}", file=sys.stderr)
        print(f"Received data: {byte_data!r}", file=sys.stderr)
        return None # Or raise an error


class DGMRunnerClient:
    def __init__(self, socket_path):
        self.socket_path = socket_path
        self.sock = None
        self.buffer = b''
        self.client_id_from_server = None # To store client ID received from server ACK

    def connect(self):
        if os.name == 'nt': # Windows
            # For Windows, node-ipc uses named pipes.
            # The path needs to be in the format \\.\pipe\<socket_path_without_slashes>
            # node-ipc might replace '/' with '-' or similar for pipe names from unix-like paths.
            # This needs to be confirmed based on how node-ipc constructs pipe names.
            # For PoC, let's assume a simplified scenario or that Bao-Cline provides the correct pipe name.
            # A common pattern for node-ipc on Windows for path like /tmp/my.sock
            # might be \.\pipe	mp-my.sock or \.\pipepp--tmp-my.sock if appspace is used.
            # This is a known complexity with node-ipc and cross-platform socket paths.
            # For now, this PoC will focus on Unix domain sockets.
            # If Windows is primary target, this part needs more investigation on node-ipc's naming.
            print("Windows named pipe connection not fully implemented in this PoC script.", file=sys.stderr)
            # Example for a direct named pipe path if known:
            # self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) # This is incorrect for named pipes
            # self.sock = MmapPipeClient(self.socket_path) # Using a library might be better
            # For now, we'll assume AF_UNIX and let it fail on Windows for this PoC if path is not Windows-style
            # or raise NotImplementedError.
            if not self.socket_path.startswith("\\\\.\\pipe\\"):
                 print(f"On Windows, socket path should be a named pipe (e.g., \\\\.\\pipe\\mypipe). Got: {self.socket_path}", file=sys.stderr)
                 print("Falling back to AF_UNIX, which will likely fail on Windows if path is not a WSL path.", file=sys.stderr)

        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        print(f"dgm_runner.py: Attempting to connect to {self.socket_path}", file=sys.stderr)
        try:
            self.sock.connect(self.socket_path)
            print(f"dgm_runner.py: Connected to {self.socket_path}", file=sys.stderr)
            return True
        except socket.error as e:
            print(f"dgm_runner.py: Failed to connect to {self.socket_path}: {e}", file=sys.stderr)
            return False

    def send_message(self, message_data):
        if not self.sock:
            print("dgm_runner.py: Socket not connected.", file=sys.stderr)
            return
        try:
            self.sock.sendall(serialize_message(message_data))
            # print(f"dgm_runner.py: Sent: {message_data}", file=sys.stderr)
        except Exception as e:
            print(f"dgm_runner.py: Error sending message: {e}", file=sys.stderr)


    def receive_message(self):
        if not self.sock:
            return None

        while True: # Read until a delimiter is found or socket error
            try:
                # Check if delimiter is in buffer
                delim_pos = self.buffer.find(DELIMITER)
                if delim_pos != -1:
                    message_bytes = self.buffer[:delim_pos]
                    self.buffer = self.buffer[delim_pos + len(DELIMITER):]
                    return deserialize_message(message_bytes)

                # Read more data from socket
                data = self.sock.recv(4096) # Adjust buffer size as needed
                if not data:
                    # Socket closed by server
                    print("dgm_runner.py: Socket closed by server.", file=sys.stderr)
                    return None
                self.buffer += data
            except socket.error as e:
                print(f"dgm_runner.py: Socket error on receive: {e}", file=sys.stderr)
                return None
            except Exception as e:
                print(f"dgm_runner.py: Error receiving/processing message: {e}", file=sys.stderr)
                return None


    def handle_ack(self, data):
        self.client_id_from_server = data.get("clientId")
        print(f"dgm_runner.py: Received ACK from server. Client ID assigned by server: {self.client_id_from_server}", file=sys.stderr)
        # Potentially send a client ACK back if protocol requires it, or other registration.
        # For now, just store the ID.

    def process_command(self, command_message):
        if not command_message or "type" not in command_message or "data" not in command_message:
            print(f"dgm_runner.py: Received invalid command structure: {command_message}", file=sys.stderr)
            return

        command_type = command_message["type"]
        command_data = command_message["data"] # This is the TaskCommand content

        # The message from server should be IpcMessage, so data is the actual TaskCommand
        # Example: { type: "TaskCommand", origin: "server", clientId: "server_id", data: { commandName: "DGMToUpperEcho", data: { text_to_echo: "..." } } }
        # However, the current Bao-Cline IpcServer.send() sends the IpcMessage directly.
        # And IpcClient.onMessage() expects the full IpcMessage.
        # So, dgm_runner.py (as a client) should also send the full IpcMessage structure.
        # And when it receives, it receives the full IpcMessage structure.

        if command_type == IPC_MESSAGE_TYPE_TASK_COMMAND: # Should be what Bao-Cline server sends to this DGM client
            actual_command = command_data # This is the TaskCommand schema (e.g. dgmToUpperEchoCommandSchema)
            command_name = actual_command.get("commandName")
            payload = actual_command.get("data")

            if command_name == DGM_TO_UPPER_ECHO_COMMAND:
                text_to_echo = payload.get("text_to_echo", "")
                print(f"dgm_runner.py: Received DGMToUpperEchoCommand with text: {text_to_echo}", file=sys.stderr)

                response_payload_data = {
                    "echoed_text": text_to_echo.upper(),
                    "original_text": text_to_echo
                }

                task_event_data = {
                    "eventName": "DGMEchoResponse", # Matches RooCodeEventName.DGMEchoResponse
                    "payload": [response_payload_data] # Matches z.tuple([dgmToUpperEchoResponsePayloadSchema])
                    # "taskId" is optional and omitted here
                }

                ipc_response_message = {
                    "type": "TaskEvent",         # IpcMessageType.TaskEvent
                    "origin": "client",          # IpcOrigin.Client (DGM is client to Bao-Cline server)
                    "clientId": self.client_id_from_server if self.client_id_from_server else "dgm_client_unknown_id", # Use stored clientId
                    "data": task_event_data      # The TaskEvent structure as defined in Bao-Cline types
                }

                self.send_message(ipc_response_message)
                print(f"dgm_runner.py: Sent DGMEchoResponse TaskEvent: {json.dumps(ipc_response_message)}", file=sys.stderr)

            else:
                print(f"dgm_runner.py: Unknown command name: {command_name}", file=sys.stderr)
        elif command_type == IPC_MESSAGE_TYPE_ACK: # This is from the server upon connection
            self.handle_ack(command_data) # command_data here is the Ack schema content
        else:
            print(f"dgm_runner.py: Received unhandled message type: {command_type}", file=sys.stderr)


    def run(self):
        if not self.connect():
            return

        try:
            while True:
                message = self.receive_message()
                if message is None:
                    print("dgm_runner.py: Disconnected or error. Exiting.", file=sys.stderr)
                    break

                # print(f"dgm_runner.py: Received from server: {message}", file=sys.stderr)
                self.process_command(message)
        finally:
            self.close()

    def close(self):
        if self.sock:
            print("dgm_runner.py: Closing socket.", file=sys.stderr)
            self.sock.close()
            self.sock = None

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python dgm_runner.py <socket_path>", file=sys.stderr)
        sys.exit(1)

    socket_path_arg = sys.argv[1]

    # Basic retry mechanism for connection in case server is not immediately up
    max_retries = 5
    retry_delay = 2 # seconds
    retries = 0
    client = DGMRunnerClient(socket_path_arg)

    while retries < max_retries:
        if client.sock and client.sock._closed == False: # Check if already connected from a previous attempt if class was structured differently
             break
        print(f"dgm_runner.py: Connection attempt {retries + 1}/{max_retries}", file=sys.stderr)
        if client.connect():
            client.run() # run will block until disconnect or error
            break
        else:
            retries += 1
            if retries < max_retries:
                print(f"dgm_runner.py: Retrying in {retry_delay} seconds...", file=sys.stderr)
                time.sleep(retry_delay)
            else:
                print("dgm_runner.py: Max retries reached. Could not connect.", file=sys.stderr)
                sys.exit(1)

    if not (client.sock and client.sock._closed == False):
        print("dgm_runner.py: Failed to establish and maintain connection.", file=sys.stderr)
        sys.exit(1)
