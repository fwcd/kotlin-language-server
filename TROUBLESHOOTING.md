# Troubleshooting

## Atom: Failed to load `ide-kotlin` package grammar

### A dynamic link library (DLL) initialization routine failed / The module was compiled against a different Node.js version using `NODE_MODULE_VERSION` X, but requires `NODE_MODULE_VERSION` Y

Run `./gradlew :editors:atom:apmRebuild` (or inside `editors/atom`: `apm rebuild`).

See also [this issue](https://github.com/tree-sitter/tree-sitter/issues/377) and [the Electron docs](https://electronjs.org/docs/tutorial/using-native-node-modules) on how to rebuild.

## Language Server: The tests fail with `java.lang.NoSuchMethodError`
* After updating the Kotlin version, there may be multiple copies of the compiler plugin in `lib-kotlin`, for example:

```
lib-kotlin
├───j2k-1.2.72-release-68.jar <- old version
├───j2k-1.3.11-release-272.jar
├───kotlin-plugin-1.2.72-release-68.jar <- old version
└───kotlin-plugin-1.3.11-release-272.jar
```

* The issue is that the compiler finds multiple versions of the same class on the classpath
* To fix this, simply remove the older JARs
* If that still does not work, delete the entire `lib-kotlin` folder
    * Gradle will automatically re-download the necessary files once the project is built again

## VSCode: Running `npm run compile` or `vsce package` fails
If you get the error

```
error TS6059: File '.../KotlinLanguageServer/bin/vscode-extension-src/...' is not under 'rootDir' '.../KotlinLanguageServer/vscode-extension-src'. 'rootDir' is expected to contain all source files.
```

delete the `bin` folder in the repository directory.
