#!/bin/bash
./gradlew :shared:publish :server:publish -x test -Pgpr.user=fwcd -Pgpr.key="$GH_TOKEN"
