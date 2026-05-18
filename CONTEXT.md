# Context

도메인 용어집과 시스템 핵심 결정. 도메인 전문가 관점에서 의미 있는 용어만 기록.

## Glossary

### Provider

Riverpod의 의존성 주입/상태 관리 단위. 본 플러그인 범위에서는 **riverpod_generator (>= 3.0)**가 만들어내는 것만 다룬다. Riverpod 3.x는 ref 타입이 `Ref<X>` 단일로 통합되어 합성 PSI가 단순함:

- **함수형 Provider** — `@riverpod` 어노테이션이 붙은 top-level 함수. 예: `@riverpod String foo(Ref ref) => ...` → 생성된 `fooProvider` 심볼.
- **클래스형 Provider (Notifier)** — `@riverpod` 어노테이션이 붙은 `_$Foo`를 상속한 클래스. 예: `class Foo extends _$Foo { ... }` → 생성된 `fooProvider` 심볼.

생성된 `*.g.dart` 파일에 `fooProvider` 변수와 `_$Foo` 슈퍼클래스가 정의된다.

Family는 본 플러그인 입장에서 별도 종류가 아님 (riverpod_generator 3.x에서 함수에 추가 인자를 넣으면 자동으로 family 생성, 심볼 이름 동일).

### Notifier

위 클래스형 Provider의 사용자 정의 클래스 부분. 비즈니스 로직 보유. `state` 필드와 `build()` 메서드, mutator 메서드들.

### 레거시 Provider (범위 외)

`Provider((ref) => ...)`, `StateNotifierProvider`, `ChangeNotifierProvider` 등 수동 작성. `.g.dart` 미생성. **본 플러그인 범위 외** — Dart 플러그인이 이미 정상 처리.

### `.g.dart` 제외

riverpod_generator가 만든 파일이 **디스크엔 존재**하지만 IntelliJ에서 excluded folder/file로 마킹되어 IntelliJ 인덱스/네비게이션이 무시하는 상태. Dart Analysis Server는 별도 프로세스라 이 마킹 무관하게 파일을 읽어 resolve 자체는 동작.

본질 문제는 "resolve가 깨진다"가 아니라 "**navigation 타깃이 원본 `@riverpod` 선언이 아니라 생성된 `.g.dart`로 향한다**"이다:
- exclude 안 함 → `fooProvider` 클릭 → `.g.dart`의 생성된 `final fooProvider = ...`로 점프 (생성 코드는 노이즈)
- exclude 함 → 점프 자체가 안 됨 (IntelliJ가 excluded 안으로 진입 거부)

플러그인의 임무는 어느 쪽이든 **원본 `@riverpod` 함수/클래스로 리다이렉트**.

### Navigation 리다이렉션

`fooProvider` 또는 `_$Foo`에 대한 Go to Declaration / Find Usages 요청이 생성된 `.g.dart` 위치 대신 원본 `@riverpod` 선언 위치(`foo()` 함수 또는 `class Foo`)로 향하도록 플러그인이 가로채는 메커니즘.

정책:
- **항상 리다이렉트** — `.g.dart` 존재 여부, exclude 여부 무관하게 우리 핸들러가 우선. `.g.dart` 자체로 가는 경로는 제공하지 않음 (사용자가 보고 싶으면 파일 직접 열기).
- **디스크 부재 케이스도 지원** — 어노테이션 인덱스 기반이라 `.g.dart`가 0개여도 navigation 동작. 단 DAS의 미해결 빨간 줄은 우리 영역 외 (별도 메커니즘 필요. 본 플러그인 범위 외).
- **Find Usages 통합** — 원본 위 / `.g.dart` 위 어디서 호출하든 동일 결과 (`ref.watch/read/listen/invalidate/refresh`, `.notifier`, `.future`, `.select`, override, `foo()` 직접 호출, Ref 확장 메서드 간접 호출 모두 포함).

### Provider 의존성

Provider가 `ref.watch` / `ref.read` / `ref.listen`으로 읽는 다른 Provider 집합. Notifier 메서드 안에서의 ref 호출 포함.

### 위젯 의존성 트리

특정 위젯과 그 자식 위젯들이 직간접적으로 의존하는 Provider 집합 전체.

- **직접 의존성** — 그 위젯 build/State 안에서 `ref.watch/read/listen` 또는 inline `Consumer(builder:)`로 직접 접근.
- **자식 traversal** — 같은 패키지 내 한정. 깊이 5 기본 (토글 무한). 외부 패키지 위젯은 자기 자체 분석 없이 인자로 들어간 위젯만 따라감.
- **간접 의존성** (Provider→Provider 펼침 토글) — Notifier 메서드 또는 Ref 확장 메서드 본문에 들어 있는 ref 호출까지. cycle 마커 (`↻`) 표시 후 중단.

### Riverpod-aware 위젯

`ref` 직접 접근 가능한 위젯 타입:

1. `ConsumerWidget`
2. `ConsumerStatefulWidget` + 짝 `ConsumerState` 클래스
3. inline `Consumer(builder: (ctx, ref, child) => ...)`
4. `HookConsumerWidget` (hooks_riverpod)
5. `StatefulHookConsumerWidget`
6. plain `StatelessWidget` / `StatefulWidget` — 직접 ref 없음. 자식에 위 타입 포함 가능 → traversal 진입점으로 유효.

### Ref 확장 메서드/getter

```dart
extension WatchUserX on WidgetRef {
  User get currentUser => watch(userProvider).requireValue;
}
```

플러그인은 이 확장의 본문을 인덱싱하여 `WidgetRefX.currentUser` → `[userProvider]` 매핑 보존. 위젯 dep view에선 토글 펼침으로 노출. Find Usages는 indirect 섹션에 별도 표시.

### 자식 위젯 휴리스틱

build 메서드 안에서 Widget(또는 하위 타입) 반환 모든 생성자/함수 호출을 자식 후보로. 정밀도보다 회수율 우선. 상황별 마커:

- 🔀 조건부 (if / `?:`)
- 🔁 반복 (map / forEach / for)
- 🎯 콜백 (Builder, ListView.builder builder, FutureBuilder builder 등)
- ⚙ extension (Ref 확장 메서드 호출)
- ↻ cycle (의존 그래프 cycle 발견)

### 활성화

`pubspec.yaml`에 `riverpod_annotation` 의존이 선언된 모듈에서만 동작. 모듈 단위로 평가. 활성 모듈 0개면 Tool Window 자동 숨김.

### 활성 Riverpod 범위

활성화된 모듈에 속한 생성되지 않은 Dart 소스 파일과 그 안의 Provider 선언/사용만 Riverpod Graph의 관찰 대상이다.

Navigation 리다이렉션, Find Usages, Provider Tool Window, Provider Dependency Graph, Widget Dependency Tree는 모두 이 범위 안의 Provider만 보여준다. 비활성 모듈의 `fooProvider` 같은 동일 이름 심볼은 Riverpod Graph 관점에서 존재하지 않는 것처럼 취급한다.

## v1 출시 범위 요약

### 포함

1. **Navigation 리다이렉션** — `fooProvider`/`_$Foo` Go to Declaration 항상 원본으로. Find Usages 통합 (직접 + 간접 via 확장).
2. **Provider Tool Window — Providers 탭** — Project View 폴더 동기화, 재귀, 파일별 트리. 행: 아이콘 + 이름 + family 시그니처 + 반환 타입 + keepAlive 마커 + 경로:라인.
3. **Widget Dependency Tree** — 진입: 클래스 선언/본문 우클릭 + 위젯 인스턴스 호출 우클릭 + gutter 클래스 선언 옆. 위젯 타입 1~6 전부. Provider→Provider 펼치기 토글, Ref 확장 펼치기 토글.
4. **Provider Dependency Graph — Dependencies 탭** — 우클릭 provider → "Show Dependency Graph". cycle 마커 + 중단.
5. **gutter icon** — `@riverpod` 선언 옆.

### v2 보류

- ProviderScope override detection
- 사용 횟수 (provider 행에)
- cross-package 위젯 traversal
- flat 보기 모드
- 빨간 줄 처리 (Dart Analysis Server overlay 영역)
- Settings 패널

### 자잘한 정책

- Settings 패널 — 없음 (opinionated). 필요해지면 v2.
- Private provider (`_foo()` → `_fooProvider`) — public/private 차별 없음. Tool Window에서 회색 처리해 시각 구분만.
- 단축키 — 별도 디폴트 없음. Find Action으로 호출. 사용자가 IDE 키맵에서 지정.
