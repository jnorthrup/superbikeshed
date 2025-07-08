package org.bereft.node.execution

import kotlinx.coroutines.runBlocking
import org.bereft.node.keys.ApiKeyNode
import java.io.FileInputStream


fun main(args: Array<String>) {
    /**
     * must be reallllly early here...
     */
    if (args.size > 0) {
        System.getProperties().load(FileInputStream(args[0]))
    }
    runBlocking {
        ApiKeyNode.bootStrap()
    }
}