package borg.trikeshed.trie

import borg.trikeshed.lib.*

// Key type for the trie (can be String, ByteArray, etc.)
typealias PatriciaKey = String

// Children: MetaSeries from Char to PatriciaNode
// MetaSeries<A, T> = Join<A, (A) -> T>
typealias PatriciaChildren<V, M> = MetaSeries<Char, PatriciaNode<V, M>>

// Ouroboros Patricia Trie Node: value, meta, and children (self-referential)
data class PatriciaNode<V, M>(
    val value: V?,
    val meta: M,
    val children: PatriciaChildren<V, M>
)

// Empty node factory
object EmptyMeta

fun <V, M> emptyPatriciaNode(meta: M = EmptyMeta): PatriciaNode<V, M> =
    PatriciaNode(value = null, meta = meta, children = 0 j { error("No children") })

// Insert operation (recursive, idiomatic)
fun <V, M> PatriciaNode<V, M>.insert(key: PatriciaKey, value: V, meta: M = this.meta, depth: Int = 0): PatriciaNode<V, M> {
    if (depth == key.length) {
        return copy(value = value, meta = meta)
    }
    val c = key[depth]
    val child = try { children.b(c) } catch (_: Exception) { null } ?: emptyPatriciaNode(meta)
    val newChild = child.insert(key, value, meta, depth + 1)
    val allChars = children.a
    val newSize = if ((0 until allChars).any { children.b(it.toChar()) != null }) allChars else allChars + 1
    val newChildren = newSize j { i ->
        val ch = i.toChar()
        when {
            ch == c -> newChild
            i < children.a -> children.b(ch) ?: emptyPatriciaNode(meta)
            else -> emptyPatriciaNode(meta)
        }
    }
    return copy(children = newChildren)
}

// Lookup operation (recursive)
fun <V, M> PatriciaNode<V, M>.lookup(key: PatriciaKey, depth: Int = 0): V? {
    if (depth == key.length) return value
    val c = key[depth]
    val child = try { children.b(c) } catch (_: Exception) { null } ?: return null
    return child.lookup(key, depth + 1)
}

// α transform for mapping over values (preserves structure)
fun <V, M, V2> PatriciaNode<V, M>.alpha(f: (V?) -> V2?): PatriciaNode<V2, M> =
    PatriciaNode(
        value = f(value),
        meta = meta,
        children = children.a j { i -> children.b(i.toChar()).alpha(f) }
    )

// Minimal test/demo function (not main)
fun ouroborosPatriciaTrieDemo() {
    val root = emptyPatriciaNode<String, String>(meta = "root")
        .insert("cat", "feline", meta = "animal")
        .insert("car", "vehicle", meta = "machine")
        .insert("dog", "canine", meta = "animal")

    val animal = root.lookup("cat") // "feline"
    val vehicle = root.lookup("car") // "vehicle"
    val missing = root.lookup("cow") // null

    val uppercased = root.alpha { it?.uppercase() }
    val animalUpper = uppercased.lookup("cat") // "FELINE"

    println("cat: $animal, car: $vehicle, cow: $missing, cat upper: $animalUpper")
}

// === COMPRESSED PATRICIA TRIE (with compressed nodes) ===

data class CompressedPatriciaNode<V, M>(
    val keyFragment: String, // The compressed substring for this node
    val value: V?,           // Value if this node is a key endpoint
    val meta: M,             // Metadata (confidence, provenance, etc.)
    val children: Indexed<CompressedPatriciaNode<V, M>> // Children indexed by first char of their fragment
)

fun <V, M> emptyCompressedPatriciaNode(meta: M): CompressedPatriciaNode<V, M> =
    CompressedPatriciaNode("", null, meta, 0 j { error("No children") })

// Insert operation for compressed Patricia trie
fun <V, M> CompressedPatriciaNode<V, M>.insert(key: String, value: V, meta: M = this.meta): CompressedPatriciaNode<V, M> {
    // Find longest common prefix between this.keyFragment and key
    val commonPrefixLen = this.keyFragment.commonPrefixWith(key).length
    val nodeFrag = this.keyFragment
    val keyFrag = key

    when {
        // Case 1: Node fragment is a prefix of key (recurse into child)
        commonPrefixLen == nodeFrag.length && commonPrefixLen < keyFrag.length -> {
            val suffix = keyFrag.substring(commonPrefixLen)
            val nextChar = suffix[0]
            val childIdx = children.play.indexOfFirst { it.keyFragment.isNotEmpty() && it.keyFragment[0] == nextChar }
            val updatedChildren = if (childIdx >= 0) {
                // Update existing child
                children.a j { i ->
                    if (i == childIdx) children[i].insert(suffix, value, meta) else children[i]
                }
            } else {
                // Add new child
                val newChild = CompressedPatriciaNode(suffix, value, meta, 0 j { error("No children") })
                val newSize = children.a + 1
                newSize j { i -> if (i < children.a) children[i] else newChild }
            }
            return copy(children = updatedChildren)
        }
        // Case 2: Key is a prefix of node fragment (split node)
        commonPrefixLen < nodeFrag.length && commonPrefixLen == keyFrag.length -> {
            val nodeSuffix = nodeFrag.substring(commonPrefixLen)
            val newChild = copy(keyFragment = nodeSuffix)
            return CompressedPatriciaNode(
                keyFragment = keyFrag,
                value = value,
                meta = meta,
                children = 1 j { newChild }
            )
        }
        // Case 3: Diverge mid-fragment (split node)
        commonPrefixLen in 1 until nodeFrag.length && commonPrefixLen < keyFrag.length -> {
            val nodeSuffix = nodeFrag.substring(commonPrefixLen)
            val keySuffix = keyFrag.substring(commonPrefixLen)
            val child1 = copy(keyFragment = nodeSuffix)
            val child2 = CompressedPatriciaNode(keySuffix, value, meta, 0 j { error("No children") })
            return CompressedPatriciaNode(
                keyFragment = nodeFrag.substring(0, commonPrefixLen),
                value = null,
                meta = this.meta,
                children = 2 j { if (it == 0) child1 else child2 }
            )
        }
        // Case 4: Exact match (update value)
        commonPrefixLen == nodeFrag.length && commonPrefixLen == keyFrag.length -> {
            return copy(value = value, meta = meta)
        }
        // Case 5: No commonality (should not happen at root)
        else -> this
    }
}

// Lookup operation for compressed Patricia trie
fun <V, M> CompressedPatriciaNode<V, M>.lookup(key: String): V? {
    val nodeFrag = this.keyFragment
    val commonPrefixLen = nodeFrag.commonPrefixWith(key).length
    when {
        // Key matches node fragment exactly
        commonPrefixLen == nodeFrag.length && commonPrefixLen == key.length -> value
        // Node fragment is prefix of key, descend
        commonPrefixLen == nodeFrag.length && commonPrefixLen < key.length -> {
            val suffix = key.substring(commonPrefixLen)
            val nextChar = suffix[0]
            val child = children.play.firstOrNull { it.keyFragment.isNotEmpty() && it.keyFragment[0] == nextChar }
            child?.lookup(suffix)
        }
        // Key is not present
        else -> null
    }
}

// Minimal demo/test for compressed Patricia trie
fun compressedPatriciaTrieDemo() {
    val root = emptyCompressedPatriciaNode<String, String>(meta = "root")
        .insert("cat", "feline", meta = "animal")
        .insert("car", "vehicle", meta = "machine")
        .insert("dog", "canine", meta = "animal")
        .insert("cart", "wagon", meta = "object")

    val animal = root.lookup("cat") // "feline"
    val vehicle = root.lookup("car") // "vehicle"
    val missing = root.lookup("cow") // null
    val cart = root.lookup("cart") // "wagon"

    println("cat: $animal, car: $vehicle, cow: $missing, cart: $cart")
} 