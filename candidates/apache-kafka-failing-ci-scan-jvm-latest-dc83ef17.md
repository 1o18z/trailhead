# apache/kafka — 실패 중인 CI 체크 'scan_jvm (latest)' 복구

| 항목 | 값 |
|------|----|
| 대상 저장소 | [apache/kafka](https://github.com/apache/kafka) |
| 별 | 34194 |
| 언어 | Java |
| 유형 | BUG_FIX |
| 등급 | HIGH |
| 신호 | FAILING_CI |
| 근거 | <https://github.com/apache/kafka/actions/runs/42443091564/job/128616277269> |

## 설명

기본 브랜치(커밋 ef6a6c1)에서 체크 'scan_jvm (latest)' 이 failure 상태. 회귀(regression)나 깨진 테스트일 가능성이 높아 버그 수정 기여 기회.
