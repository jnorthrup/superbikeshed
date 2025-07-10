#!/bin/bash

# Run just the bitgraph tests
echo "Running SUMO & Yamato Bitgraph Tests..."

# Compile only the bitgraph files
kotlinc-jvm \
  -cp build/classes/kotlin/jvm/main:~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-test/1.9.23/*/kotlin-test-1.9.23.jar:~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-test-junit/1.9.23/*/kotlin-test-junit-1.9.23.jar \
  src/commonMain/kotlin/borg/trikeshed/sumo/bitgraph/*.kt \
  src/commonTest/kotlin/borg/trikeshed/sumo/bitgraph/*.kt \
  -d build/test-classes

# Run the tests
java -cp build/test-classes:build/classes/kotlin/jvm/main:~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-test/1.9.23/*/kotlin-test-1.9.23.jar:~/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-test-junit/1.9.23/*/kotlin-test-junit-1.9.23.jar \
  org.junit.runner.JUnitCore \
  borg.trikeshed.sumo.bitgraph.SumoBitgraphTest \
  borg.trikeshed.sumo.bitgraph.YamatoBitgraphTest \
  borg.trikeshed.sumo.bitgraph.BitgraphOperationsTest

echo "Tests completed!"