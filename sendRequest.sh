#!/bin/bash

set -o errexit

http :8765/fooManual "X-B3-TraceId:4883117762eb9420" "X-B3-SpanId:4883117762eb9420"