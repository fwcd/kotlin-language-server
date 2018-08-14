# Building
Contains the commands required to build this project. Note that you might need to use `gradlew` instead of `./gradlew` when running on `cmd.exe`.

## Setting up the development environment
* Java should be installed and located under JAVA_HOME or PATH

### For language server development
* `./gradlew install`

### For extension development
* VSCode is required
* `npm install`
* `npm install -g vsce`

## Building the Language Server
* With Testing:
    * `./gradlew build`
* Without Testing:
    * `./gradlew build -x test`
* Start scripts for the language server are located under `build/install/kotlin-language-server/bin/`

## Testing the Language Server
* `./gradlew test`

## Running/Debugging the VSCode extension
* Open the debug tab in VSCode
* Run the `Extension` launch configuration

## Packaging the VSCode extension
* `vsce package -o build.vsix`
* The extension is located as `build.vsix` in the repository folder
