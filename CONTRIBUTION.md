
# Project workflow

## Conventional commits

We love and therefore use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).

Conventional commits requires next commit types:
- `feat`
- `fix`


Additionally we introduce the next ones:
- `refactor` - for changes, which heavily modifies the code with the sole goal to simplify the code. The change MUST NOT introduce any new features, but MAY introduce fixes occasionally.
- `chore` - for small changes, such as variable/file renaming, changes to comments, import organisation, formatting

## Feature based workflow

We use feature based workflow, which implies branch per feature/bug/issue

## New to Kotlin?

Check out these links:
- [Kotlin Function features](https://kotlinlang.org/docs/functions.html)
- [infix notation](https://kotlinlang.org/docs/functions.html#infix-notation)
- [Kotlin lambdas](https://kotlinlang.org/docs/lambdas.html)
- [Scope functions](https://kotlinlang.org/docs/scope-functions.html)

While working with kotlin, you are likely to encounter concepts from the above list

## Tracking issues

If you want to introduce massive change, please create a corresponding tracking issue and pull-request.

## Project-wise requirements

> These requirements apply to the project as a whole.

KLSP:
- *MUST NOT* create any system files in user's project folder, and user's home folder.
- *SHOULD NOT* rely on editor specific features
