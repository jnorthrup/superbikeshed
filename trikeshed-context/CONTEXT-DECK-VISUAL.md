# Context Deck DSL Visual Guide

## Concept: Contexts as Playing Cards

```
┌─────────────────┐
│   Context Deck  │  ← Top (highest priority)
├─────────────────┤
│ 🎯 Trump Card   │  priority: 100
│ "current_user"  │  
├─────────────────┤
│ 🏷️ Tagged Card  │  priority: 50
│ "feature_flags" │  tags: ["config"]
├─────────────────┤
│ 📋 Normal Card  │  priority: 10
│ "app_settings"  │
├─────────────────┤
│ 🌍 Base Card    │  priority: 0
│ "environment"   │
└─────────────────┘  ← Bottom (lowest priority)
```

## Core Operations

### 1. Building a Deck
```kotlin
val deck = contextDeck<MyContext> {
    // Base layer
    card("base", BaseContext())
    
    // Middle layer with tag
    tagged("feature", "dark_mode", FeatureContext())
    
    // High priority
    trump("active_user", UserContext())
}
```

### 2. Stack Operations
```
Push:  [A,B,C] + D → [D,A,B,C]
Pop:   [A,B,C] → A + [B,C]
Peek:  [A,B,C] → A (deck unchanged)
```

### 3. Dealing Cards
```
Original: [A♠,B♥,C♦,D♣,E♠,F♥]
         ↓ deal(3)
Hand 1: [A♠,D♣]
Hand 2: [B♥,E♠]  
Hand 3: [C♦,F♥]
```

## Visual Examples

### Web Request Context Stack
```
┌─────────────────────┐
│ 📨 Current Request  │ ← Processing this now
├─────────────────────┤
│ 🔒 User Session     │ ← Authentication context
├─────────────────────┤
│ 🏢 Tenant Config    │ ← Multi-tenant settings
├─────────────────────┤
│ ⚙️ App Config       │ ← Global configuration
└─────────────────────┘
```

### Pipeline Processing
```
Input Deck:              After filtering by "transform" tag:
┌─────────────────┐      ┌─────────────────┐
│ validate (p:90) │      │ validate (p:90) │
├─────────────────┤      ├─────────────────┤
│ enrich (p:80)   │  →   │ enrich (p:80)   │
├─────────────────┤      ├─────────────────┤
│ output (p:10)   │      │ filter (p:70)   │
├─────────────────┤      └─────────────────┘
│ filter (p:70)   │
├─────────────────┤
│ input (p:0)     │
└─────────────────┘
```

### Context Switching
```
Tenant A Deck:          Switch →          Tenant B Deck:
┌─────────────────┐                      ┌─────────────────┐
│ Tenant A Config │                      │ Tenant B Config │
│ - Premium tier  │                      │ - Basic tier    │
│ - 10GB storage  │     switcher.        │ - 1GB storage   │
├─────────────────┤     switchTo()       ├─────────────────┤
│ Features A      │                      │ Features B      │
│ - API: enabled  │                      │ - API: disabled │
└─────────────────┘                      └─────────────────┘
```

## Pattern Matching
```kotlin
card.match {
    byName("user") { 
        // Handle user context
    }
    byTag("database") {
        // Handle any database context  
    }
    byPriority(50) {
        // Handle high-priority contexts
    }
    otherwise {
        // Default handling
    }
}
```

## Deck Composition
```
Auth Deck:        Feature Deck:       Combined:
┌──────────┐      ┌──────────┐        ┌──────────┐
│ user     │  +   │ flag_a   │   =    │ user     │
│ session  │      │ flag_b   │        │ session  │
│ perms    │      │ exp_1    │        │ perms    │
└──────────┘      └──────────┘        │ flag_a   │
                                      │ flag_b   │
                                      │ exp_1    │
                                      └──────────┘
```

## Coroutine Integration
```kotlin
launch(DeckCoroutineContext(myDeck)) {
    // Deck available in coroutine scope
    val deck = coroutineContext[DeckCoroutineContext]
    deck?.findCard("config")?.use()
}
```

## Real-World Use Cases

### 1. Request Processing
- Stack: App → Tenant → User → Request
- Each layer adds context
- Pop request when done

### 2. Game State
- Stack: Config → Level → Player → ActiveEffects
- Shuffle for randomization
- Deal effects to subsystems

### 3. Multi-tenant SaaS
- Separate deck per tenant
- Switch decks on request
- Maintain isolation

### 4. Feature Flags
- Tag features by environment
- Filter by tags for deployment
- Priority-based rollout

### 5. Transaction Context
- Push transaction start
- Stack operations
- Pop on commit/rollback

## Benefits

1. **Ordered** - Natural priority/stack ordering
2. **Composable** - Merge and chain decks
3. **Type-safe** - Sealed classes for contexts
4. **Filterable** - By tags, names, priorities
5. **Coroutine-friendly** - Integration with suspend functions
6. **Testable** - Deterministic deck operations