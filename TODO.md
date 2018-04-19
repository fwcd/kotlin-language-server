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
  - something as Outer.Inn?
  - X value.InnerClass
- keywords
  - as
  - is

## Features
- Format-on-save
- Fix-imports-on-save, default to 'on'
  - See https://godoc.org/golang.org/x/tools/cmd/goimports
- Support files that don't exist on disk
- Javadoc comments
- Rename
- .kts support with eval