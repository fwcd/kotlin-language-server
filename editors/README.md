# Editor Integration

## Visual Studio Code
See [BUILDING](../BUILDING.md#vscode-extension) or install the extension from the [marketplace](https://marketplace.visualstudio.com/items?itemName=fwcd.kotlin).

## Atom
See [BUILDING](../BUILDING.md#atom-plugin).

## Emacs
_using [`lsp-mode`](https://github.com/emacs-lsp/lsp-mode)_

Add the language server executable to your `PATH`.

## Vim
_using [`LanguageClient-neovim`](https://github.com/autozimu/LanguageClient-neovim)_

Add the language server to your `PATH` and include the following configuration in your `.vimrc`:

```vim
let g:LanguageClient_serverCommands = {
    \ 'kotlin': ["kotlin-language-server"],
    \ }
```

## Other Editors
Invoke the language server executable in a client-specific way. The server uses `stdio` to send and receive `JSON-RPC` messages.
