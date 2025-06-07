#!/usr/bin/env kscript


@file:DependsOnMaven("log4j:log4j:1.2.14")
@file:DependsOnMaven("com.offbytwo:docopt:0.6.0.20150202") // neither should having comments after declartions

// some pointless comment

import org.docopt.Docopt

// test the docopt dependency
val docopt = Docopt("Usage: jl <command> [options] [<joblist_file>]")

// instantiate a logger to test the log4j dependency
org.apache.log4j.Logger.getRootLogger()

println("kscript with annotations rocks!")