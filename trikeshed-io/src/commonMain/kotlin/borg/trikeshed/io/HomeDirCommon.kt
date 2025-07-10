@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

expect val homedirGet: String
expect fun mktemp(): String
expect fun rm(path: String): Boolean
expect fun mkdir(path: String): Boolean

/** emulates shell command*/
 val homedir: String by lazy { homedirGet }