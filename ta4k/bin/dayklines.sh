#!/bin/bash

: ${MP_CACHE:=~/mpdata/cache}	# stores idempotent exchange data zipfiles in a hierarchy
: ${MP_DATA:=~/mpdata}	# ussually ~/mpdata
: ${MP_IMPORT:=~/mpdata/import}	# holds isam and csv hierarchically ussually ~/mpdata/import

new_chunks=( )

while true
do
set -x
TC=${1:-BTC} CC=${2:-USDT}
finalcsv=$MP_IMPORT/klines/1m/${TC}/${CC}/final-${TC}-${CC}-1m.csv
#find the newest dailly kline close
since=$(tail -n 1 ${finalcsv} | cut -f7 -d,)

#https://github.com/binance/binance-spot-api-docs/blob/master/rest-api.md#klinecandlestick-data

#fetch
#symbol	    STRING	YES
#interval	  ENUM	YES
#startTime	LONG	NO
#endTime	  LONG	NO
#limit	    INT	NO	Default 500; max 1000.

segment=$(mktemp)
curl "https://api.binance.com/api/v3/klines?symbol=${TC}${CC}&interval=1m&startTime=$((since))&limit=1000" |
  sed -e "s/\[\[//g" -e  "s/\]\]/\\n/g"  -e  "s/\],\[/\\n/g" |tr -d '"'  >${segment}

linespulled=$(wc -l < ${segment})
cat ${segment} >>${finalcsv}
new_chunks+=( ${segment})
#rm ${segment}
(( linespulled < 999 )) && {
  echo --------segments--------
  echo ${new_chunks[*]}
  exit 0
}
done
