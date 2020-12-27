# Building
Describes how to build and run the language server and the editor extensions.

## Setup
* Java 11+ should be installed and located under `JAVA_HOME` or `PATH`.
* Note that you might need to use `gradlew` instead of `./gradlew` for the commands on Windows.

## Language Server

### Building
If you just want to build the language server and use its binaries in your client of choice, run:

>`./gradlew :server:installDist`

The language server executable is now located under `server/build/install/server/bin/kotlin-language-server`. (Depending on your language client, you might want to add it to your `PATH`)

Note that there are external dependent libraries, so if you want to put the server somewhere else, you have to move the entire `install`-directory.

### Packaging
To create a ZIP-archive of the language server, run:

>`./gradlew :server:distZip`

## Grammars

### Packaging
To create a ZIP-archive of the grammars, run:

>`./gradlew :grammars:distZip`

## Gradle Tasks
This paragraph assumes that you are familiar with Gradle's [task system](https://docs.gradle.org/current/userguide/build_lifecycle.html). In short: Every task describes an atomic piece of work and may depend on other tasks. Task dependencies will automatically be executed. The following subsections describe the available tasks for each module of this project.

### Language Server (:server)

| Task | Command | Description |
| ---- | ------- | ----------- |
| Package | `./gradlew :server:installDist` | Packages the language server as a bundle of JAR files (e.g. for use with an editor extension) |
| Package for Debugging | `./gradlew :server:installDebugDist` | Packages the language server with a debug launch configuration |
| Test | `./gradlew :server:test` | Executes all unit tests |
| Run | `./gradlew :server:run` | Runs the standalone language server from the command line |
| Debug | `./gradlew :server:debugRun` | Launches the standalone language server from the command line using a debug configuration |
| Build | `./gradlew :server:build` | Builds, tests and packages the language server |
| Package for Release | `./gradlew :server:distZip` | Creates a release zip in `server/build/distributions`. If any dependencies have changed since the last release, a new license report should be generated and placed in `src/main/dist` before creating the distribution. |
| Generate License Report | `./gradlew :server:licenseReport` | Generates a license report from the dependencies in `server/build/reports/licenses` |

### Grammars (:grammars)

| Task | Command | Description |
| ---- | ------- | ----------- |
| Package for Release | `./gradlew :grammars:distZip` | Creates a zip of the grammars in `grammars/build/distributions` |
