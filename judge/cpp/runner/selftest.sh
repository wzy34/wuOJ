#!/bin/sh
# Checks oj-runner inside the judge image:
#   docker run --rm wuoj/judge-cpp:dev /usr/local/share/oj-runner/selftest.sh
set -eu

fail() {
    echo "FAIL: $1" >&2
    exit 1
}

expect() {
    name=$1
    pattern=$2
    shift 2
    result=$(/usr/local/bin/oj-runner run "$@")
    echo "$name: $result"
    echo "$result" | grep -Eq "$pattern" || fail "$name: expected $pattern"
}

expect "true" '"exitCode":0,"signal":0,.*"wallTimedOut":false,"oomKilled":false,"error":null' -- /bin/true
expect "false" '"exitCode":1,"signal":0' -- /bin/false
expect "sleep past the wall limit" '"signal":9,.*"wallTimedOut":true' --wall-ms 1000 -- /bin/sleep 5
expect "missing program" '"exitCode":127,.*"error":"/no/such/program: No such file or directory"' -- /no/such/program
expect "output limit" '"signal":25' --fsize-bytes 10 --stdout /tmp/out -- /usr/bin/yes
expect "drops privileges" '"exitCode":0' --uid 1000 --gid 1000 --stdout /tmp/id -- /usr/bin/id -u
[ "$(cat /tmp/id)" = "1000" ] || fail "program should run as uid 1000, ran as $(cat /tmp/id)"
echo "all runner checks passed"
