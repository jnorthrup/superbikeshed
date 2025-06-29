#!/usr/bin/env kscript


// See here for kscript installation instructions
// https://github.com/holgerbrandl/kscript

//@file:DependsOn("com.amazon.redshift:redshift-jdbc4:1.1.17.1017")
//@file:MavenRepository("redshift", "http://redshift-maven-repository.s3-website-us-east-1.amazonaws.com/release")
//@file:CompilerOpts("-jvm-target 1.8")

import java.sql.*


// See https://github.com/holgerbrandl/kscript/issues/220#issuecomment-503002241
// todo which one works
@file:KotlinOpts("-J'--add-modules java.sql'")
@file:CompilerOpts("-J'--add-modules java.sql'")


print("sql example is")
print(Connection.TRANSACTION_SERIALIZABLE)