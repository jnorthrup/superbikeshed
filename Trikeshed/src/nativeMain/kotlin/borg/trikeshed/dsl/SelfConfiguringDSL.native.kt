package borg.trikeshed.dsl

import kotlinx.datetime.Clock

actual fun getCurrentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()