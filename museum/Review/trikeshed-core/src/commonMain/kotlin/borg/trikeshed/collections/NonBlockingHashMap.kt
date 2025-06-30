package borg.trikeshed.collections

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.updateAndGet

// --- NonBlockingHashMap Implementation ---
// Based on Cliff Click's NonBlockingHashMap design

class NonBlockingHashMap<K : Any, V : Any> : MutableMap<K, V> {

    // --- Sentinels ----
    // Value in _vals array marking a TOMBSTONE, i.e. key is deleted
    private val TOMBSTONE: Any = Any()
    // Value in _vals array marking that CHM is resizing and this slot is copied
    private val RESIZED_VALUE: Any = Any()
    // Value in _keys array marking an empty slot (never has a value)
    private val NO_KEY: Any = Any()
    // Value in _vals array marking an empty slot that is available for use
    private val NO_VALUE: Any = Any() // Can be same as NO_KEY if distinction not needed for vals array initial state

    // --- CHM Class: The core hash table structure ---
    private class CHM<K : Any, V : Any>(size: Int) {
        // Key array holding K or NO_KEY
        val _keys: Array<AtomicRef<Any>> = Array(size) { atomic(NO_KEY as Any) }
        // Value array holding V (wrapped as Prime), TOMBSTONE, RESIZED_VALUE, or NO_VALUE
        val _vals: Array<AtomicRef<Any>> = Array(size) { atomic(NO_VALUE as Any) }

        // Pointer to the new table during a resize.
        val _newchm: AtomicRef<CHM<K, V>?> = atomic(null)

        // Approximate count of key-value pairs (updated non-atomically during put/remove, but within CAS loops)
        val _size: AtomicInt = atomic(0)
        // Count of used slots, including tombstones. Helps trigger resize.
        val _slots: AtomicInt = atomic(0)

        // Size of the table, must be power of 2
        val _size_prime: Int = size

        // Heuristic to decide when to resize, e.g., >75% full including tombstones
        val _resize_threshold: Int = (size * 0.75).toInt()
    }

    // --- Main Map State ---
    private var _chm: AtomicRef<CHM<K, V>> = atomic(CHM(MIN_SIZE)) // Start with minimal size

    // --- Constants ---
    companion object {
        private const val MIN_SIZE = 8 // Minimum size for the table (must be power of 2)
        // Reprobe limit for linear probing to avoid excessive clustering.
        // Not strictly from original NBHM, but common in open addressing. Cliff's uses reprobe limit.
        private const val REPROBE_LIMIT = 10
    }

    // --- Helper to wrap user values to distinguish from null/sentinels if V could be Any? ---
    // Since V is constrained to V: Any, user cannot insert nulls.
    // So, we don't need a PRIME wrapper for V if NO_VALUE is distinct from any valid V.
    // However, if V could be TOMBSTONE or RESIZED_VALUE, then wrapping is needed.
    // For now, assuming V cannot be TOMBSTONE or RESIZED_VALUE instances.

    // --- Hashing ---
    private fun hash(key: K): Int {
        // Basic supplemental hash function to spread bits
        var h = key.hashCode()
        h = h xor (h ushr 20) xor (h ushr 12)
        return h xor (h ushr 7) xor (h ushr 4)
    }

    // --- Core Operations ---

    private fun get_impl(chmToQuery: CHM<K, V>, key: K): Any? { // Returns V or TOMBSTONE or null (not found) or RESIZED_VALUE
        var currentChm = chmToQuery
        while (true) { // Loop to handle potential jump to new table if RESIZED_VALUE encountered
            val len = currentChm._size_prime
            val h = hash(key)
            var idx = h and (len - 1)

            for (i in 0 until REPROBE_LIMIT) {
                val k_slot = currentChm._keys[idx].value
                if (k_slot === NO_KEY) return null // Empty slot, key not found

                if (key == k_slot) { // Key match
                    val v_slot = currentChm._vals[idx].value
                    if (v_slot === RESIZED_VALUE) {
                        // Table is resizing, help copy and then restart get in the new table.
                        // help_copy should ideally return the new table.
                        currentChm = help_copy(currentChm)
                        // Restart the get operation in the new CHM from the beginning of get_impl
                        // This is done by breaking the inner for-loop and continuing the outer while(true) loop.
                        break // Break from for-loop, continue while(true) to use new currentChm
                    }
                    return v_slot // Could be V or TOMBSTONE or NO_VALUE
                }
                idx = (idx + 1) and (len - 1) // Linear probe
                if (i == REPROBE_LIMIT -1) return null // Reprobe limit hit
            }
            // If we broke from for-loop due to RESIZED_VALUE, currentChm was updated, so while(true) continues.
            // If for-loop completed without returning (e.g. reprobe limit but RESIZED_VALUE not hit, though logic should prevent this),
            // then something is wrong, or we should have returned null from loop.
            // The `if (i == REPROBE_LIMIT -1) return null` handles reprobe limit exhaustion.
        }
    }

    override fun get(key: K): V? {
        if (key == null) throw IllegalArgumentException("Null keys not allowed")

        var currentChm = _chm.value
        // If a resize is happening, help copy and get the new CHM.
        // This ensures 'get' operates on the most current table version.
        if (currentChm._newchm.value != null) {
            currentChm = help_copy(currentChm)
        }

        val v = get_impl(currentChm, key)

        @Suppress("UNCHECKED_CAST")
        return if (v != null && v !== NO_VALUE && v !== TOMBSTONE && v !== RESIZED_VALUE) {
            v as V
        } else {
            null
        }
    }

    override fun containsKey(key: K): Boolean {
        if (key == null) throw IllegalArgumentException("Null keys not allowed")
        val v = get(key)
        return v != null
    }

    override fun put(key: K, value: V): V? {
        if (key == null || value == null) throw IllegalArgumentException("Null keys or values not allowed")

        var chm = _chm.value

        main_put_loop@ while (true) {
            if (chm._newchm.value != null) {
                chm = help_copy(chm) // Help ongoing resize and get new table
                // continue@main_put_loop // Restart operation on the potentially new CHM
            }

            val len = chm._size_prime
            val h = hash(key)
            var idx = h and (len - 1)
            var reprobe_cnt = 0

            probing_loop@ while(true) {
                val k_slot = chm._keys[idx].value
                val v_slot = chm._vals[idx].value

                if (v_slot === RESIZED_VALUE) { // Slot copied to new table
                     chm = help_copy(chm) // Ensure current thread helps, then restart main loop
                     continue@main_put_loop
                }

                if (k_slot === NO_KEY) { // Found an empty slot
                    // Try to claim the key slot
                    if (chm._keys[idx].compareAndSet(NO_KEY, key)) {
                        // Key slot claimed. Now try to set the value.
                        if (chm._vals[idx].compareAndSet(NO_VALUE, value)) {
                            chm._size.incrementAndGet()
                            chm._slots.incrementAndGet()
                            return null // Successfully inserted
                        } else {
                            // Key slot claimed, but value CAS failed (e.g., another thread also CAS'd key and is setting value, or RESIZED).
                            // This specific scenario (key CAS ok, value CAS fail) indicates high contention on this exact slot.
                            // Retry the main put operation.
                            continue@main_put_loop
                        }
                    } else {
                        // CAS for key failed, means k_slot is no longer NO_KEY. Another thread put a key or is trying.
                        // Retry probe on this chm.
                        continue@probing_loop // Re-read slot at current idx or probe next
                    }
                }

                if (key == k_slot) { // Key found
                    val old_val_actual = v_slot
                    if (old_val_actual === TOMBSTONE) { // Previously deleted, now re-inserting
                         if (chm._vals[idx].compareAndSet(TOMBSTONE, value)) {
                            chm._size.incrementAndGet()
                            // _slots not incremented as it was already a tombstone (it was "used")
                            @Suppress("UNCHECKED_CAST")
                            return null // Effectively a new insert, standard put returns null for new key
                         } else {
                            // Value changed from TOMBSTONE concurrently (e.g. another put or resize). Retry main.
                            continue@main_put_loop
                         }
                    } else if (old_val_actual !== NO_VALUE /* && old_val_actual !== RESIZED_VALUE is handled above */) { // Actual value present
                        if (chm._vals[idx].compareAndSet(old_val_actual, value)) {
                            @Suppress("UNCHECKED_CAST")
                            return old_val_actual as V // Successfully updated
                        } else {
                            // Value changed concurrently. Retry main.
                            continue@main_put_loop
                        }
                    } else { // Value is NO_VALUE (another thread is in middle of put for this key?)
                        // Retry main loop, state is unstable for this slot.
                        continue@main_put_loop
                    }
                }
                // Linear probe
                idx = (idx + 1) and (len - 1)
                reprobe_cnt++
                if (reprobe_cnt >= REPROBE_LIMIT) {
                    // Trigger resize if too many probes, then retry put in the new table.
                    chm = resize(chm)
                    continue@main_put_loop // Restart in new (or same if resize failed by this thread) table.
                }
            } // End probing_loop

            // This part should ideally not be reached if probing loop correctly continues main_put_loop
            // On second thought, this logic after probing loop might be redundant if all paths in probing loop
            // correctly `continue@main_put_loop` or `continue@probing_loop`.
            // Let's ensure resize check is also inside main_put_loop if needed.

            // Check for resize condition if not triggered by reprobe limit
            // This check should be done periodically, perhaps at start of main_put_loop or if reprobe_cnt gets somewhat high.
            // The original NBHM checks slots > threshold *before* probing.
            // For now, reprobe_limit is the main trigger from within put.
            // A proactive resize check:
            if (chm._newchm.value == null && chm._slots.value >= chm._resize_threshold) {
                 chm = resize(chm)
                 // continue@main_put_loop // Ensure we use the new chm for the next attempt
            }
             // If chm was updated (e.g. by resize call or by another thread finishing resize),
             // the next iteration of main_put_loop will use it.
        } // End main_put_loop
    }

    override fun remove(key: K): V? {
        if (key == null) throw IllegalArgumentException("Null keys not allowed")

        var chm = _chm.value
        main_remove_loop@ while(true) {
            if (chm._newchm.value != null) {
                chm = help_copy(chm) // Help ongoing resize
                // continue@main_remove_loop // Restart on new CHM
            }

            val len = chm._size_prime
            val h = hash(key)
            var idx = h and (len-1)
            var reprobe_cnt = 0

            probing_loop@ while(true) {
                val k_slot = chm._keys[idx].value
                val v_slot = chm._vals[idx].value

                if (v_slot === RESIZED_VALUE) {
                    chm = help_copy(chm)
                    continue@main_remove_loop // Restart on new CHM
                }

                if (k_slot === NO_KEY) return null // Key not found

                if (key == k_slot) { // Key found
                    if (v_slot === TOMBSTONE || v_slot === NO_VALUE) { // Already deleted or empty or mid-put
                        return null
                    }
                    // Try to CAS value to TOMBSTONE
                    if (chm._vals[idx].compareAndSet(v_slot, TOMBSTONE)) {
                        chm._size.decrementAndGet()
                        // _slots remains the same as TOMBSTONE still occupies a slot
                        @Suppress("UNCHECKED_CAST")
                        return v_slot as V // Successfully removed
                    } else {
                        // Value changed concurrently (e.g. another remove, put, or resize). Retry.
                        continue@main_remove_loop
                    }
                }
                // Linear probe
                idx = (idx + 1) and (len-1)
                reprobe_cnt++
                if (reprobe_cnt >= REPROBE_LIMIT) {
                    // Key not found after reprobing. If a resize is pending, it might be in the new table.
                    // If chm._newchm is set, help_copy and retry on new table (handled by main_remove_loop start).
                    // Otherwise, key is considered not found in this CHM.
                    if (chm._newchm.value != null) { // Check again if resize started during probing
                         chm = help_copy(chm)
                         continue@main_remove_loop
                    }
                    return null // Not found
                }
            } // End probing_loop
        } // End main_remove_loop
    }

    private fun resize(chmToResize: CHM<K,V>): CHM<K,V> {
    private fun resize(chmToResize: CHM<K,V>): CHM<K,V> {
        // Check if another thread already started resize. If so, help.
        val existingNewChm = chmToResize._newchm.value
        if (existingNewChm != null) {
            return help_copy(chmToResize)
        }

        val newSize = chmToResize._size_prime * 2
        val newCreatedChm = CHM<K,V>(newSize)

        // CAS chm._newchm from null to new_chm.
        if (chmToResize._newchm.compareAndSet(null, newCreatedChm)) {
            // This thread is the one initiating the resize.
            return help_copy(chmToResize) // Start copying and then try to update main _chm
        } else {
            // Another thread won the CAS race to set _newchm. Help their resize.
            return help_copy(chmToResize)
        }
    }

    private fun help_copy(chmToCopyFrom: CHM<K,V>): CHM<K,V> {
        val newChm = chmToCopyFrom._newchm.value ?: return chmToCopyFrom // Resize not started or already finished by another thread for this old CHM instance

        // Incrementally copy slots
        // This is a simplified bulk copy for now. A real NBHM copies slot by slot, often on demand.
        for (i in 0 until chmToCopyFrom._size_prime) {
            copy_slot(chmToCopyFrom, i, newChm)
        }

        // Try to promote the new CHM to be the main one.
        // This CAS is critical. If it succeeds, subsequent operations will use newChm.
        // If it fails, another thread already did it (or a newer resize happened).
        if (_chm.compareAndSet(chmToCopyFrom, newChm)) {
            // This thread successfully updated the global _chm pointer
        }
        return newChm // Return the new table, whether this thread CASed it or another did.
    }

    private fun copy_slot(oldChm: CHM<K,V>, idx: Int, newChm: CHM<K,V>) {
        // TODO: Implement single slot copy logic
        // 1. Read key k and value v from oldChm._keys[idx] and oldChm._vals[idx].
        // 2. If v is NO_VALUE or TOMBSTONE, try to CAS oldChm._vals[idx] to RESIZED_VALUE.
        // 3. If v is an actual value:
        //    Try to CAS oldChm._vals[idx] from v to RESIZED_VALUE.
        //    If successful, then put(k, v) into newChm.
        //    If CAS fails, another thread is handling this slot (e.g. deleted it, or already copied it).
        // If k is NO_KEY, nothing to copy.
        // This needs to be idempotent.
        var oldVal = oldChm._vals[idx].value
        if (oldVal === RESIZED_VALUE) return // Already copied by someone else

        var oldKey = oldChm._keys[idx].value
        if (oldKey === NO_KEY) { // Empty slot
            // Try to mark it as copied in old table to prevent new puts.
            // This is tricky. An empty slot doesn't strictly need a RESIZED_VALUE in _vals
            // unless puts to old table are still possible and need to be redirected.
            // For now, if NO_KEY, assume it's fine.
            // A more robust way might be to CAS oldChm._keys[idx] to a "MOVED_NO_KEY" sentinel if needed.
            return
        }

        // Try to claim this slot for copying by CASing its value to RESIZED_VALUE
        // This loop handles if value changes under our feet (e.g. to TOMBSTONE or by another put)
        while(true) {
            oldKey = oldChm._keys[idx].value // Re-read key, it might become NO_KEY if deleted.
            if (oldKey === NO_KEY) { oldChm._vals[idx].compareAndSet(oldVal, RESIZED_VALUE); return } // If key got deleted, mark as done.

            oldVal = oldChm._vals[idx].value // Re-read value each time before CAS
            if (oldVal === RESIZED_VALUE) return // Another thread completed copy for this slot

            if (oldVal === NO_VALUE) { // Slot was cleared or mid-put before resize
                if (oldChm._vals[idx].compareAndSet(NO_VALUE, RESIZED_VALUE)) return
            } else if (oldVal === TOMBSTONE) {
                if (oldChm._vals[idx].compareAndSet(TOMBSTONE, RESIZED_VALUE)) return
            } else { // Actual value
                if (oldChm._vals[idx].compareAndSet(oldVal, RESIZED_VALUE)) {
                    // Successfully marked for resize, now put it into new table
                    // The put into newChm needs to handle collisions in the new table.
                    // This is simplified, assumes newChm.put is robust.
                    if (oldKey != NO_KEY && oldKey is K && oldVal is V) { // Ensure types
                        newChm.put(oldKey, oldVal) // This put will handle hashing and probing in new table
                    }
                    return
                }
            }
            // CAS failed, value changed. Loop and retry on the new value.
        }
    }


    // --- MutableMap Interface ---
    override val size: Int
        get() = _chm.value._size.value

    override fun isEmpty(): Boolean = size == 0

    override fun containsValue(value: V): Boolean {
        if (value == null) throw IllegalArgumentException("Null values not allowed")
        // Uses the ValueIterator which is designed to handle resizes.
        val iter = ValueIterator()
        while (iter.hasNext()) {
            if (iter.next() == value) {
                return true
            }
        }
        return false
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> by lazy { EntrySet() }
    override val keys: MutableSet<K> by lazy { KeySet() }
    override val values: MutableCollection<V> by lazy { ValueCollection() }

    override fun clear() {
        // TODO: Implement clear (new CHM, CAS _chm)
        // This is complex: needs to ensure subsequent operations see the cleared state.
        // Safest: replace _chm with a new, empty CHM.
        var currentChm = _chm.value
        while(true) {
            if (currentChm._newchm.value != null) { // Helping existing resize
                currentChm = help_copy(currentChm)
                // After helping, the _chm reference might have been updated by help_copy.
                // So, re-read it.
                if (_chm.value != currentChm) { // If help_copy updated the global _chm
                     currentChm = _chm.value
                     continue // Restart with the newest CHM
                }
                // If help_copy returned the new table but didn't update global _chm yet,
                // this thread might try to CAS it.
            }
            // Create a new minimal CHM
            val newEmptyChm = CHM<K,V>(MIN_SIZE)
            if (_chm.compareAndSet(currentChm, newEmptyChm)) {
                return // Successfully cleared
            }
            // CAS failed, another thread changed _chm (e.g. another clear, or a resize completed)
            currentChm = _chm.value // Re-read and retry
        }
    }

    // --- Iterators and Views ---
    // Base iterator class - weakly consistent, does not throw CME
    private abstract inner class NBHMIterator<E> : MutableIterator<E> {
        var _currentCHM: CHM<K, V> = _chm.value // Initial CHM for this iterator instance
        var _idx: Int = -1                     // Current slot index
        var _last_key: K? = null               // Last key returned by next(), for remove()
        var _next_key: K? = null               // Next key to be returned
        var _next_val: V? = null               // Next value to be returned

        init {
            advance() // Find first element
        }

        private fun advance(): Unit {
            _last_key = _next_key // Current _next_key becomes _last_key if advance is called after a next()
            _next_key = null      // Clear next key/val
            _next_val = null

            while (true) { // Loop to handle jumping to new CHM or finding next slot
                if (_currentCHM._newchm.value != null) { // Check for resize
                    _currentCHM = help_copy(_currentCHM)
                    // After helping copy, table might have changed. If iterator was part way,
                    // it's hard to re-sync perfectly. A common strategy for NBHM iterators
                    // is to restart scan from beginning of new table if resize occurs.
                    // This is a simplification and might miss elements or return duplicates if not careful.
                    // For a truly robust iterator, this needs more sophisticated state.
                    // For now, let's assume a full restart of scan from index 0 of the new CHM.
                    _idx = -1 // Restart scan in the new CHM
                }

                _idx++ // Move to next slot
                while (_idx < _currentCHM._size_prime) {
                    val key = _currentCHM._keys[_idx].value
                    if (key !== NO_KEY && key !== TOMBSTONE /* Key array doesn't use TOMBSTONE directly */) {
                        val value = _currentCHM._vals[_idx].value
                        if (value !== NO_VALUE && value !== TOMBSTONE && value !== RESIZED_VALUE) {
                            @Suppress("UNCHECKED_CAST")
                            _next_key = key as K
                            @Suppress("UNCHECKED_CAST")
                            _next_val = value as V
                            return // Found next element
                        } else if (value === RESIZED_VALUE) {
                            // Slot moved, current CHM is stale. Retry outer loop to get new CHM.
                            // This will trigger the _currentCHM._newchm.value check above.
                            _idx-- // Re-evaluate this slot index after switching to new CHM
                            break // Break from inner _idx loop, will re-check _newchm
                        }
                    }
                    _idx++
                }

                if (_idx >= _currentCHM._size_prime) { // Reached end of current CHM
                    if (_currentCHM._newchm.value != null) {
                        // If resize occurred and we finished old table, try again on new one.
                        // The initial check for _newchm should handle this.
                        // This path means we iterated fully and newchm appeared.
                        _currentCHM = help_copy(_currentCHM)
                        _idx = -1 // restart scan
                        // continue in while(true)
                    } else {
                        // No new CHM and end of table reached. No more elements.
                        return
                    }
                }
            } // End while(true) for CHM jumping
        }


        override fun hasNext(): Boolean {
            return _next_key != null
        }

        override fun remove() {
            val k = _last_key ?: throw IllegalStateException("next() not called or element already removed")
            this@NonBlockingHashMap.remove(k) // Delegate to map's remove
            _last_key = null // Prevent multiple removes
        }
    }

    private inner class KeyIterator : NBHMIterator<K>() {
        override fun next(): K {
            val k = _next_key ?: throw NoSuchElementException()
            advance()
            return k
        }
    }

    private inner class ValueIterator : NBHMIterator<V>() {
        override fun next(): V {
            val v = _next_val ?: throw NoSuchElementException()
            // Need to advance to save the key for potential remove via value iterator (not directly supported by MutableIterator<V>)
            // However, our base NBHMIterator saves _last_key based on _next_key.
            // So, we need to ensure _next_key is properly set before calling advance.
            // This is okay: _next_key and _next_val are paired.
            advance()
            return v
        }
    }

    private inner class EntryIterator : NBHMIterator<MutableMap.MutableEntry<K, V>>() {
        override fun next(): MutableMap.MutableEntry<K, V> {
            val k = _next_key ?: throw NoSuchElementException()
            val v = _next_val ?: throw NoSuchElementException() // Should be paired with k
            advance()

            return object : MutableMap.MutableEntry<K, V> {
                override val key: K = k
                override var value: V = v // Store current value at time of entry creation for this specific Entry instance

                override fun setValue(newValue: V): V {
                    if (newValue == null) throw IllegalArgumentException("Null values not allowed")
                    // Call the main map's put method to update the value.
                    // This ensures that any CAS logic and resize handling in put is respected.
                    val previousValueInMap = this@NonBlockingHashMap.put(k, newValue)

                    // The contract for MutableMap.MutableEntry.setValue is to return the old value *this entry held*.
                    val oldValueThisEntryHeld = this.value
                    this.value = newValue // Update the value stored in this specific Entry instance
                    return oldValueThisEntryHeld // Return the value this entry instance had before this call.
                }
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other !is Map.Entry<*, *>) return false
                    return key == other.key && value == other.value
                }
                override fun hashCode(): Int = key.hashCode() xor value.hashCode()
                override fun toString(): String = "$key=$value"
            }
        }
    }

    private inner class KeySet : AbstractMutableSet<K>() {
        override val size: Int get() = this@NonBlockingHashMap.size
        override fun isEmpty(): Boolean = this@NonBlockingHashMap.isEmpty()
        override fun contains(element: K): Boolean = this@NonBlockingHashMap.containsKey(element)
        override fun clear() = this@NonBlockingHashMap.clear()
        override fun iterator(): MutableIterator<K> = KeyIterator()
        override fun remove(element: K): Boolean {
            return this@NonBlockingHashMap.remove(element) != null
        }
    }

    private inner class ValueCollection : AbstractMutableCollection<V>() {
        override val size: Int get() = this@NonBlockingHashMap.size
        override fun isEmpty(): Boolean = this@NonBlockingHashMap.isEmpty()
        override fun contains(element: V): Boolean = this@NonBlockingHashMap.containsValue(element) // Relies on map's containsValue
        override fun clear() = this@NonBlockingHashMap.clear()
        override fun iterator(): MutableIterator<V> = ValueIterator()
        // remove(element: V) would need to iterate and find a key for that value, then remove key. Complex and inefficient.
        // Default AbstractMutableCollection.remove will use iterator.
    }

    private inner class EntrySet : AbstractMutableSet<MutableMap.MutableEntry<K,V>>() {
        override val size: Int get() = this@NonBlockingHashMap.size
        override fun isEmpty(): Boolean = this@NonBlockingHashMap.isEmpty()
        override fun clear() = this@NonBlockingHashMap.clear()
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = EntryIterator()
        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
            val v = this@NonBlockingHashMap.get(element.key)
            return v != null && v == element.value
        }
        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
            // Implements remove(key, value) behavior
            // Need to add remove(key, value) to NonBlockingHashMap for this to be correct.
            return this@NonBlockingHashMap.remove(element.key, element.value)
        }
    }

    // Required by MutableMap.entries.remove(element)
    fun remove(key: K, value: V): Boolean {
        if (key == null || value == null) throw IllegalArgumentException("Null key or value not allowed")

        var chm = _chm.value
        main_remove_if_value_loop@ while(true) {
            if (chm._newchm.value != null) {
                chm = help_copy(chm)
            }

            val len = chm._size_prime
            val h = hash(key)
            var idx = h and (len-1)
            var reprobe_cnt = 0

            probing_loop@ while(true) {
                val k_slot = chm._keys[idx].value
                val v_slot = chm._vals[idx].value

                if (v_slot === RESIZED_VALUE) {
                    chm = help_copy(chm)
                    continue@main_remove_if_value_loop
                }

                if (k_slot === NO_KEY) return false // Key not found

                if (key == k_slot) { // Key found
                    if (v_slot == value) { // Value matches
                        // Try to CAS value to TOMBSTONE
                        if (chm._vals[idx].compareAndSet(v_slot, TOMBSTONE)) {
                            chm._size.decrementAndGet()
                            return true // Successfully removed
                        } else {
                            // Value changed concurrently. Retry.
                            continue@main_remove_if_value_loop
                        }
                    } else { // Key found, but value does not match
                        return false
                    }
                }
                // Linear probe
                idx = (idx + 1) and (len-1)
                reprobe_cnt++
                if (reprobe_cnt >= REPROBE_LIMIT) {
                    if (chm._newchm.value != null) {
                         chm = help_copy(chm)
                         continue@main_remove_if_value_loop
                    }
                    return false // Not found
                }
            }
        }
    }
}
