# Riverpod Graph Dogfooding Checklist

Date: 2026-05-18

Use this checklist during `./gradlew runIde` against a real Flutter/Riverpod project before release. Automated fixture coverage is noted only where it exists; it does not replace manual IDE dogfooding.

## Manual RunIde Checks

| Area | Manual status | Automated guard |
| --- | --- | --- |
| Tool Window 표시 | Not run | `RiverpodGraphServiceFixtureTest` verifies active/inactive provider snapshots. |
| Providers 탭 갱신 | Not run | `RiverpodGraphServiceFixtureTest` verifies PSI-change cache invalidation and newly added providers. |
| Dependency graph 표시 | Not run | `RiverpodProviderDependencyFixtureTest` verifies provider-to-provider graph edges, including family `.future` and `.select`. |
| Widget dependencies 표시 | Not run | Existing widget analyzer tests cover static analysis; runIde dialog path still needs dogfooding. |
| Cmd/Ctrl-click navigation | Not run | `RiverpodNavigationFixtureTest` uses `GotoDeclarationAction.findAllTargetElements`. |
| Find Usages | Not run | `RiverpodFindUsagesFixtureTest` uses `ReferencesSearch.search`. |
| Gutter marker | Not run | `RiverpodGutterFixtureTest` uses `myFixture.doHighlighting()` and `findAllGutters()`. |
| Generated `.g.dart` present | Not run | Navigation/find-usages fixture keeps generated files present. |
| Generated `.g.dart` removed | Not run | `RiverpodNavigationFixtureTest` deletes generated `.g.dart` and verifies source navigation. |
| Generated `.g.dart` excluded | Not run | No automated excluded-folder test yet; verify manually by excluding generated files/folder in Project Structure. |
| Nested package / monorepo pubspec | Not run | `RiverpodActivationFixtureTest` verifies nearest nested `pubspec.yaml` activation and plain package exclusion. |

## Suggested Manual Scenario

1. Open a real Flutter project that uses `riverpod_annotation` and generated `*.g.dart` files.
2. Confirm the Riverpod Graph tool window appears for the app package.
3. Confirm Providers refresh after adding, editing, and deleting an `@riverpod` declaration.
4. Open a provider with `ref.watch(otherProvider)`, `familyProvider(arg).future`, and `familyProvider(arg).select(...)`; confirm dependency graph rows classify them correctly.
5. From a `ConsumerWidget`, Cmd/Ctrl-click generated provider usages and confirm navigation lands on the source `@riverpod` function/class.
6. Run Find Usages from both the source declaration and generated provider symbol; confirm results match and point to handwritten Dart files.
7. Confirm gutter markers appear only beside actual `@riverpod` declarations.
8. Exclude generated `.g.dart` files/folder in the IDE and repeat navigation and Find Usages.
9. Open a monorepo where the root `pubspec.yaml` is not a Riverpod package but `packages/app/pubspec.yaml` is; confirm the app package remains active and sibling plain packages stay inactive.
