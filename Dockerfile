# Running this container will start a language server that listens for TCP connections on port 2088
# Every connection will be run in a forked child process

# Please note that before building the image, you have to build the language server with `./gradlew :server:installDist`

FROM openjdk:10

WORKDIR /
COPY server/build/install/server .

EXPOSE 2088

CMD ["/server/bin/kotlin-language-server", "--tcpPort=2088"]
