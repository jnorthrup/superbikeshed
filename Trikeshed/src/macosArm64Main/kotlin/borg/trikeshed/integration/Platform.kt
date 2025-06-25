package borg.trikeshed.integration

import kotlinx.datetime.Clock

actual fun getCurrentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()