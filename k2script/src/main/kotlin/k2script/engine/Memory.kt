@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.engine

object Memory {
    fun withCleanup(block: () -> Unit) {
        try {
            block()
        } finally {
            System.gc()
        }
    }
} 