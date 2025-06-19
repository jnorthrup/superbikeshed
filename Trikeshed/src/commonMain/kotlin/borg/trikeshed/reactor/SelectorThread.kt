package borg.trikeshed.reactor

import kotlinx.coroutines.flow.MutableSharedFlow
import borg.trikeshed.lib.Join
import borg.trikeshed.reactor.SelectableChannel
import borg.trikeshed.reactor.SelectorInterface
import borg.trikeshed.reactor.SelectionKey

class SelectorThread(
    val selector: SelectorInterface,
    val taskFlow: MutableSharedFlow<suspend () -> Unit>,
    val writeFlow: MutableSharedFlow<Join<SelectableChannel, ByteBuffer>>
)
