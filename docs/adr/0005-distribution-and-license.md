# 0005 — JetBrains Marketplace 배포 + Apache 2.0 오픈소스

날짜: 2026-05-10
상태: 채택

## 맥락

배포 채널과 라이선스는 사용자 채택률·기여 가능성·장기 유지보수 모델을 결정한다.

대상 사용자 풀: Riverpod_generator를 쓰는 Flutter 개발자 — Flutter 사용자의 부분집합 → JetBrains IDE 사용자의 부분집합. 시장은 niche.

## 결정

- 배포: **JetBrains Marketplace 무료 공개**.
- 라이선스: **Apache 2.0**.
- Vendor: `ki960213` (GitHub 핸들).
- 소스: GitHub 공개 저장소.

## 대안

1. **유료 (Marketplace 구독/일시불)** — 거부. niche 시장에 유료화는 채택률 학살. Riverpod 사용자 수 ≪ Flutter 사용자 수 ≪ JetBrains 사용자 수.
2. **GitHub Releases 자체 호스팅** — 거부. 발견성 ↓, 자동 업데이트 ↓. JetBrains 호환성 검증도 받지 못함.
3. **MIT** — 거부 (Apache 2.0 채택). MIT는 짧지만 patent grant 미포함. Apache 2.0이 기업 환경에서 더 안전하고 오픈소스 기여 표준에 가깝다.
4. **클로즈드 소스 + 무료 배포** — 거부. Dart/Flutter 생태계 거의 모든 도구가 OSS. 클로즈드는 신뢰 ↓ 기여 ↓.

## 결과

- `LICENSE` 파일 (Apache 2.0 전체 텍스트) 추가 필요.
- `plugin.xml`의 `<vendor>` `ki960213`으로 갱신.
- `README.md`에 라이선스/기여 가이드 표기.
- `build.gradle.kts`/`gradle.properties`에 마켓플레이스 publishing 설정 (publishPlugin 태스크).
- 가치 회수: 평판/포트폴리오/생태계 기여. 매출 기대 없음.
- 향후 유료 전환은 권장하지 않음 — 한 번 OSS 무료로 풀면 fork 가능.
