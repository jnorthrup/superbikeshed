#!/usr/bin/env bash
set -x

L=( $( java  -classpath "./target/classes:./target/lib/*" org.github.jnorthrup.runtime.PrintKeyPairKt ) )
echo  mesh.id=$1                                >>./cfg/ApiKeys.txt
echo  $1.keypair.private="${L[0]}"              >>./cfg/ApiKeys.txt
echo  mesh.id=$1                                >>./cfg/ShardCfg.txt
echo  $1.keypair.public="${L[1]}"               >>./cfg/ShardCfg.txt
