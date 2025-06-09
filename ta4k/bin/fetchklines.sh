#!/bin/bash

#vars
: ${MP_CACHE:=~/mpdata/cache}	# stores idempotent exchange data zipfiles in a hierarchy
: ${MP_DATA:=~/mpdata}	# ussually ~/mpdata
: ${MP_IMPORT:=~/mpdata/import}	# holds isam and csv hierarchically ussually ~/mpdata/import

set -x
mkdir -p $MP_CACHE $MP_IMPORT
export TC=${1:-BTC}
export CC=${2:-USDT}
export TUNIT=${TIME_UNIT:-1m}

#### part1 fetch

#### part2 unzip

export DT=$(date -u +'%Y-%m')
export CLEAN=$(date -u  -d "-1 month" +%Y-%m)
export KLINECACHE=${3:-$MP_CACHE/klines/$TUNIT/${TC}/${CC}}
export TARGET=${4:-$MP_IMPORT/klines/$TUNIT/${TC}/${CC}}
x="$(mktemp)"
rm "${x}"
mkdir -p "${x}" "${TARGET}" "${KLINECACHE}"

pushd "${KLINECACHE}"
mv $TC$CC-${TUNIT}-$CLEAN-{0{1..9},{10..31}}.zip{,.CHECKSUM}  /tmp
#wget -rc --no-parent https://data.binance.vision/data/spot/monthly/trades/$TC$CC/${TUNIT}/$TC$CC-${TUNIT}-20{17..21}-{0{1..9},1{0..2}}.zip{,.CHECKSUM}
aria2c   -Z -c -{x,j,s}\ 15 -d ${KLINECACHE} https://data.binance.vision/data/spot/{monthly/klines/$TC$CC/${TUNIT}/$TC$CC-${TUNIT}-20{17..22}-{0{1..9},1{0..2}},daily/klines/$TC$CC/${TUNIT}/$TC$CC-${TUNIT}-$DT-{0{1..9},{10..31}}}.zip{,.CHECKSUM}
#https://data.binance.vision/data/spot/monthly/klines/BTCUSDC/1m/BTCUSDC-1m-2021-05.zip.CHECKSUM
pushd "${x}"
unzip -aa -n "${KLINECACHE}/$TC$CC-${TUNIT}-*.zip"

#### part 3 combine

for i in 1; do
  echo 'Open_time,Open,High,Low,Close,Volume,Close_time,Quote_asset_volume,Number_of_trades,Taker_buy_base_asset_volume,Taker_buy_quote_asset_volume,Ignore'
   sort -fu  $TC$CC-${TUNIT}*.csv |grep  --extended-regexp -e '(.*,){11}' |sed  --posix --regexp-extended 's/(\.[0-9]+])0+,/\1,/g'
done >${TARGET}/final-$TC-$CC-${TUNIT}.csv || rm ${TARGET}/final-$TC-$CC-${TUNIT}.csv

#### cleanup
rm -fr "${x}"
popd
popd
