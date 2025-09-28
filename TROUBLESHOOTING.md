# Troubleshooting

## java.lang.OutOfMemoryError when running language server

The language server is currently a memory hog, mostly due to its use of an in-memory database for symbols (ALL symbols from dependencies etc.!). This makes it not work well for machines with little RAM. If you experience out of memory issues, and still have lots of RAM, the default heap space might be too low. You might want to try tweaking the maximum heap space setting by setting `-Xmx8g` (which sets the heap size to 8GB. Change the number to your needs). This can be done by setting the `JAVA_OPTS` environment variable.

In [the VSCode extension](https://github.com/fwcd/vscode-kotlin), this is in the extension settings in the setting `Kotlin > Java: Opts`.

If you use Emacs, you can try the `setenv` function to set environment variables. Example: `(setenv "JAVA_OPTS" "-Xmx8g")`.

## Neovim / Mason LSP

In case you see the following in your `lsp.log`;

```
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"java.util.concurrent.CompletionException: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was BEGIN_ARRAY at path $"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat java.base/java.util.concurrent.CompletableFuture.encodeThrowable(CompletableFuture.java:315)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat java.base/java.util.concurrent.CompletableFuture.completeThrowable(CompletableFuture.java:320)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(CompletableFuture.java:1770)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat java.base/java.lang.Thread.run(Thread.java:1583)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"Caused by: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was BEGIN_ARRAY at path $"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat com.google.gson.internal.bind.ReflectiveTypeAdapterFactory$Adapter.read(ReflectiveTypeAdapterFactory.java:397)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat com.google.gson.Gson.fromJson(Gson.java:1227)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat com.google.gson.Gson.fromJson(Gson.java:1329)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat com.google.gson.Gson.fromJson(Gson.java:1271)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat org.javacs.kt.ConfigurationKt.getStoragePath(Configuration.kt:81)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat org.javacs.kt.KotlinLanguageServer.initialize$lambda$6(KotlinLanguageServer.kt:97)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat org.javacs.kt.util.AsyncExecutor.compute$lambda$2(AsyncExecutor.kt:19)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(CompletableFuture.java:1768)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\t... 3 more"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"Caused by: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was BEGIN_ARRAY at path $"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat com.google.gson.internal.bind.JsonTreeReader.expect(JsonTreeReader.java:165)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat com.google.gson.internal.bind.JsonTreeReader.beginObject(JsonTreeReader.java:89)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\tat com.google.gson.internal.bind.ReflectiveTypeAdapterFactory$Adapter.read(ReflectiveTypeAdapterFactory.java:386)"
[ERROR][2025-09-28 09:46:29] ...lsp/handlers.lua:562	"\t... 10 more"
```

The solution is to provide the following LSP config where
`init_options.storagePath` is defined.

```lua
require("mason-lspconfig").setup_handlers {
  ["kotlin_language_server"] = function ()
    require("lspconfig")["kotlin_language_server"].setup {
      init_options = {
        storagePath = vim.fn.stdpath("data") .. "/kotlin-language-server",
      }
      -- etc...
    }
}
```
