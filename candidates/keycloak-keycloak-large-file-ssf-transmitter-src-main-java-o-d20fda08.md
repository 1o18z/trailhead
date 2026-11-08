# keycloak/keycloak — 과대 파일 분할 리팩터링: ssf/transmitter/src/main/java/org/keycloak/ssf/transmitter/stream/StreamService.java

| 항목 | 값 |
|------|----|
| 대상 저장소 | [keycloak/keycloak](https://github.com/keycloak/keycloak) |
| 별 | 37750 |
| 언어 | Java |
| 유형 | REFACTOR |
| 등급 | MEDIUM |
| 신호 | LARGE_FILE |
| 근거 | <https://github.com/keycloak/keycloak/blob/main/ssf/transmitter/src/main/java/org/keycloak/ssf/transmitter/stream/StreamService.java> |

## 설명

`ssf/transmitter/src/main/java/org/keycloak/ssf/transmitter/stream/StreamService.java` 가 약 72KB 로 매우 큼. 단일 책임/가독성 관점에서 모듈·클래스 분할이나 함수 추출 리팩터링 기여 기회.
