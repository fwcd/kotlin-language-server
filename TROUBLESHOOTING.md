# Troubleshooting

## java.lang.OutOfMemoryError when running language server

The language server is currently a memory hog, mostly due to its use of an in-memory database for symbols (ALL symbols from dependencies etc.!). This makes it not work well for machines with little RAM. If you experience out of memory issues, and still have lots of RAM, the default heap space might be too low. You might want to try tweaking the maximum heap space setting by setting `-Xmx8g` (which sets the heap size to 8GB. Change the number to your needs). This can be done by setting the `JAVA_OPTS` environment variable.

In [the VSCode extension](https://github.com/fwcd/vscode-kotlin), this is in the extension settings in the setting `Kotlin > Java: Opts`.

If you use Emacs, you can try the `setenv` function to set environment variables. Example: `(setenv "JAVA_OPTS" "-Xmx8g")`.
