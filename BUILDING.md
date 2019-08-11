# Building
Describes how to build and run the language server and the editor extensions.

## Setup
* Java 8+ should be installed and located under `JAVA_HOME` or `PATH`.
* Note that you might need to use `gradlew` instead of `./gradlew` for the commands on Windows.

## Language Server

### Building
If you just want to build the language server and use its binaries in your client of choice, run:

>`./gradlew :server:installDist`

The language server executable is now located under `server/build/install/server/bin/kotlin-language-server`. (Depending on your language client, you might want to add it to your `PATH`)

Note that there are external dependent libraries, so if you want to put the server somewhere else, you have to move the entire `install`-directory.

## VSCode extension

### Development/Running
First run `npm run watch` from the `editors/vscode` directory in a background shell. The extension will then incrementally build in the background.

Every time you want to run the extension with the language server:
* Prepare the extension using `./gradlew :editors:vscode:prepare` (this automatically builds and copies the language server's binaries into the extension folder)
* Open the debug tab in VSCode
* Run the `Extension` launch configuration

### Debugging
Your can attach the running language server on `localhost:8000`. Note that this can be done using the `Attach Kotlin Language Server` launch configuration in VSCode (requires the [Java Debug Extension](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-debug)).

### Packaging
Run `./gradlew :editors:vscode:packageExtension` from the repository's top-level-directory. The extension will then be located under the name `kotlin-[version].vsix` in `editors/vscode`.

## Atom plugin

### Development/Running
To build and link the Atom plugin into your local packages folder, run:

>`./gradlew :editors:atom:apmLink`

That's it! To use the extension, just reload your Atom window.

> Note that you might have to manually run `apm rebuild` and `apm link` in `editors/atom` if `apm` could not be found on your `PATH`.

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

### Editors (:editors)

#### VSCode (:editors:vscode)

| Task | Command | Description |
| ---- | ------- | ----------- |
| Prepare | `./gradlew :editors:vscode:prepare` | Copies the packaged language server, the grammar files and other resources to the extension's directory. Can be useful to run separately if the extension's code is already built using `npm run watch`. |
| Compile | `./gradlew :editors:vscode:compile` | Compiles the TypeScript code of the extension. |
| Test | `./gradlew :editors:vscode:test` | Runs the unit tests of the extension. Currently not supported from within VSCode. |
| Package | `./gradlew :editors:vscode:packageExtension` | Creates a `.vsix` package of the extension for use within VSCode. |

#### Atom (:editors:atom)

| Task | Command | Description |
| ---- | ------- | ----------- |
| Prepare | `./gradlew :editors:atom:prepare` | Copies the packaged language server into the extension's directory. |
| Install | `./gradlew :editors:atom:install` | Installs the npm dependencies of the extension. |
| APM Rebuild | `./gradlew :editors:atom:apmRebuild` | Rebuilds the extension's native modules using Atom's Node version. |
| APM Link | `./gradlew :editors:atom:apmLink` | Links the extension into your local Atom package directory. |
