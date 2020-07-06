#!/bin/bash

set -o errexit

port="${1:-8080}"
log="${2:-ab.log}"

ab  -n 10000 -c 10 -k -H "X-B3-TraceId: 4883117762eb9420" -H "X-B3-SpanId: 4883117762eb9420" http://127.0.0.1:"${port}"/foo > "${log}"