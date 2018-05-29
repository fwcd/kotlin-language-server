# Building
Contains the commands require to build this project. Note that you might need to use `gradlew` instead of `./gradlew` when running on `cmd.exe`.

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
* The JAR archive is located under build/libs/KotlinLanguageServer.jar

## Testing the Language Server
* `./gradlew test`

## Running/Debugging the VSCode extension
* Open the debug tab in VSCode
* Run the `Extension (kotlin-language-server)` launch configuration

## Packaging the VSCode extension
* `vsce package`
* The extension is located as a .vsix file in the repository folder