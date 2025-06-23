@file:Suppress("NOTHING_TO_INLINE")

package borg.trikeshed.cursor

import borg.trikeshed.isam.meta.IOMemento.*
import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmOverloads
import kotlin.reflect.KClass

// Simplified cursor operations with test responses

/** Test response for cursor operations */
fun testCursorResponse(): String = "cursor_test_response"

/** Simplified cursor get operation */
fun DatabaseCursor.get(range: IntRange): DatabaseCursor {
    return listOf("mock_row_1", "mock_row_2").toIdx() as DatabaseCursor
}

/** Simplified cursor get by strings */  
fun DatabaseCursor.get(vararg columns: String): DatabaseCursor {
    return listOf("col_${columns.joinToString("_")}").toIdx() as DatabaseCursor
}

/** Simplified cursor get by ints */
fun DatabaseCursor.get(vararg indices: Int): DatabaseCursor {
    return listOf("idx_${indices.joinToString("_")}").toIdx() as DatabaseCursor
}

/** Test cursor operations */
object CursorTestOps {
    fun mockResult() = "test_cursor_result"
}