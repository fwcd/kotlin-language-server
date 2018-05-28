# Building
Contains the commands require to build this project. Note that you might need to use `gradlew` instead of `./gradlew` when running on `cmd.exe`.

## Setting up the development environment
* `npm install`
* `./gradlew install`
* VSCode is required for extension development

## Building the Language Server
* `./gradlew build`
* The JAR archive is located under build/libs/KotlinLanguageServer.jar

## Testing the Language Server
* `./gradlew test`

## Running/Debugging the VSCode extension
* Open the debug tab in VSCode
* Run the `Extension (kotlin-language-server)` launch configuration