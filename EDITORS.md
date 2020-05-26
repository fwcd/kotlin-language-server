# Editor Integration

## Visual Studio Code
See [vscode-kotlin-ide](https://github.com/fwcd/vscode-kotlin-ide) or install the extension from the [marketplace](https://marketplace.visualstudio.com/items?itemName=fwcd.kotlin).

## Atom
See [atom-ide-kotlin](https://github.com/fwcd/atom-ide-kotlin).

## Emacs
_using [`lsp-mode`](https://github.com/emacs-lsp/lsp-mode)_

Add the language server executable to your `PATH`.

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
