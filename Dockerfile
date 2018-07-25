# Running this container will start a language server that listens for TCP connections on port 2088
# Every connection will be run in a forked child process

# Please note that before building the image, you have to build the language server with `./gradlew build`

FROM openjdk:10

COPY ./build/install ./

EXPOSE 2088

CMD ["./kotlin-language-server/bin/kotlin-language-server", "--tcp-server=0:2088"]
