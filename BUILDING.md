# Building
Describes how to build and run this project. Note that you might need to use `gradlew` instead of `./gradlew` on Windows.

## Setup
* Java 8 should be installed and located under JAVA_HOME or PATH. Java 11 is *not* supported yet, see [#108](https://github.com/fwcd/KotlinLanguageServer/issues/108) for details.

### ...for language server development
* `./gradlew server:build`

### ...for extension development
* [VSCode](https://code.visualstudio.com) is required
* `npm install`
* `npm install -g vsce`

## Language Server

| Task | Command | Description |
| ---- | ------- | ----------- |
| Packaging | `./gradlew server:installDist` | Packages the language server as a bundle of JAR files (e.g. for use with the VSCode extension) |
| Debug Packaging | `./gradlew server:installDebugDist` | Packages the language server with a debug launch configuration |
| Testing | `./gradlew server:test` | Executes all unit tests |
| Running | `./gradlew server:run` | Runs the standalone language server from the command line |
| Debugging | `./gradlew server:debugRun` | Launches the standalone language server from the command line using a debug configuration |
| Building | `./gradlew server:build` | Builds, tests and packages the language server |
| Releasing | `./gradlew server:distZip` | Creates a release zip in `server/build/distributions`. If any dependencies have changed since the last release, a new license report should be generated and placed in `src/main/dist` before creating the distribution. |
| Generating a license report | `./gradlew server:licenseReport` | Generates a license report from the dependencies in `server/build/reports/licenses` |

### Launching the packaged language server
* Start scripts for the language server are located under `server/build/install/server/bin/`

### Debugging
* Attach the running language server on `localhost:8000`
    * Note that this can be done using the `Attach Kotlin Language Server` launch configuration in VSCode (requires the [Java Debug Extension](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-debug))

## VSCode Extension

### Running/Debugging
* Package the language server using `./gradlew server:installDist` (or `./gradlew server:installDebugDist` for debugging)
* Open the debug tab in VSCode
* Run the `Extension` launch configuration

### Packaging
* `vsce package -o build.vsix`
* The extension is located as `build.vsix` in the repository folder
