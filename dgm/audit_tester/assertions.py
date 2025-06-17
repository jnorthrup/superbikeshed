"""
This module provides custom assertion functions for the Audit Tester framework.
These functions are designed to be used within test module methods to verify
conditions and report failures by raising `AuditAssertionError`.
"""

class AuditAssertionError(AssertionError):
    """Custom assertion error for the audit tester framework.

    This error is raised by assertion functions in this module when a check fails,
    allowing the TestRunner to specifically catch and log audit failures.
    """
    pass

def assert_true(condition, message="Condition was not true"):
    """Asserts that a given condition is true.

    Args:
        condition (bool): The condition to check.
        message (str, optional): The message to include in the error if the assertion fails.
                                 Defaults to "Condition was not true".

    Raises:
        AuditAssertionError: If the condition is false.
    """
    if not condition:
        raise AuditAssertionError(message)

def assert_false(condition, message="Condition was not false"):
    """Asserts that a given condition is false.

    Args:
        condition (bool): The condition to check.
        message (str, optional): The message to include in the error if the assertion fails.
                                 Defaults to "Condition was not false".

    Raises:
        AuditAssertionError: If the condition is true.
    """
    if condition:
        raise AuditAssertionError(message)

def assert_equal(actual, expected, message=None):
    """Asserts that the actual value is equal to the expected value.

    Args:
        actual: The actual value observed.
        expected: The expected value.
        message (str, optional): The message to include in the error if the assertion fails.
                                 If None, a default message comparing actual and expected is used.

    Raises:
        AuditAssertionError: If `actual` is not equal to `expected`.
    """
    if actual != expected:
        if message is None:
            message = f"Assertion failed: Expected '{expected}', but got '{actual}'."
        raise AuditAssertionError(message)

def assert_not_equal(actual, expected, message=None):
    """Asserts that the actual value is not equal to the expected value.

    Args:
        actual: The actual value observed.
        expected: The value `actual` is expected to differ from.
        message (str, optional): The message to include in the error if the assertion fails.
                                 If None, a default message is used.

    Raises:
        AuditAssertionError: If `actual` is equal to `expected`.
    """
    if actual == expected:
        if message is None:
            message = f"Assertion failed: Expected values to be different, but both are '{actual}'."
        raise AuditAssertionError(message)

def assert_in(member, container, message=None):
    """Asserts that a member is present in a container.

    Args:
        member: The item expected to be in the container.
        container: The container (e.g., list, dict, set, string) to check.
        message (str, optional): The message to include in the error if the assertion fails.
                                 If None, a default message is used.

    Raises:
        AuditAssertionError: If `member` is not found in `container`.
    """
    if member not in container:
        if message is None:
            message = f"Assertion failed: Expected '{member}' to be in container '{container}'."
        raise AuditAssertionError(message)

def assert_not_in(member, container, message=None):
    """Asserts that a member is not present in a container.

    Args:
        member: The item expected not to be in the container.
        container: The container (e.g., list, dict, set, string) to check.
        message (str, optional): The message to include in the error if the assertion fails.
                                 If None, a default message is used.

    Raises:
        AuditAssertionError: If `member` is found in `container`.
    """
    if member in container:
        if message is None:
            message = f"Assertion failed: Expected '{member}' not to be in container '{container}'."
        raise AuditAssertionError(message)

def assert_raises(expected_exception, callable_obj, *args, **kwargs):
    """Asserts that a specific exception (or its subclass) is raised when calling `callable_obj`.

    Args:
        expected_exception (type or tuple[type, ...]): The type of exception (or tuple of types) expected to be raised.
        callable_obj (callable): The function or method to call.
        *args: Positional arguments to pass to `callable_obj`.
        **kwargs: Keyword arguments to pass to `callable_obj`.

    Raises:
        AuditAssertionError: If the expected exception (or one of the expected types) is not raised,
                             or if a different exception is raised.

    Returns:
        Exception: The instance of the raised exception if the assertion is successful.
                   This allows for further inspection of the exception if needed.
    """
    try:
        callable_obj(*args, **kwargs)
    except Exception as e:
        if not isinstance(e, expected_exception):
            # Constructing a more informative message if expected_exception is a tuple
            if isinstance(expected_exception, tuple):
                expected_names = ", ".join([ex.__name__ for ex in expected_exception])
            else:
                expected_names = expected_exception.__name__

            raise AuditAssertionError(
                f"Assertion failed: Expected exception(s) {expected_names}, "
                f"but {e.__class__.__name__} was raised with message: '{e}'"
            )
        return e # Return the exception instance

    # Constructing expected names for the final error message
    if isinstance(expected_exception, tuple):
        expected_names = ", ".join([ex.__name__ for ex in expected_exception])
    else:
        expected_names = expected_exception.__name__

    raise AuditAssertionError(
        f"Assertion failed: Expected exception(s) {expected_names} was not raised."
    )

if __name__ == '__main__':
    # Example Usage:
    print("--- Testing assertion functions ---")
    try:
        assert_true(True, "This should pass")
        print("assert_true passed (as expected)")
        assert_true(False, "This should fail")
    except AuditAssertionError as e:
        print(f"assert_true failed (as expected): {e}")

    try:
        assert_false(False, "This should pass")
        print("assert_false passed (as expected)")
        assert_false(True, "This should fail")
    except AuditAssertionError as e:
        print(f"assert_false failed (as expected): {e}")

    try:
        assert_equal(5, 5, "This should pass")
        print("assert_equal passed (as expected)")
        assert_equal("hello", "world", "Strings are not equal")
    except AuditAssertionError as e:
        print(f"assert_equal failed (as expected): {e}")

    try:
        assert_not_equal(5, 6, "This should pass")
        print("assert_not_equal passed (as expected)")
        assert_not_equal("hello", "hello")
    except AuditAssertionError as e:
        print(f"assert_not_equal failed (as expected): {e}")

    try:
        assert_in(1, [1, 2, 3], "This should pass")
        print("assert_in passed (as expected)")
        assert_in(4, [1, 2, 3])
    except AuditAssertionError as e:
        print(f"assert_in failed (as expected): {e}")

    try:
        assert_not_in(4, [1, 2, 3], "This should pass")
        print("assert_not_in passed (as expected)")
        assert_not_in(1, [1, 2, 3])
    except AuditAssertionError as e:
        print(f"assert_not_in failed (as expected): {e}")

    def my_func_value_error(x):
        if x == 0:
            raise ValueError("x cannot be zero")
        return x

    def my_func_type_error():
        raise TypeError("A type error occurred")

    try:
        assert_raises(ValueError, my_func_value_error, 0)
        print("assert_raises passed for ValueError (as expected)")

        assert_raises(TypeError, my_func_type_error)
        print("assert_raises passed for TypeError (as expected)")

        # Test case where wrong exception is raised
        print("Testing assert_raises for wrong exception (ValueError instead of TypeError):")
        assert_raises(TypeError, my_func_value_error, 0)
    except AuditAssertionError as e:
        print(f"assert_raises failed (as expected for wrong exception): {e}")

    try:
        # Test case where no exception is raised
        print("Testing assert_raises for no exception (when ValueError was expected):")
        assert_raises(ValueError, my_func_value_error, 1)
    except AuditAssertionError as e:
        print(f"assert_raises failed (as expected for no exception): {e}")

    try:
        # Test for tuple of exceptions
        print("Testing assert_raises for tuple of exceptions (ValueError or TypeError), raising ValueError:")
        assert_raises((ValueError, TypeError), my_func_value_error, 0)
        print("assert_raises passed for tuple (ValueError) (as expected)")

        print("Testing assert_raises for tuple of exceptions (ValueError or TypeError), raising TypeError:")
        assert_raises((ValueError, TypeError), my_func_type_error)
        print("assert_raises passed for tuple (TypeError) (as expected)")

        print("Testing assert_raises for tuple of exceptions (KeyError, IndexError), raising ValueError:")
        assert_raises((KeyError, IndexError), my_func_value_error, 0)
    except AuditAssertionError as e:
        print(f"assert_raises failed for tuple (as expected for wrong exception type): {e}")

    print("--- End of assertion tests ---")
