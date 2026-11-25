#!/bin/sh
# Run in a Redis container with the verification Lua files in the same directory.
set -eu
cd "$(dirname "$0")"
prefix="cloud:verify:test:{$(cat /proc/sys/kernel/random/uuid)}"
code="$prefix:code"
cool="$prefix:cooldown"
rate="$prefix:rate"
trap 'redis-cli DEL "$code" "$cool" "$rate" >/dev/null' EXIT

expect() {
    expected="$1"
    shift
    actual="$("$@")"
    if [ "$actual" != "$expected" ]; then
        echo "FAIL: expected $expected, got $actual" >&2
        exit 1
    fi
}
issue() { redis-cli --raw --eval verification-issue.lua "$code" "$cool" , digest "$1" 300 60; }
reserve() { redis-cli --raw --eval verification-reserve.lua "$code" , "$1" "$2" 5; }
finish() { redis-cli --raw --eval verification-finish.lua "$code" , "$1" "$2"; }
cancel() { redis-cli --raw --eval verification-cancel.lua "$code" , "$1"; }

expect 1 issue issue-1
expect -1 issue issue-2
for i in 1 2 3 4; do expect -3 reserve wrong owner; done
expect -4 reserve wrong owner
expect 0 redis-cli --raw EXISTS "$code"
redis-cli DEL "$cool" >/dev/null
expect 1 issue issue-1
expect 1 reserve digest owner
expect -2 reserve digest other
expect 0 cancel issue-1
redis-cli DEL "$cool" >/dev/null
expect -2 issue replacement
expect 0 finish other COMMIT
expect 1 finish owner ROLLBACK
expect READY redis-cli --raw HGET "$code" state
ttl="$(redis-cli --raw TTL "$code")"
test "$ttl" -gt 0 && test "$ttl" -le 300
out="$(for i in 1 2 3 4 5 6 7 8; do reserve digest "owner-$i" & done
wait)"
successes="$(printf '%s\n' "$out" | awk '$0 == 1 {n++} END {print n+0}')"
test "$successes" = 1
owner="$(redis-cli --raw HGET "$code" owner)"
expect 1 finish "$owner" COMMIT
expect -1 reserve digest owner
expect 1 issue new-issue
expect 0 cancel issue-1
expect 1 cancel new-issue
redis-cli DEL "$cool" >/dev/null
expect 1 issue issue-1
redis-cli EXPIRE "$code" 0 >/dev/null
expect -1 reserve digest owner

# Concurrent attempts execute the actual rate-limit script from independent clients.
out="$(for i in 1 2 3 4 5 6 7 8; do
    redis-cli --raw --eval verification-rate-limit.lua "$rate" , 3 3600 &
done
wait)"
successes="$(printf '%s\n' "$out" | awk '$0 == 1 {n++} END {print n+0}')"
test "$successes" = 3
expect 0 redis-cli --raw --eval verification-rate-limit.lua "$rate" , 3 3600
expect 3 redis-cli --raw GET "$rate"
ttl="$(redis-cli --raw TTL "$rate")"
test "$ttl" -gt 0 && test "$ttl" -le 3600
echo 'PASS: cooldown, failure limit, reservation, rollback, commit, stale owner, cancellation, expiry, rate limit'
