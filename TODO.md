## Bugs
- Jump to function name, not start of declaration
- Make sure process quits
- open is always a keyword
- Autocomplete
  - Static members
  - getFoo(), setFoo() as foo
  - String templates
  - something.? when there is a next line
  - import ?
  - `named function` with quotes

## Features
- Format-on-save
- Format should fix imports
  - See https://godoc.org/golang.org/x/tools/cmd/goimports
- Support files that don't exist on disk
- Javadoc comments
- Rename
- .kts support with eval