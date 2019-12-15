#!/bin/bash
docker login docker.pkg.github.com -u fwcd -p "$GH_TOKEN"
docker build -t docker.pkg.github.com/fwcd/kotlin-language-server/server:latest .
docker push docker.pkg.github.com/fwcd/kotlin-language-server/server:latest
