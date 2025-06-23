package borg.trikeshed.strace

actual class StraceSummary(
    actual val totalSyscalls: Int,
    actual val uniqueSyscalls: Int,
    actual val duration: Long,
    actual val patterns: List<DataPattern>
) 