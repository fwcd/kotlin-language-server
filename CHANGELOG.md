# Change Log
All notable changes to the language server will be documented in this file.

Check [Keep a Changelog](http://keepachangelog.com/) for recommendations on how to structure this file.

## [1.3.1]
- Add support for run/debug code lenses
- Add definition lookup support for JDT symbols
- Add quick fix for implementing abstract functions
- Add experimental JDT.LS integration

## [1.3.0]
- Bump to Kotlin 1.6
- Support JDK 17
- Add incremental indexing
- Improve logged Gradle errors

## [1.2.0]
- Support for renaming
- Improved semantic highlighting for enum members
- Improved region code folding

## [1.1.2]
- Support for semantic highlighting
- Improved source jar lookup support
- Improved Kotlin DSL support

## [1.1.1]
- Exclude too long symbol names from index for now

## [1.1.0]
- Provide ServerInfo via LSP's initialize
- Make Gradle class path resolver slightly more robust

## [1.0.0]
- Symbol indexing
- Code completion for unimported members
- Improved progress bars

## [0.9.0]
- More detailed initialization progress
- Improved completion icons

## [0.8.3]
- Lazier file tree walking while looking for resolvers

## [0.8.2]
- Minor tweaks

## [0.8.1]
- Minor fixes

## [0.8.0]
- Tagged deprecation/unused variable warnings
- Kotlin 1.4.20 support
- New formatter (ktfmt)
- Java 11+ is now required

## [0.7.1]

## [0.7.0]
- Improve completion list sorting
- Fix bug that occurred when project path contained whitespace

## [0.6.0]
- Add package completion
- Support Java imports
- Provide tree-structured document symbols
- Improve lint scheduling
- Fix formatOnSave and some other bugs

## [0.5.2]
- Fix Docker image tag

## [0.5.1]
- Fix Docker deployment and codeblock grammar

## [0.5.0]
- Add Docker support
- Add support for TCP transport

## [0.4.0]
- Add support for Kotlin DSL buildscripts
- Add support for non-Maven/Gradle projects
- Improve Java-to-Kotlin converter
- Use the Field icon instead of Property icon in completion lists
- Add experimental support for multiplatform projects
- Introduce the 'kls' URI scheme

## [0.3.0]
- Improve trailing lambda completions
- Provide completions for generic extension methods

## [0.2.9]
- Include a grammar ZIP in the release assets

## [0.2.8]
- Include grammars distribution in release

## [0.2.7]
- Improve release naming

## [0.2.6]
- Remove version postfix from ZIP distributions

## [0.2.5]
- Move editor extensions into separate repositories

## [0.2.0]
- Rewrite Java-to-Kotlin converter from scratch
- Implement Kotlin formatter
- Add keyword completions
- Make completions more reliable

## [0.1.13]
- Kotlin 1.3.11 support
- Detailed completion signatures for overloaded methods
- Updated to VSCode ^1.30.2
- Improved dependency resolution logs

## [0.1.12]
- Kotlin 1.2.70 support

## [0.1.11]
- Improved keyword syntax highlighting

## [0.1.10]
- More compact distribution
- Security fixes
- Fixed decompiler

## [0.1.6]
- Bugfixes related to Gradle dependency resolution in combination with Android projects

## [0.1.5]
- Bugfixes related to Kotlin stdlib resolution through Gradle

## [0.1.4]
- Async language server operations

## [0.1.3]
- Java to Kotlin converter

## [0.1.2]
- First stable build

## [0.1.1]
- Migrated to the Gradle build tool

## [0.1.0]
- Initial release
