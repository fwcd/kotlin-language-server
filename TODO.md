## Bugs
- Jump to function name, not start of declaration
- Make sure process quits
- open is always a keyword
- Autocomplete
  - getFoo(), setFoo() as foo
  - String templates
  - import ?
  - something as Outer.Inn?
  - value.InnerClass
  - editFoo?() should overwrite () instead of adding another ()
  - ::?
  - rec|.otherStuff
  - Many random extension functions are showing up
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