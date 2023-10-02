# Running this image will start a language server that listens for TCP connections on port 49100
# Every connection will be run in a forked child process

ARG JDKVERSION=17

FROM --platform=$BUILDPLATFORM eclipse-temurin:${JDKVERSION} AS builder

ARG JDKVERSION

WORKDIR /src/kotlin-language-server

COPY . .
RUN ./gradlew :server:installDist -PjavaVersion=${JDKVERSION}

FROM eclipse-temurin:${JDKVERSION}

WORKDIR /opt/kotlin-language-server

COPY --from=builder /src/kotlin-language-server/server/build/install/server /opt/kotlin-language-server
RUN ln -s /opt/kotlin-language-server/bin/kotlin-language-server /usr/local/bin/kotlin-language-server

EXPOSE 49100

CMD ["/usr/local/bin/kotlin-language-server", "--tcpServerPort", "49100"]
