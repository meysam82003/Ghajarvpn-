#!/bin/sh
# Report the ELF LOAD-segment alignment of every native file in jniLibs, so
# 16 KB page-size compatibility is measured, not assumed. A file passes when
# every LOAD segment is aligned to at least 0x4000. Report only (exit 0);
# the result is printed into the CI log.
root=$(cd "$(dirname "$0")/.." && pwd)
printf '%-14s %-28s %-10s %s\n' ABI FILE MIN_ALIGN 16KB
for f in "$root"/app/src/main/jniLibs/*/*.so; do
    [ -f "$f" ] || continue
    abi=$(basename "$(dirname "$f")")
    min=$(readelf -lW "$f" 2>/dev/null | awk '$1=="LOAD" {print $NF}' | while read a; do printf '%d\n' "$a"; done | sort -n | head -1)
    [ -n "$min" ] || min=0
    ok=no; [ "$min" -ge 16384 ] && ok=yes
    printf '%-14s %-28s %-10s %s\n' "$abi" "$(basename "$f")" "$min" "$ok"
done
exit 0
