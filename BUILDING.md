# Building
Describes how to build and run this project. Note that you might need to use `gradlew` instead of `./gradlew` on Windows.

## Setup
* Java 8+ should be installed and located under JAVA_HOME or PATH

### ...for language server development
* `./gradlew build`

### ...for extension development
* [VSCode](https://code.visualstudio.com) is required
* `npm install`
* `npm install -g vsce`

## Language Server

| Task | Command | Description |
| ---- | ------- | ----------- |
| Packaging | `./gradlew installDist` | Packages the language server as a bundle of JAR files (e.g. for use with the VSCode extension) |
| Debug Packaging | `./gradlew installDebugDist` | Packages the language server with a debug launch configuration |
| Testing | `./gradlew test` | Executes all unit tests |
| Running | `./gradlew run` | Runs the standalone language server from the command line |
| Debugging | `./gradlew debug` | Launches the standalone language server from the command line using a debug configuration |
| Building | `./gradlew build` | Builds, tests and packages the language server |

### Launching the packaged language server
* Start scripts for the language server are located under `build/install/kotlin-language-server/bin/`

### Debugging
* Attach the running language server on `localhost:8000`
    * Note that this can be done using the `Attach Kotlin Language Server` launch configuration in VSCode (requires the [Java Debug Extension](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-debug))

## VSCode Extension

### Running/Debugging
* Package the language server using `./gradlew installDist` (or `./gradlew installDebugDist` for debugging)
* Open the debug tab in VSCode
* Run the `Extension` launch configuration

### Packaging
* `vsce package -o build.vsix`
* The extension is located as `build.vsix` in the repository folder
