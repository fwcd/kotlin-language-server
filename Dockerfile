# Running this container will start a language server that listens for TCP connections on port 49100
# Every connection will be run in a forked child process

FROM --platform=$BUILDPLATFORM eclipse-temurin:11 AS builder

WORKDIR /src/kotlin-language-server

COPY . .
RUN ./gradlew :server:installDist

FROM eclipse-temurin:11

WORKDIR /opt/kotlin-language-server

COPY --from=builder /src/kotlin-language-server/server/build/install/server /opt/kotlin-language-server
RUN ln -s /opt/kotlin-language-server/bin/kotlin-language-server /usr/local/bin/kotlin-language-server

EXPOSE 49100

CMD ["/usr/local/bin/kotlin-language-server", "--tcpServerPort", "49100"]
