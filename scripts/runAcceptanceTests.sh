#!/bin/bash

set -o errexit

BENCHMARK_TOOL="${BENCHMARK_TOOL:-ab}"

echo -e "SLEUTH DISABLED \n\n"
./mvnw clean install -Dapp.args="--spring.sleuth.enabled=false" -Dscript.to.run="${BENCHMARK_TOOL}NoSleuth.sh" -Dlog.name="sleuthDisabled.log"

echo -e "\n\n SLEUTH ON EACH \n\n"
./mvnw clean install -Dapp.args="--spring.sleuth.reactor.instrumentation-type=DECORATE_ON_EACH" -Dscript.to.run="${BENCHMARK_TOOL}.sh" -Dlog.name="sleuthOnEach.log"

echo -e "\n\n SLEUTH ON LAST \n\n"
./mvnw clean install -Dapp.args="--spring.sleuth.reactor.instrumentation-type=DECORATE_ON_LAST" -Dscript.to.run="${BENCHMARK_TOOL}.sh" -Dlog.name="sleuthOnLast.log"

echo -e "\n\n SLEUTH ON MANUAL \n\n"
./mvnw clean install -Dapp.args="--spring.sleuth.reactor.instrumentation-type=MANUAL" -Dscript.to.run="${BENCHMARK_TOOL}Manual.sh" -Dlog.name="sleuthOnManual.log"

