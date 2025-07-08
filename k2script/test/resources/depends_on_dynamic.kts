#!/usr/bin/env kscript

@file:DependsOn("log4j:log4j:[1.2,)")
@file:DependsOn("com.offbytwo:docopt:[0.6,)")

// some pointless comment

import org.docopt.Docopt

// test the docopt dependency
val docopt = Docopt("Usage: jl <command> [options] [<joblist_file>]")

// instantiate a logger to test the log4j dependency
org.apache.log4j.Logger.getRootLogger()

println("dynamic kscript rocks!")