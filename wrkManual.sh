#!/bin/bash

set -o errexit

port="${1:-8765}"
log="${2:-wrk.log}"

wrk -t2 -c5 -d5s --timeout 2s -s script.lua  http://127.0.0.1:"${port}"/fooManual > "${log}"