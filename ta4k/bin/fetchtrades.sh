#!/bin/bash

set -x
export TC=${1:-BTC} CC=${2:-USDT}
export TUNIT=${TIME_UNIT:-1m}

#### part1 fetch

#### part2 unzip

export BASE=${3:-~/mpdata/cache/trades/$TUNIT/${TC}/${CC}}
export TARGET=${4:-~/mpdata/import/trades/$TUNIT/${TC}/${CC}}
x="$(mktemp)"
rm "${x}"
mkdir -p "${x}" "${TARGET}" "${BASE}"

pushd "${BASE}"

#wget -rc --no-parent https://data.binance.vision/data/spot/monthly/trades/$TC$CC/${TUNIT}/$TC$CC-${TUNIT}-20{17..21}-{0{1..9},1{0..2}}.zip{,.CHECKSUM}
aria2c -Z -c -{x,j,s}\ 15 -d ${BASE} https://data.binance.vision/data/spot/{monthly/trades/$TC$CC/${TUNIT}/$TC$CC-${TUNIT}-20{17..21}-{0{1..9},1{0..2}},daily/trades/$TC$CC/${TUNIT}/$TC$CC-${TUNIT}-$(date +'%Y-%m')-{0{1..9},{10..31}}}.zip{,.CHECKSUM}
popd
pushd "${x}"
unzip "${BASE}/*.zip"

#### part 3 combine

for i in 1; do
  echo "trade Id", "price", "qty", "quoteQty", "time", "isBuyerMaker", "isBestMatch"
  cat $TC$CC-${TUNIT}*.csv
done >${TARGET}/${TUNIT}.csv || rm ${TARGET}/${TUNIT}.csv

#### cleanup
popd
popd
