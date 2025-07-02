# Fiduciary Attention: Interest, Attention, and Obligation

## Core Principle

**Interest requires attention.** In the fiduciary codebase, "fiduciary interests" are not abstract—they are the actual, user-driven interests that the system is obligated to serve. This is an extension of the fiduciary obligation: to act in the user's best financial interest, as defined by explicit attention objects.

## Attention as the Mechanism of Interest

- **Attention** is how the system focuses on something—be it a document, a corpus, a transaction, or a market opportunity.
- **Interest** is what motivates or justifies that focus. In a fiduciary context, "interest" is a legal/ethical obligation to act in the user's best interest.
- **Fiduciary attention** means that every operation claiming to serve a fiduciary interest must be rooted in an explicit attention object (e.g., `DocumentAttention`, `CorpusAttention`, or a value object like `JetsamValue`).

## Enforcing Fiduciary Obligation in Code

- The system should never act on "interest" unless it is backed by a concrete, type-safe attention object.
- All operations (gathering, gossiping, synchronizing) must be parameterized by or filtered through attention objects representing the user's actual fiduciary interests.
- No data is processed, gossiped, or acted upon unless it is the subject of explicit fiduciary attention.

## Example: JetsamGossip and Fiduciary Attention

```kotlin
// Only gather jetsam for items under current fiduciary attention
fun gatherJetsamForAttention(attention: Attention): JetsamGossip =
    JetsamGossipManager.gatherJetsam().filterByAttention(attention)

// Only gossip about jetsam that matches fiduciary attention
fun gossipToCouch(attention: Attention, jetsam: JetsamGossip) =
    JetsamGossipManager.gossipToCouch(jetsam.filterByAttention(attention))
```

## Summary

- **Interest** in fiduciary is always grounded in **attention**.
- All fiduciary actions (including JetsamGossip) must be parameterized by, and limited to, explicit attention objects representing the user's real interests.
- This encodes the fiduciary obligation in the architecture: **no attention, no interest, no action**. 