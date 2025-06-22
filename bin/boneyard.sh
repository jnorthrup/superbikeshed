#!/bin/bash

# Canonical keywords for your project
keywords=(
  "quic"
  "couchdb"
  "ipfs"
  "http2"
  "http 1.1"
  "relaxfactory"
  "tls"
  "zran"
  "ljson"
  "gztool"
  "threads"
  "reactor"
  "ccek"
  "CoroutineContextElementKey"
  "CoroutineContextElement"
  "CCEK"
  "trikeshed"
  "rtsgame"
)

# Output file
boneyard_file="boneyard.txt"
> "$boneyard_file"

# Collect and format hashes for each keyword
for keyword in "${keywords[@]}"; do
  git log --all --grep="$keyword" -i --format="superbikeshed:%H:%s" 2>/dev/null
done | sort | uniq >> "$boneyard_file"

echo "Boneyard written to $boneyard_file" 