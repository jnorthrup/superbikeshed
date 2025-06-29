#!/bin/bash

TRADED=($@)

x=${TRADED[*]}
y=\(${x// /\|}\)

$(dirname $0)/tweeze.sh | grep --extended-regexp "^${y}\:" | while read; do
  a=$(echo $REPLY | cut -f2- -d:)
  b=$(echo $REPLY | cut -f1 -d:)
  for j in $b; do
    echo "for i in ${a}; do $(dirname $0)/fetchklines.sh $j" '$i ; done'
  done
done
#
#
#for i in BIDR BNB BRL BTC BUSD DOWNUSDT EUR GBP TRY UPUSDT USDT   ; do ./fetchklines.sh DOT $i&done
