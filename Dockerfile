# Running this container will start a language server that listens for TCP connections on port 49100
# Every connection will be run in a forked child process

FROM openjdk:11 AS builder

WORKDIR /kotlin-language-server
COPY . .
RUN ./gradlew :server:installDist

FROM openjdk:11

WORKDIR /
COPY --from=builder /kotlin-language-server/server/build/install/server /server

EXPOSE 49100

CMD ["/server/bin/kotlin-language-server", "--tcpServerPort", "49100"]
