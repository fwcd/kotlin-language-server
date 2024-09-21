# Kotlin Language Server

[![Release](https://img.shields.io/github/release/fwcd/kotlin-language-server)](https://github.com/fwcd/kotlin-language-server/releases)
[![Build](https://github.com/fwcd/kotlin-language-server/actions/workflows/build.yml/badge.svg)](https://github.com/fwcd/kotlin-language-server/actions/workflows/build.yml)
[![Downloads](https://img.shields.io/github/downloads/fwcd/kotlin-language-server/total)](https://github.com/fwcd/kotlin-language-server/releases)
[![Chat](https://img.shields.io/badge/chat-on%20discord-7289da)](https://discord.gg/cNtppzN)

## Notice and status

This is fork of currently almost unmaintained upstream repo: https://github.com/fwcd/kotling-language-server

*This fork* is being actively maintained. Our goal is to make this extension usable and feature complete. We *do not* aim for full feature parity with JetBrains ide's Kotlin support.

> We do support only Linux and MacOS. Windows is not tested but still may work. Windows only        issues has least priority.

## Kotlin Language Server

A [language server](https://microsoft.github.io/language-server-protocol/) that provides smart code completion, diagnostics, hover, document symbols, definition lookup, method signature help and more for [Kotlin](https://kotlinlang.org).

![Icon](Icon128.png)

Any editor conforming to LSP is supported, including [VSCode](https://github.com/fwcd/vscode-kotlin) and [Atom](https://github.com/fwcd/atom-ide-kotlin).

### Scope of the project

This project is strictly limited by Language Server Protocol capabilities. We aim to provide complete language server experience.

Some features, that our users may expect, lies outside of our responsibility. We won't deliver any features related to code and test running, project building. These tasks should be performed by other means. We offload these features to editor specific extensions.

## Getting Started

* See [CONTRIBUTION.md](CONTRIBUTION.md) for development guidelines
* See [Taiga board](https://tree.taiga.io/project/owl-from-hogvarts-kotlin-language-server/timeline) for ongoing and planed work. Core team uses it as main tool for cooperation and project's status tracking
* See [BUILDING.md](BUILDING.md) for build instructions
* See [Editor Integration](EDITORS.md) for editor-specific instructions
* See [Troubleshooting](TROUBLESHOOTING.md) for tips on troubleshooting errors
* See [Kotlin Quick Start](https://github.com/fwcd/kotlin-quick-start) for a sample project
* See [Kotlin Debug Adapter](https://github.com/fwcd/kotlin-debug-adapter) for editor-agnostic launch and debug support of Kotlin/JVM programs
* See [tree-sitter-kotlin](https://github.com/fwcd/tree-sitter-kotlin) for an experimental [Tree-Sitter](https://tree-sitter.github.io/tree-sitter/) grammar

## Packaging

[![Packaging status](https://repology.org/badge/vertical-allrepos/kotlin-language-server.svg)](https://repology.org/project/kotlin-language-server/versions)

## This repository needs your help!

> See [CONTRIBUTION.md](./CONTRIBUTION.md)

[The original author](https://github.com/georgewfraser) created this project while he was considering using Kotlin in his work. He ended up deciding not to and is not really using Kotlin these days though this is a pretty fully-functional language server that just needs someone to use it every day for a while and iron out the last few pesky bugs.

There are two hard parts of implementing a language server:
- Figuring out the dependencies
- Incrementally re-compiling as the user types

The project uses the internal APIs of the [Kotlin compiler](https://github.com/JetBrains/kotlin/tree/master/compiler).

### Figuring out the dependencies

Dependencies are determined by the [DefaultClassPathResolver.kt](shared/src/main/kotlin/org/javacs/kt/classpath/DefaultClassPathResolver.kt), which invokes Maven or Gradle to get a list of classpath JARs. Alternatively, projects can also 'manually' provide a list of dependencies through a shell script, located either at `[project root]/kls-classpath` or `[config root]/kotlin-language-server/classpath`, which outputs a list of JARs. Depending on your platform, the scripts also can be suffixed with `.{sh,bat,cmd}`.

* Example of the `~/.config/kotlin-language-server/classpath` on Linux:
```bash
#!/bin/bash
echo /my/path/kotlin-compiler-1.4.10/lib/kotlin-stdlib.jar:/my/path/my-lib.jar
```

* Example of the `%HOMEPATH%\.config\kotlin-language-server\classpath.bat` on Windows:
```cmd
@echo off
echo C:\my\path\kotlin-compiler-1.4.10\lib\kotlin-stdlib.jar;C:\my\path\my-lib.jar
```

### Incrementally re-compiling as the user types

I get incremental compilation at the file-level by keeping the same `KotlinCoreEnvironment` alive between compilations in [Compiler.kt](server/src/main/kotlin/org/javacs/kt/compiler/Compiler.kt). There is a performance benchmark in [OneFilePerformance.kt](server/src/test/kotlin/org/javacs/kt/OneFilePerformance.kt) that verifies this works.

Getting incremental compilation at the expression level is a bit more complicated:
- Fully compile a file and store in [CompiledFile](server/src/main/kotlin/org/javacs/kt/CompiledFile.kt):
    - `val content: String` A snapshot of the source code
    - `val parse: KtFile` The parsed AST
    - `val compile: BindingContext` Additional information about the AST from typechecking
- After the user edits the file:
    - Find the smallest section the encompasses all the user changes
    - Get the `LexicalScope` encompassing this region from the `BindingContext` that was generated by the full-compile
    - Create a fake, in-memory .kt file with just the expression we want to re-compile
        - [Add space](https://github.com/fwcd/kotlin-language-server/blob/427cfa7a688d6d2ff202625ebad1ea605e3b8c37/server/src/main/kotlin/org/javacs/kt/CompiledFile.kt#L125) at the top of the file so the line numbers match up
    - Re-compile this tiny fake file

The incremental expression compilation logic is all in [CompiledFile.kt](server/src/main/kotlin/org/javacs/kt/CompiledFile.kt). The Kotlin AST has a built-in repair API, which seems to be how IntelliJ works, but as far as I can tell this API does not work if the surrounding IntelliJ machinery is not present. Hence I created the "fake tiny file" incremental-compilation mechanism, which seems to be quite fast and predictable.

There is an extensive suite of behavioral [tests](server/src/test/kotlin/org/javacs/kt), which are all implemented in terms of the language server protocol, so you should be able to refactor the code any way you like and the tests should still work.

## Modules

| Name | Description |
| ---- | ----------- |
| server | The language server executable |
| shared | Classpath resolution and utilities |

## Scripts

| Name | Command | Description |
| ---- | ------- | ----------- |
| release_version.py | `python3 scripts/release_version.py` | Creates a tag for the current version and bumps the development version |

## Protocol Extensions

The Kotlin language server supports some non-standard requests through LSP. See [KotlinProtocolExtensions](server/src/main/kotlin/org/javacs/kt/KotlinProtocolExtensions.kt) for a description of the interface. The general syntax for these methods is `kotlin/someCustomMethod`.

## Initialization Options

The Kotlin language server supports some custom initialization options via the `initializationOptions` property in the `initialize` request parameters. See `InitializationOptions` in [Configuration](server/src/main/kotlin/org/javacs/kt/Configuration.kt) for a list of supported properties.

## Features

### Autocomplete
![Autocomplete](images/Autocomplete.png)

### Signature help
![Signature Help](images/SignatureHelp.png)

### Hover
![Hover](images/Hover.png)

### Go-to-definition, find all references
![Find all references](images/FindAllReferences.png)

### Document symbols
![Document symbols](images/DocumentSymbols.png)

### Global symbols
![Global symbols](images/GlobalSymbols.png)


## Authors

### Current maintainers team
* [owl-from-hogvarts](https://github.com/owl-from-hogvarts)
* [localPiper](https://github.com/localPiper)
* [Zerumi](https://github.com/zerumi)

### Original authors
* [georgewfraser](https://github.com/georgewfraser)
* [fwcd](https://github.com/fwcd)
