# Riverpod Graph

Riverpod Graph is a JetBrains IDE plugin for Flutter projects that use `riverpod_generator` 3.x.

## v1 Features

- Redirects Go to Declaration for generated `fooProvider` and `_$Foo` symbols back to the source `@riverpod` function or class.
- Integrates Find Usages for source declarations and generated provider symbols.
- Shows a Riverpod Graph tool window with Providers and Dependencies tabs.
- Shows static widget dependency trees from widget context actions.
- Adds gutter markers beside `@riverpod` declarations.

## Scope

Riverpod-aware features are intended for modules whose `pubspec.yaml` contains `riverpod_annotation`.

Legacy manual providers such as `Provider((ref) => ...)`, `StateNotifierProvider`, and `ChangeNotifierProvider` are outside v1 scope because the Dart plugin already handles them.

Widget dependency analysis is static. Conditional, loop, callback, Ref extension, and cycle cases are marked so the result stays honest about analysis limits.

## License

Apache License 2.0.
