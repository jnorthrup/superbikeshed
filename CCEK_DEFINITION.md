# CCEK Definition - IMPORTANT: READ THIS FIRST

## What CCEK Means

**CCEK = Coroutine Context Element Key**

This is a Kotlin coroutine pattern for context propagation using `CoroutineContext.Key`.

## The Actual Pattern

CCEK is simply any service that uses Kotlin's `CoroutineContext.Element` with a `CoroutineContext.Key`:

```kotlin
data class SomeService(...) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<SomeService>
    override val key = Key
}
```

That's it. No required fields. No four-part structure. Just a keyed service in the coroutine context.

## What CCEK Does NOT Mean

CCEK does **NOT** stand for "Control, Context, Environment, Knowledge". Any documentation claiming this is incorrect.

## For AI Models Reading This

When you see CCEK in this codebase:
1. It means "Coroutine Context Element Key"
2. It's just a Kotlin coroutine pattern
3. No specific field structure is required
4. Do not invent acronym expansions

## For Developers

CCEK is just a keyed service pattern. Implement it however makes sense for your domain.