# 0003 — 플랫폼 EP를 통한 Navigation 리다이렉션

날짜: 2026-05-10
상태: 채택 (ADR 0001 대체)

## 맥락

당초 가정: `.g.dart`가 디스크에 부재하여 합성 PSI 필요 (ADR 0001). 기술 검증 결과:

1. Dart 플러그인 PSI는 resolve에 사용되지 않음 (Dart Analysis Server가 처리)
2. 외부 플러그인이 PSI를 합성해도 resolve/navigation 경로에 실제 영향 없음
3. Dart Analysis Server overlay 주입 공개 API 부재

또한 사용자 본인 사용 시나리오 재확인 결과, 실제 문제는 합성 부재가 아니라:

- `.g.dart`는 디스크에 존재 (resolve는 정상)
- IntelliJ에서 exclude 처리 시 navigation 끊김
- exclude 안 하면 navigation이 생성된 `.g.dart` 코드로 향함 (사용자는 원본 `@riverpod` 선언으로 가고 싶음)

## 결정

플랫폼 레벨 확장 포인트로 navigation/검색을 가로챈다. Dart 플러그인 EP에 의존하지 않는다.

- `com.intellij.gotoDeclarationHandler` — `fooProvider` / `_$Foo` 참조에서 발생하는 Go to Declaration을 원본 `@riverpod` 함수/클래스로 리다이렉트
- `com.intellij.referencesSearch` (`QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>`) — Find Usages 양방향 연결:
  - 원본 `foo()` 함수 검색 → 모든 `ref.watch(fooProvider)`/`ref.read(fooProvider)`/`ref.listen(fooProvider)` 등 포함
  - `fooProvider` 변수 검색 → 동일 결과
- `FileBasedIndexExtension` — `@riverpod` 어노테이션이 붙은 함수/클래스를 인덱싱하여 이름 → 원본 위치 맵 빠르게 조회
- 원본 위치 추론 규칙 (riverpod_generator 3.x):
  - `@riverpod` top-level function `foo` → 생성 심볼 `fooProvider` (camelCase + "Provider")
  - `@riverpod` class `Foo extends _$Foo` → 생성 심볼 `fooProvider` (lowerCamelCase + "Provider"), 슈퍼클래스 `_$Foo` (`_$` + 원본 클래스명)

## 대안

1. **합성 PSI** (ADR 0001) — 거부. 기술적으로 불가.
2. **`.g.dart` stub 자동 생성** — 거부. 디스크 쓰기 부작용. 사용자 워크플로우 침범. 본 결정으로 불필요.
3. **Dart 플러그인 fork/패치** — 거부. 유지보수 비용 폭증.
4. **사용자에게 `.g.dart` 안 exclude 권고만 함** — 거부. 그래도 navigation이 생성 코드로 향하는 문제 미해결.

## 결과

- Dart 플러그인 의존: `<depends>Dart</depends>` 정도면 충분 (Dart PsiElement 타입 캐스팅용). 깊은 EP 의존 없음.
- IntelliJ excluded folder 안의 `.g.dart`가 인덱싱 안 되어도 무방. 우리 인덱스는 원본 `.dart` 파일의 `@riverpod` 어노테이션 기반.
- 단점: `fooProvider` PSI 자체는 여전히 `.g.dart`에 존재(또는 부재). 우리는 그 심볼을 "참조의 타깃"으로 취급하지 않고, **참조의 텍스트 매칭 + 원본 어노테이션 인덱스**로 직접 원본을 찾는다.
- excluded 상태에서 `.g.dart` 자체가 디스크에 존재만 하면 DAS resolve도 정상이라 빨간 줄도 없음. 우리 플러그인 + IntelliJ exclude 조합이 이상적 워크플로우가 됨.
