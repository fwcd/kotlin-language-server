# Editor Integration

## Visual Studio Code
See [vscode-kotlin-ide](https://github.com/fwcd/vscode-kotlin-ide) or install the extension from the [marketplace](https://marketplace.visualstudio.com/items?itemName=fwcd.kotlin).

## Atom
See [atom-ide-kotlin](https://github.com/fwcd/atom-ide-kotlin).

## Emacs
_using [`lsp-mode`](https://github.com/emacs-lsp/lsp-mode)_

There are two ways of setting up the language server with `lsp-mode`:
- Add the language server executable to your `PATH`. This is useful for development and for always using the latest version from the `main`-branch.
- Let `lsp-mode` download the server for you (`kotlin-ls`). This will use [the latest release](https://github.com/fwcd/kotlin-language-server/releases/latest).


### Run/debug code lenses
If you use [dap-mode](https://github.com/emacs-lsp/dap-mode), you can set `(setq lsp-kotlin-debug-adapter-enabled t)` to enable the debug adapter. You will need to have [Kotlin Debug Adapter](https://github.com/fwcd/kotlin-debug-adapter) on your system. A simple configuration of `dap-mode` for Kotlin may look like:
```emacs-lisp
(require 'dap-kotlin)
(setq lsp-kotlin-debug-adapter-enabled t)
;; replace the path below to the path to your Kotlin Debug Adapter
(setq lsp-kotlin-debug-adapter-path "/path/to/kotlin-debug-adapter")
```

Then you can activate `lsp-kotlin-lens-mode` to see the Run/Debug code lenses at your main-functions.


### Override members (e.g, toString and equals)
The language server provides a custom protocol extension for finding overridable members of a class (variables and methods). `lsp-mode` provides a function that uses this called `lsp-kotlin-implement-member`. You can run it while hovering a class name, and you will get a menu with all available overridable members. (protip: Bind this function to a key!). If you have [Helm](https://github.com/emacs-helm/helm) or [Ivy](https://github.com/abo-abo/swiper) installed, one of them will be utilized. 



## Vim
_using [`LanguageClient-neovim`](https://github.com/autozimu/LanguageClient-neovim)_

Add the language server to your `PATH` and include the following configuration in your `.vimrc`:

```vim
autocmd BufReadPost *.kt setlocal filetype=kotlin

let g:LanguageClient_serverCommands = {
    \ 'kotlin': ["kotlin-language-server"],
    \ }
```

_using [`coc.nvim`](https://github.com/neoclide/coc.nvim)_

Add the following to your coc-settings.json file:

```json
{
    "languageserver": {
        "kotlin": {
            "command": "[path to cloned language server]/server/build/install/server/bin/kotlin-language-server",
            "filetypes": ["kotlin"]
        }
    }
}
```

Note that you may need to substitute `kotlin-language-server` with `kotlin-language-server.bat` on Windows.\
You should also note, that you need a syntax highlighter like [udalov/kotlin-vim](https://github.com/udalov/kotlin-vim) or [sheerun/vim-polyglot](https://github.com/sheerun/vim-polyglot) to work well with coc.

## Neovim

Using Neovim's [nvim-lspconfig](https://github.com/neovim/nvim-lspconfig), register
the language server using the following.

```lua
require'lspconfig'.kotlin_language_server.setup{}
```

If desired, you can also pass in your own defined options to the setup function.

```lua
require'lspconfig'.kotlin_language_server.setup{
    on_attach = on_attach,
    flags = lsp_flags,
    capabilities = capabilities,
}
```

## Other Editors
Install a [Language Server Protocol client](https://microsoft.github.io/language-server-protocol/implementors/tools/) for your tool. Then invoke the language server executable in a client-specific way.

The server can be launched in three modes:

* `Stdio` (the default mode)
    * The language server uses the standard streams for JSON-RPC communication
* `TCP Server`
    * The language server starts a server socket and listens on `--tcpServerPort`
* `TCP Client`
    * The language server tries to connect to `--tcpClientHost` and `--tcpClientPort`

The mode is automatically determined by the arguments provided to the language server.
