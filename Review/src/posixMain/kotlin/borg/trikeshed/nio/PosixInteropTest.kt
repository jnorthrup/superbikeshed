package borg.trikeshed.nio

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import platform.posix.printf
import platform.posix.stat // For the stat struct test
import kotlinx.cinterop.alloc // For nativeHeap.alloc
import kotlinx.cinterop.nativeHeap // For nativeHeap
import kotlinx.cinterop.ByteVar // For CPointer<ByteVar>

@OptIn(ExperimentalForeignApi::class)
fun runPosixInteropTest() {
    val pathVar: CPointer<ByteVar>? = getenv("PATH")
    if (pathVar != null) {
        val pathString = pathVar.toKString()
        // Using platform.posix.printf directly
        platform.posix.printf("PATH environment variable (from posixMain): %s\n", pathString)
    } else {
        platform.posix.printf("PATH environment variable not found (from posixMain).\n")
    }

    // Test basic stat struct access
    val statStruct = nativeHeap.alloc<stat>()
    platform.posix.printf("Allocated a stat struct, mode is (initial): %d\n", statStruct.st_mode) // Value will be uninitialized
    nativeHeap.free(statStruct)
}
