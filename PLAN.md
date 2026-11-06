# trailhead — PLAN

> 기여할 만한 오픈소스를 자동 탐색하고, 열린/닫힌 PR을 대조해 중복·거절을 걸러낸 뒤,
> 기여 후보를 리스트업해 보여주는 도구.

## 결정 사항 (고정)

| 항목 | 결정 |
|------|------|
| 스택 | Kotlin + Spring Boot 3.4 (Gradle, Java 21) |
| 형태 | 웹 대시보드 + 매 실행 콘솔 로그/리포트 파일 |
| 탐색 범위 | 내 주력 언어 — Kotlin / Java (설정으로 조정 가능) |
| 필터 | good first issue / help wanted 류 초심자 친화 이슈 |
| 중복·거절 회피 | 이슈를 참조하는 열린/닫힌 PR 대조 |
| 인증 | 로컬 `gh` 토큰 재사용 (`GITHUB_TOKEN` 환경변수) |
| 저장 | H2 파일 DB + JSON/마크다운 리포트 파일 |

## 핵심 파이프라인

1. **탐색(Discovery)**: 설정된 언어 × 라벨로 GitHub Search API 호출
   → 열린 이슈 수집 (제목/번호/URL/라벨/작성·수정일/댓글수).
2. **저장소 건강도(Repo health)**: 이슈가 속한 repo의 stars, 최근 push,
   open issue 수 조회(캐시).
3. **중복·거절 대조(Dedup)**: 각 이슈의 timeline에서 cross-referenced PR을 찾아
   - 열린 PR 존재 → 누군가 이미 작업 중(taken, 위험)
   - 닫혔지만 머지 안 된 PR 존재 → 과거 접근이 거절됨(caution)
   - 머지된 PR 존재 → 이미 해결 가능성(skip)
4. **점수화(Scoring)**: 위 신호를 합쳐 0~100 contributability 점수 + 사유.
5. **리스트업/알림**: 점수순 정렬 → DB upsert, 콘솔 출력, `reports/`에
   마크다운+JSON 리포트 저장, 웹 대시보드에 표시.

## MVP 범위 (1차)

- [x] 프로젝트 스캐폴딩 (Gradle, Spring Boot, Kotlin)
- [ ] GitHubClient (RestClient, 토큰 인증, search/repo/timeline)
- [ ] DiscoveryService (검색 → 건강도 → dedup → 점수 → 저장)
- [ ] 리포트 파일 writer (markdown + json) + 콘솔 출력
- [ ] 웹 대시보드 (후보 목록, 점수/배지/사유, 수동 탐색 버튼)

## 추후(2차 이후)

- 스케줄러(cron)로 주기 탐색 + 새 후보 알림(이메일/Slack)
- 설정 UI로 언어/라벨/필터 조정
- 내 기여 이력 추적(이미 PR 낸 이슈 제외)
- rate limit 백오프 / 캐시 고도화
