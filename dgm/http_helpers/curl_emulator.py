# Requires: requests
import requests

def _prepare_response(response: requests.Response = None, error_message: str = None):
    """Helper to format the output consistently."""
    if error_message:
        return {
            "status_code": None,
            "headers": None,
            "text": None,
            "json": None,
            "content": None,
            "error": error_message,
            "url": None,
        }

    response_json = None
    try:
        response_json = response.json()
    except requests.exceptions.JSONDecodeError:
        response_json = None # Or some other indicator that it's not valid JSON

    return {
        "status_code": response.status_code,
        "headers": dict(response.headers),
        "text": response.text,
        "json": response_json,
        "content": response.content,
        "error": None,
        "url": response.url,
    }

def request(
    method: str,
    url: str,
    headers: dict = None,
    params: dict = None,
    data=None,
    json_data: dict = None,
    verify_ssl: bool = True,
    allow_redirects: bool = True,
    timeout: int = 30,
):
    """
    Emulates a curl request using the requests library.

    Args:
        method: HTTP method string (e.g., "GET", "POST").
        url: The URL to request.
        headers: Optional dictionary of headers.
        params: Optional dictionary of URL parameters for GET requests.
        data: Optional dictionary, list of tuples, bytes, or file-like object
              to send in the body (for POST/PUT).
        json_data: Optional dictionary to send as JSON in the body.
                   If provided, `data` is ignored and Content-Type is set to application/json.
        verify_ssl: Boolean, whether to verify SSL certificates.
        allow_redirects: Boolean, whether to follow redirects.
        timeout: Request timeout in seconds.

    Returns:
        A dictionary containing response details or error information.
    """
    _headers = headers.copy() if headers else {}

    if json_data is not None:
        _headers["Content-Type"] = "application/json"
        data = None # Ensure data is not used if json_data is present

    try:
        # Using a session object could be beneficial for multiple calls to the same host
        # but for individual calls, requests.request is fine.
        # with requests.Session() as session:
        #     response = session.request(...)

        response = requests.request(
            method=method.upper(),
            url=url,
            headers=_headers,
            params=params,
            data=data if json_data is None else requests.compat.json.dumps(json_data),
            verify=verify_ssl,
            allow_redirects=allow_redirects,
            timeout=timeout,
        )
        response.raise_for_status()  # Raise HTTPError for bad responses (4xx or 5xx)
        return _prepare_response(response)

    except requests.exceptions.HTTPError as e:
        # Error is from the HTTP response itself (4xx/5xx)
        return _prepare_response(e.response, error_message=str(e))
    except requests.exceptions.RequestException as e:
        # Catch other requests-related errors (DNS failure, connection refused, timeout, etc.)
        return _prepare_response(error_message=str(e))
    except Exception as e:
        # Catch any other unexpected error
        return _prepare_response(error_message=f"An unexpected error occurred: {str(e)}")

def get(url: str, headers: dict = None, params: dict = None, verify_ssl: bool = True, allow_redirects: bool = True, timeout: int = 30):
    """Performs a GET request."""
    return request("GET", url, headers=headers, params=params, verify_ssl=verify_ssl, allow_redirects=allow_redirects, timeout=timeout)

def post(url: str, headers: dict = None, data=None, json_data: dict = None, verify_ssl: bool = True, allow_redirects: bool = True, timeout: int = 30):
    """Performs a POST request."""
    return request("POST", url, headers=headers, data=data, json_data=json_data, verify_ssl=verify_ssl, allow_redirects=allow_redirects, timeout=timeout)

def put(url: str, headers: dict = None, data=None, json_data: dict = None, verify_ssl: bool = True, allow_redirects: bool = True, timeout: int = 30):
    """Performs a PUT request."""
    return request("PUT", url, headers=headers, data=data, json_data=json_data, verify_ssl=verify_ssl, allow_redirects=allow_redirects, timeout=timeout)

def delete(url: str, headers: dict = None, verify_ssl: bool = True, allow_redirects: bool = True, timeout: int = 30):
    """Performs a DELETE request."""
    return request("DELETE", url, headers=headers, verify_ssl=verify_ssl, allow_redirects=allow_redirects, timeout=timeout)

if __name__ == "__main__":
    print("Testing CurlEmulator with GET request to httpbin.org...")

    # Example 1: Simple GET
    get_response = get("https://httpbin.org/get?name=test&value=123", params={"additional_param": "true"})
    print("\n--- GET Response ---")
    if get_response["error"]:
        print(f"Error: {get_response['error']}")
    else:
        print(f"Status Code: {get_response['status_code']}")
        print(f"URL: {get_response['url']}")
        # print(f"Headers: {get_response['headers']}")
        # print(f"Text Body: {get_response['text'][:200]}...") # Print first 200 chars
        if get_response['json']:
            print(f"JSON Body Args: {get_response['json'].get('args')}")
            print(f"JSON Body Headers: {get_response['json'].get('headers')}")


    # Example 2: POST JSON Data
    print("\nTesting CurlEmulator with POST (JSON) request to httpbin.org...")
    post_payload = {"message": "Hello from CurlEmulator", "value": 42}
    post_response = post("https://httpbin.org/post", json_data=post_payload, headers={"X-Custom-Header": "MyValue"})

    print("\n--- POST (JSON) Response ---")
    if post_response["error"]:
        print(f"Error: {post_response['error']}")
    else:
        print(f"Status Code: {post_response['status_code']}")
        if post_response['json']:
            print(f"Returned JSON 'data': {post_response['json'].get('data')}")
            print(f"Returned JSON 'json': {post_response['json'].get('json')}")
            print(f"Returned JSON Headers: {post_response['json'].get('headers')}")

    # Example 3: Request that might fail (e.g., bad URL or SSL verification failure if not httpbin)
    print("\nTesting CurlEmulator with a potentially failing request (invalid domain)...")
    fail_response = get("https://nonexistentdomain.invalidtld")
    print("\n--- Failing Request Response ---")
    if fail_response["error"]:
        print(f"Expected Error: {fail_response['error']}")
    else:
        print(f"Status Code: {fail_response['status_code']}")
        print(f"Text Body: {fail_response['text']}")

    print("\nTesting CurlEmulator with SSL verification disabled for a known site (badssl.com)...")
    # expired.badssl.com has an expired certificate
    ssl_test_response = get("https://expired.badssl.com/", verify_ssl=False)
    print("\n--- SSL Test (verify_ssl=False) Response ---")
    if ssl_test_response["error"]:
        print(f"Error (should not happen if verify_ssl=False): {ssl_test_response['error']}")
    else:
        print(f"Status Code: {ssl_test_response['status_code']}")
        print(f"URL: {ssl_test_response['url']}")
        print("Successfully fetched with SSL verification disabled.")

    ssl_fail_response = get("https://expired.badssl.com/", verify_ssl=True)
    print("\n--- SSL Test (verify_ssl=True) Response ---")
    if ssl_fail_response["error"]:
        print(f"Expected Error (due to SSL verification): {ssl_fail_response['error']}")
    else:
        print(f"Status Code: {ssl_fail_response['status_code']}")
        print("This should have failed SSL verification.")

    print("\nTesting with a redirect")
    redirect_response = get("http://httpbin.org/redirect/1")
    print("\n--- Redirect Response (allow_redirects=True) ---")
    if redirect_response["error"]:
        print(f"Error: {redirect_response['error']}")
    else:
        print(f"Status Code: {redirect_response['status_code']}")
        print(f"Final URL: {redirect_response['url']}") # Should be .../get

    redirect_response_no_follow = get("http://httpbin.org/redirect/1", allow_redirects=False)
    print("\n--- Redirect Response (allow_redirects=False) ---")
    if redirect_response_no_follow["error"]: # requests.HTTPError will be raised
         print(f"Error (as expected): {redirect_response_no_follow['error']}")
         print(f"Status Code (original): {redirect_response_no_follow['status_code']}") # Should be 302
    else:
        print(f"Status Code: {redirect_response_no_follow['status_code']}") # Should be 302
        print(f"URL: {redirect_response_no_follow['url']}") # Should be .../redirect/1
        if 'Location' in redirect_response_no_follow['headers']:
            print(f"Location Header: {redirect_response_no_follow['headers']['Location']}")
