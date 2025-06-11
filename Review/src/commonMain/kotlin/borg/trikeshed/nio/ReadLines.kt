package borg.trikeshed.nio

import borg.trikeshed.lib.Series

expect fun readLinesSeq(path:String  ): Sequence<String>
expect fun readLines(path:String  ): Series<String>
