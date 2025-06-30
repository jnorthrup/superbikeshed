import socket
import socketserver
import json
import threading
import os
import sys
import tempfile

try:
    import litellm
except ImportError:
    print("Error: litellm library not found. Please install it via 'pip install litellm'", file=sys.stderr)
    sys.exit(1)

HOST = "localhost"
PORT_FILE_NAME = "k2script_litellm_port.txt"
PORT_FILE_PATH = os.path.join(tempfile.gettempdir(), PORT_FILE_NAME)

# Ensure litellm router is not started by default if not explicitly configured
# litellm.set_verbose=True # Optional: for debugging

class LiteLLMRequestHandler(socketserver.BaseRequestHandler):
    def handle(self):
        print(f"LiteLLM Service: Client connected from {self.client_address}", flush=True)
        try:
            while True:
                # Read data until newline
                buffer = bytearray()
                while True:
                    char = self.request.recv(1)
                    if not char or char == b'\n':
                        break
                    buffer.extend(char)

                if not buffer:
                    print("LiteLLM Service: Client sent empty message or disconnected.", flush=True)
                    break # Client disconnected or sent empty line

                data_str = buffer.decode('utf-8').strip()
                if not data_str: # Handle case where only newline was sent
                    continue

                print(f"LiteLLM Service: Received raw data: {data_str}", flush=True)

                try:
                    request_data = json.loads(data_str)
                except json.JSONDecodeError as e:
                    error_msg = {"id": None, "status": "error", "error_message": f"JSON decode error: {e}"}
                    self.send_response(error_msg)
                    print(f"LiteLLM Service: JSON decode error: {e}, data: {data_str}", flush=True)
                    continue # Wait for next valid JSON command

                req_id = request_data.get("id", "unknown_id")
                print(f"LiteLLM Service: Processing request ID: {req_id}", flush=True)

                model = request_data.get("model")
                messages = request_data.get("messages")
                api_key = request_data.get("api_key") # Optional
                # Other potential litellm params can be extracted here
                # e.g., temperature, max_tokens, base_url, custom_llm_provider

                if not model or not messages:
                    error_msg = {"id": req_id, "status": "error", "error_message": "Missing 'model' or 'messages' in request"}
                    self.send_response(error_msg)
                    continue

                try:
                    # LiteLLM uses environment variables for API keys (e.g., OPENAI_API_KEY)
                    # If an api_key is passed explicitly, litellm can use it.
                    # Some providers might need specific env vars set if api_key param isn't universal.
                    # For this PoC, we rely on litellm's api_key parameter and its default env var handling.

                    call_params = {"model": model, "messages": messages}
                    if api_key:
                        call_params["api_key"] = api_key

                    # Add other supported params if present in request_data
                    for param in ["temperature", "max_tokens", "base_url", "custom_llm_provider", "stream", "timeout"]:
                        if param in request_data:
                            call_params[param] = request_data[param]

                    print(f"LiteLLM Service: Calling litellm.completion with params: {call_params}", flush=True)
                    response = litellm.completion(**call_params)

                    # Assuming non-streaming response for PoC
                    # For streaming, response would be a generator
                    content = response.choices[0].message.content if response.choices and response.choices[0].message else None

                    response_msg = {"id": req_id, "status": "success", "content": content, "raw_response": response.model_dump_json()}
                    self.send_response(response_msg)

                except litellm.exceptions.AuthenticationError as e:
                    print(f"LiteLLM Service: AuthenticationError for request ID {req_id}: {e}", flush=True)
                    error_msg = {"id": req_id, "status": "error", "error_message": f"LiteLLM Authentication Error: {e}"}
                    self.send_response(error_msg)
                except litellm.exceptions.RateLimitError as e:
                    print(f"LiteLLM Service: RateLimitError for request ID {req_id}: {e}", flush=True)
                    error_msg = {"id": req_id, "status": "error", "error_message": f"LiteLLM Rate Limit Error: {e}"}
                    self.send_response(error_msg)
                except Exception as e:
                    print(f"LiteLLM Service: Exception during litellm.completion for request ID {req_id}: {e}", flush=True)
                    error_msg = {"id": req_id, "status": "error", "error_message": f"LiteLLM call failed: {e}"}
                    self.send_response(error_msg)

        except ConnectionResetError:
            print("LiteLLM Service: Client connection reset.", flush=True)
        except BrokenPipeError:
            print("LiteLLM Service: Client connection broken pipe.", flush=True)
        except Exception as e:
            print(f"LiteLLM Service: Error in handler: {e}", flush=True)
        finally:
            print(f"LiteLLM Service: Client disconnected: {self.client_address}", flush=True)

    def send_response(self, response_data):
        try:
            response_str = json.dumps(response_data) + "\n"
            self.request.sendall(response_str.encode('utf-8'))
            # print(f"LiteLLM Service: Sent response: {response_data}", flush=True)
        except Exception as e:
            print(f"LiteLLM Service: Error sending response: {e}", flush=True)


class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    daemon_threads = True # Allow main thread to exit even if handler threads are running
    allow_reuse_address = True # Helpful for quick restarts

def main():
    # Try to bind to port 0 to get a free port assigned by the OS
    try:
        server = ThreadedTCPServer((HOST, 0), LiteLLMRequestHandler)
        actual_port = server.server_address[1]
        print(f"LiteLLM Service: Starting server on {HOST}:{actual_port}", flush=True)

        # Write the port to the file
        try:
            with open(PORT_FILE_PATH, "w") as f:
                f.write(str(actual_port))
            print(f"LiteLLM Service: Port {actual_port} written to {PORT_FILE_PATH}", flush=True)
        except IOError as e:
            print(f"LiteLLM Service: Error writing port to file {PORT_FILE_PATH}: {e}", flush=True)
            server.shutdown() # Ensure server is shutdown if port file cannot be written
            server.server_close()
            sys.exit(1)

        server_thread = threading.Thread(target=server.serve_forever)
        server_thread.daemon = True # So it exits when main thread exits
        server_thread.start()
        print(f"LiteLLM Service: Server loop running in thread: {server_thread.name}", flush=True)

        # Keep main thread alive until interrupted
        try:
            while server_thread.is_alive():
                server_thread.join(timeout=1.0) # Check every second
        except KeyboardInterrupt:
            print("LiteLLM Service: Shutdown requested via KeyboardInterrupt.", flush=True)
        finally:
            print("LiteLLM Service: Shutting down server...", flush=True)
            server.shutdown()
            server.server_close()
            print("LiteLLM Service: Server shut down.", flush=True)
            try:
                if os.path.exists(PORT_FILE_PATH):
                    os.remove(PORT_FILE_PATH)
                    print(f"LiteLLM Service: Port file {PORT_FILE_PATH} removed.", flush=True)
            except IOError as e:
                print(f"LiteLLM Service: Error removing port file {PORT_FILE_PATH}: {e}", flush=True)

    except Exception as e:
        print(f"LiteLLM Service: Failed to start server: {e}", flush=True)
        sys.exit(1)

if __name__ == "__main__":
    main()
