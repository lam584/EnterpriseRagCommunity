#!/usr/bin/env bash
set -euo pipefail

OUT_FILE=${1:-/tmp/sut_monitor.csv}
DURATION=${2:-120}
INTERVAL=1

read -r _ u1 n1 s1 i1 w1 irq1 sirq1 st1 _ < /proc/stat
prev_total=$((u1+n1+s1+i1+w1+irq1+sirq1+st1))
prev_idle=$((i1+w1))

: > "$OUT_FILE"
for ((sec=0; sec<DURATION; sec+=INTERVAL)); do
  sleep "$INTERVAL"
  read -r _ u2 n2 s2 i2 w2 irq2 sirq2 st2 _ < /proc/stat
  total=$((u2+n2+s2+i2+w2+irq2+sirq2+st2))
  idle=$((i2+w2))
  dt=$((total-prev_total))
  di=$((idle-prev_idle))

  if (( dt > 0 )); then
    cpu=$(awk -v dt="$dt" -v di="$di" 'BEGIN { printf "%.2f", (1-(di/dt))*100 }')
  else
    cpu="0.00"
  fi

  mem_total_kb=$(awk '/MemTotal:/ { print $2 }' /proc/meminfo)
  mem_avail_kb=$(awk '/MemAvailable:/ { print $2 }' /proc/meminfo)
  mem_avail_pct=$(awk -v a="$mem_avail_kb" -v t="$mem_total_kb" 'BEGIN { if (t>0) printf "%.2f", (a/t)*100; else print "0.00" }')

  echo "${cpu},${mem_avail_pct}" >> "$OUT_FILE"
  prev_total=$total
  prev_idle=$idle
done
