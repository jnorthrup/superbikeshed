#!/bin/bash

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

for keyword in "${keywords[@]}"; do
  safe_keyword=$(echo "$keyword" | tr '[:upper:]' '[:lower:]' | tr ' ' '_' | tr -cd '[:alnum:]_')
  mkdir -p museum/$safe_keyword
  grep -i "$keyword" boneyard.txt | awk -F: '{print $2}' | sort | uniq | while read hash; do
    [ -z "$hash" ] && continue
    mkdir -p museum/$safe_keyword/$hash
    git diff $hash^ $hash > museum/$safe_keyword/$hash/delta.diff
    git show --stat $hash > museum/$safe_keyword/$hash/commit.txt
    grep "$hash" boneyard.txt | awk -F: '{print $1}' | sort | uniq > museum/$safe_keyword/$hash/keywords.txt
  done
done 