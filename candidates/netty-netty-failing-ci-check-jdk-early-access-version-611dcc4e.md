# netty/netty — 실패 중인 CI 체크 'check-jdk-early-access-version' 복구

| 항목 | 값 |
|------|----|
| 대상 저장소 | [netty/netty](https://github.com/netty/netty) |
| 별 | 35070 |
| 언어 | Java |
| 유형 | BUG_FIX |
| 등급 | HIGH |
| 신호 | FAILING_CI |
| 근거 | <https://github.com/netty/netty/actions/runs/42187211732/job/127790293930> |

## 설명

기본 브랜치(커밋 e0d7510)에서 체크 'check-jdk-early-access-version' 이 failure 상태. 회귀(regression)나 깨진 테스트일 가능성이 높아 버그 수정 기여 기회.
