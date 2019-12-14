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

Note that you may need to substitute `kotlin-language-server` with `kotlin-language-server.bat` on Windows.

## Other Editors
Install a [Language Server Protocol client](https://microsoft.github.io/language-server-protocol/implementors/tools/) for your tool. Then invoke the language server executable in a client-specific way.

The server uses `stdio` by default to send and receive `JSON-RPC` messages, but can be launched with the argument `--tcpPort=port` for TCP support.
