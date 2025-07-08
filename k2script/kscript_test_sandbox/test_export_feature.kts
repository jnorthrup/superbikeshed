#!/usr/bin/env kscript

@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:4.2.2")
@file:ProjectCoordinates(group="com.example", artifact="my-script", version="1.0.0")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

class GreetingApp : CliktCommand() {
    private val count: Int by option(help="Number of greetings").int().default(1)
    private val name: String by option(help="Name to greet").default("World")

    override fun run() {
        repeat(count) {
            println("Hello, $name! (${it + 1}/$count)")
        }
    }
}

GreetingApp().main(args)