from typing import Awaitable, Dict, Callable, Tuple # Tuple not strictly needed here but good for general typing

from .core_types import (
    HttpMethod, HttpPath, ParsedHttpRequest, ServerHttpResponse,
    HttpStatusCode, HttpHeaders, HttpHeaderKey, HttpHeaderValue, HttpBody
)

# Type for a handler function within the ApplicationRouter
# It takes a ParsedHttpRequest and returns an Awaitable ServerHttpResponse
InternalHandler = Callable[[ParsedHttpRequest], Awaitable[ServerHttpResponse]]

class ApplicationRouter:
    def __init__(self):
        # Routes: Dict[HttpPath, Dict[HttpMethod, InternalHandler]]
        self._routes: Dict[HttpPath, Dict[HttpMethod, InternalHandler]] = {
            HttpPath("/"): {
                HttpMethod.GET: self._handle_get_root,
                HttpMethod.POST: self._handle_post_root,
                HttpMethod.PUT: self._handle_put_root,
                HttpMethod.DELETE: self._handle_delete_root,
            },
            HttpPath("/test"): {
                HttpMethod.GET: self._handle_get_test,
            }
            # Add more routes as needed
        }

    async def route_request(self, request: ParsedHttpRequest) -> ServerHttpResponse:
        print(f"AppRouter: Routing request {request.method.value} {request.path}")

        # Separate path from query for routing, though handlers can still access full request.path
        # This router implementation matches on path without query parameters.
        route_path_str = request.path.split('?', 1)[0]
        route_path = HttpPath(route_path_str)

        path_handlers = self._routes.get(route_path)
        if path_handlers:
            handler = path_handlers.get(request.method)
            if handler:
                try:
                    return await handler(request)
                except Exception as e:
                    print(f"AppRouter: Handler exception for {request.method.value} {request.path}: {e}")
                    # Optionally log traceback
                    return self._error_response(HttpStatusCode(500), b"Internal Server Error")
            else:
                print(f"AppRouter: Method {request.method.value} not allowed for {route_path}")
                return self._error_response(HttpStatusCode(405), b"Method Not Allowed")
        else:
            print(f"AppRouter: Path {route_path} not found")
            return self._error_response(HttpStatusCode(404), b"Not Found")

    def _error_response(self, status_code: HttpStatusCode, body_bytes: bytes) -> ServerHttpResponse:
        return ServerHttpResponse(
            status_code=status_code,
            headers=HttpHeaders([(HttpHeaderKey("Content-Type"), HttpHeaderValue("text/plain; charset=utf-8"))]),
            body=HttpBody(body_bytes)
        )

    # --- Handler Methods ---
    # These are examples based on the original Http3ServerProtocol handlers

    async def _handle_get_root(self, request: ParsedHttpRequest) -> ServerHttpResponse:
        print(f"AppRouter: Handling GET for / (Headers: {request.headers})")
        # Simple echo of some headers for demonstration if needed, or just a static response
        body_content = "Hello from the AppRouter: GET /"
        return ServerHttpResponse(
            status_code=HttpStatusCode(200),
            headers=HttpHeaders([(HttpHeaderKey("Content-Type"), HttpHeaderValue("text/plain; charset=utf-8"))]),
            body=HttpBody(body_content.encode('utf-8'))
        )

    async def _handle_post_root(self, request: ParsedHttpRequest) -> ServerHttpResponse:
        body_str = request.body.decode('utf-8', errors='replace') if request.body else ""
        print(f"AppRouter: Handling POST for / (Headers: {request.headers}, Body: {body_str})")
        response_body = f"AppRouter: Resource created at / with body: {body_str}"
        return ServerHttpResponse(
            status_code=HttpStatusCode(201), # Created
            headers=HttpHeaders([(HttpHeaderKey("Content-Type"), HttpHeaderValue("text/plain; charset=utf-8"))]),
            body=HttpBody(response_body.encode('utf-8'))
        )

    async def _handle_put_root(self, request: ParsedHttpRequest) -> ServerHttpResponse:
        body_str = request.body.decode('utf-8', errors='replace') if request.body else ""
        print(f"AppRouter: Handling PUT for / (Headers: {request.headers}, Body: {body_str})")
        response_body = f"AppRouter: Resource updated at / with body: {body_str}"
        return ServerHttpResponse(
            status_code=HttpStatusCode(200),
            headers=HttpHeaders([(HttpHeaderKey("Content-Type"), HttpHeaderValue("text/plain; charset=utf-8"))]),
            body=HttpBody(response_body.encode('utf-8'))
        )

    async def _handle_delete_root(self, request: ParsedHttpRequest) -> ServerHttpResponse:
        print(f"AppRouter: Handling DELETE for / (Headers: {request.headers})")
        return ServerHttpResponse(
            status_code=HttpStatusCode(200), # Or 204 No Content
            headers=HttpHeaders([(HttpHeaderKey("Content-Type"), HttpHeaderValue("text/plain; charset=utf-8"))]),
            body=HttpBody(b"AppRouter: Resource deleted at /")
        )

    async def _handle_get_test(self, request: ParsedHttpRequest) -> ServerHttpResponse:
        print(f"AppRouter: Handling GET for /test (Headers: {request.headers})")
        query_params_str = "No query params"
        # Basic query param parsing (very simplified, not robust)
        if '?' in request.path:
            query_params_str = request.path.split('?', 1)[1]

        body_content = f"Hello from the AppRouter: GET /test\nQuery Params (raw): {query_params_str}"
        return ServerHttpResponse(
            status_code=HttpStatusCode(200),
            headers=HttpHeaders([(HttpHeaderKey("Content-Type"), HttpHeaderValue("text/plain; charset=utf-8"))]),
            body=HttpBody(body_content.encode('utf-8'))
        )

if __name__ == '__main__': # Example usage for router testing
    import asyncio # Required for the test runner

    async def run_router_test():
        router = ApplicationRouter()

        # Test GET /
        get_req_root = ParsedHttpRequest(method=HttpMethod.GET, path=HttpPath("/"), headers=HttpHeaders([]))
        response = await router.route_request(get_req_root)
        print(f"Test GET /: Status={response.status_code}, Body='{response.body.decode()}'")

        # Test POST /
        post_req_root = ParsedHttpRequest(method=HttpMethod.POST, path=HttpPath("/"), headers=HttpHeaders([]), body=HttpBody(b"Test POST data"))
        response = await router.route_request(post_req_root)
        print(f"Test POST /: Status={response.status_code}, Body='{response.body.decode()}'")

        # Test GET /test
        get_req_test = ParsedHttpRequest(method=HttpMethod.GET, path=HttpPath("/test"), headers=HttpHeaders([]))
        response = await router.route_request(get_req_test)
        print(f"Test GET /test: Status={response.status_code}, Body='{response.body.decode()}'")

        # Test GET /test?param=value (Path includes query for this example)
        # The router currently matches on path without query. Handler gets full path.
        get_req_test_query = ParsedHttpRequest(method=HttpMethod.GET, path=HttpPath("/test?param1=val1&param2=val2"), headers=HttpHeaders([]))
        response = await router.route_request(get_req_test_query)
        print(f"Test GET /test?param1=val1: Status={response.status_code}, Body='{response.body.decode()}'")

        # Test Not Found
        get_req_notfound = ParsedHttpRequest(method=HttpMethod.GET, path=HttpPath("/nonexistent"), headers=HttpHeaders([]))
        response = await router.route_request(get_req_notfound)
        print(f"Test GET /nonexistent: Status={response.status_code}, Body='{response.body.decode()}'")

        # Test Method Not Allowed
        patch_req_root = ParsedHttpRequest(method=HttpMethod.PATCH, path=HttpPath("/"), headers=HttpHeaders([]))
        response = await router.route_request(patch_req_root)
        print(f"Test PATCH /: Status={response.status_code}, Body='{response.body.decode()}'")

    asyncio.run(run_router_test())
