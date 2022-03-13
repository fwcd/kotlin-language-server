# VSCode Extension for Java + Kotlin

A VSCode extension that enchances [vscode-java](https://github.com/redhat-developer/vscode-java) and [vscode-kotlin](https://github.com/fwcd/vscode-kotlin) with java + kotlin interoperability. This uses a JDT LS extension with a custom project importer to allow Java code to have access to Kotlin code.

**Disclaimer**: This is very experimental, but it seems to work for small maven and gradle projects at least.

## Setup (for now)

For now, to set this up, you need to run the `build.sh` script in this directory to package the JDT LS extension. Afterwards, you should run `npm install` to install the extension dependencies.

To debug, you can use F5 on VSCode, as with any other extension. To package the extension you can use `vsce package`.
