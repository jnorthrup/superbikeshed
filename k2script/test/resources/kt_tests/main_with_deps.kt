#!/usr/bin/env kscript

@file:DependsOn("log4j:log4j:1.2.14")
@file:EntryPoint("Foo")

package test

class Foo{
    companion object {
        @JvmStatic fun main(args: Array<String>) {
            println("made it!")
            org.apache.log4j.Logger.getRootLogger()
        }
    }
}
