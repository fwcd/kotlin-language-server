# Running this container will start a language server that listens for TCP connections on port 2088
# Every connection will be run in a forked child process

FROM alpine:3.10 AS builder

WORKDIR /kotlin-language-server
COPY . .
RUN ./gradlew :server:installDist

FROM openjdk:10

WORKDIR /
COPY --from=builder /kotlin-language-server/server/build/install/server .

EXPOSE 2088

CMD ["/server/bin/kotlin-language-server", "--tcpPort=2088"]
