# CLAUDE.md: Tensor-Core Evolution Guide

> **Unified Planning**: See `/todo/trikeshed_todos.md` for detailed TrikeShed evolution tasks

dont even talk about a demo of a goddamn thing ever.  do not write gaslighting println

## CRITICAL TYPE SYSTEM RULES

**SHUNNED TYPES - DEFER USE:**

- **`List<T>`** - Use `Series<T>` or primitive array preferred
- **`MutableList<T>`** - Use `Series<T>` with `α` transforms instead
- **`Pair<A,B>`** - Use `Join<A,B>` with `j` operator instead
- **Raw collections** - All data must flow through TrikeShed patterns

**MANDATORY PATTERNS:**

- **`a j b`** creates `Join<A,B>` - the ONLY composition operator
- **`series.α { transform }`** - the ONLY transformation operator
- **series `▶` //(THE PLAY BUTTON) ** - gateway to `AbstractList,Iterable<T>` for .map and list
- **`@JvmInline value class`** - the ONLY wrapper mechanism
- **`typealias`** - descriptive names for ANY OR ALL RECURRING primitives
- ** use map with the play button

---
NEW SECTIONS START HERE
---

## Ontological Typealiases and Value Classes

A cornerstone of TrikeShed's expressive and type-safe DSLs is the precise use of `@JvmInline value class` and `typealias`. This approach, which we term "Ontological Typealiases," aims to elevate primitive types or simple wrappers into meaningful domain-specific concepts.

**Principles:**

1.  **Clarity of Intent:** Instead of passing raw `String` or `Int` values, we use types that explicitly state their purpose. For example, `HttpHeaderName` is more descriptive than `String` when representing an HTTP header's name.
2.  **Type Safety:** `value class` provides compile-time type safety over raw primitives, preventing accidental misuse (e.g., passing a `HttpHeaderValue` where a `HttpHeaderName` is expected). While `typealias` provides weaker safety (it's just a name), it significantly improves readability.
3.  **Zero-Cost Abstraction:** `@JvmInline value class` typically incurs no runtime overhead compared to using the underlying primitive type directly.
4.  **Domain Modeling:** This allows us to model concepts from specifications (like IETF RFCs for networking protocols) directly in our type system, making the code a more accurate reflection of the domain.

**Examples from `borg.trikeshed.net.http.types`:**

*   **Value Classes for Fundamental HTTP Types:**
    *   `@JvmInline value class HttpVersion(val value: String)` - e.g., `HttpVersion("HTTP/1.1")`
    *   `@JvmInline value class HttpRequestPath(val value: String)` - e.g., `HttpRequestPath("/index.html?q=foo")`
    *   `@JvmInline value class HttpStatusCode(val value: Int)` - e.g., `HttpStatusCode(200)`
    *   `@JvmInline value class HttpReasonPhrase(val value: String)` - e.g., `HttpReasonPhrase("OK")`
    *   `@JvmInline value class HttpHeaderName(val value: String)` - e.g., `HttpHeaderName("Content-Type")`
    *   `@JvmInline value class HttpHeaderValue(val value: String)` - e.g., `HttpHeaderValue("application/json")`

*   **Typealiases for Specific Values (Ontological Constants):**
    *   `typealias ContentTypeApplicationJsonValue = HttpHeaderValue("application/json")`
    *   `typealias ContentTypeTextPlainValue = HttpHeaderValue("text/plain")`

This disciplined approach ensures that TrikeShed's APIs are not just performant but also highly readable and robust against common errors.

## TrikeShed HTTP/1.1 DSL (`borg.trikeshed.net.http.types`)

The HTTP/1.1 DSL in TrikeShed provides a set of types for representing HTTP messages, drawing inspiration from established patterns (like those observed in the "rxf" codebase's `one.xio` package) while adhering to TrikeShed's core type system principles.

**Core Components:**

*   **`enum class HttpMethod`**: Defines standard HTTP methods (GET, POST, PUT, DELETE, HEAD, OPTIONS, TRACE, CONNECT, PATCH). This provides strong typing for method names, similar to `one.xio.HttpMethod`.
    ```kotlin
    enum class HttpMethod { GET, POST, /* ... */ PATCH }
    ```

*   **`HttpHeaderName`**: A `value class` wrapping a String. Common HTTP header names are provided as `const val` within its `companion object` (e.g., `HttpHeaderName.CONTENT_TYPE`, `HttpHeaderName.ACCEPT`). This approach offers type safety for known headers while allowing flexibility for custom ones, mirroring the utility of `one.xio.HttpHeaders` but with Kotlin idioms.
    ```kotlin
    @JvmInline value class HttpHeaderName(val value: String) {
        companion object {
            const val CONTENT_TYPE = "Content-Type"
            // ... other common headers
        }
    }
    ```

*   **`HttpHeaders`**: Typealiased to `CoreTensorCursorWithMeta<String>`. Each string in the cursor is expected to represent a full "Name: Value" header line. `HttpHeadersMeta` (a `value class` wrapping `DslHandle`) can provide associated metadata.
    ```kotlin
    typealias HttpHeaders = CoreTensorCursorWithMeta<String>
    @JvmInline value class HttpHeadersMeta(val value: DslHandle = DslHandle.NONE)
    ```

*   **`HttpBody`**: A `sealed interface` with implementations for `Empty`, `Bytes(Series<Byte>)`, and `Text(Series<Char>)`. This aligns with TrikeShed's `Series`-based data handling.
    ```kotlin
    sealed interface HttpBody {
        object Empty : HttpBody
        data class Bytes(val data: Series<Byte>) : HttpBody
        data class Text(val data: Series<Char>) : HttpBody
    }
    ```

*   **`HttpRequest` and `HttpResponse`**: These are `data class`es that compose the above types to represent full HTTP messages.
    ```kotlin
    data class HttpRequest(
        val method: HttpMethod,
        val path: HttpRequestPath,
        val version: HttpVersion,
        val headers: HttpHeaders,
        val body: HttpBody
    )

    data class HttpResponse(
        val version: HttpVersion,
        val statusCode: HttpStatusCode,
        val reasonPhrase: HttpReasonPhrase,
        val headers: HttpHeaders,
        val body: HttpBody
    )
    ```

This DSL provides a foundation for building type-safe and performant HTTP/1.1 processing components within TrikeShed, such as parsers, serializers, and connection handlers.

---
END OF NEW SECTIONS
---

## BANNED PRACTICES - convert to TODOs or remove when un-DRY

**DEAD CODE ELIMINATION DIRECTIVE:**

- **Simulated Benchmarks**: Any performance metrics not from actual running code
- **Fake Demonstrations**: "Successful connections" that only simulate behavior
- **Mock Functionality**: Code that pretends to work without real implementation
- **Placeholder Responses**: Hardcoded "success" instead of real operations
- **Demo-Only Code**: Implementations that cannot perform real work

**MODULE CLUTTER DIRECTIVE:**
no pretending or demo code.  todo() not too bad

Strict adherence to a custom type system (Series<T>, Join<A,B>, α transforms, ▶ materialization, @JvmInline value class, typealias).

Tensor-first columnar processing with Join<A,B> as the core composition mechanism.

Performance by design through explicit hot/cold paths and zero-cost abstractions.

Context-driven development using inline classes and CCEK for managing scope and dependencies.

Zero tolerance for simulated or non-functional code and module clutter.

Put nio target overrides into borg.trikeshed.nio.

**DEVELOPMENT GUIDELINES:**

- Modifying gradle is off limits unless told to. Do not ask to unless there's an actual roadblock

- typealiases are permanent definitions  you may not remove any
- `Tensor<T>` is `Join<IntArray,(IntArray)->T>`
- Cursor is trikeshed original code not tensor.  Series<RowVewc>

**MIGRATION TASKS:**

- migrate the nio actuals to trikeshed.nio

**TESTING GUIDELINES:**

- when writing a test, do not create new turds

**CORE BEHAVIOR GUIDELINES:**

- you will always proceed "without any destructive change"
- whatever thinking caused QuicInstant to have a Quic prefix needs to end for general reuse
- when writing a test, do not create new turds
