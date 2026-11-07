# keycloak/keycloak — 과대 파일 분할 리팩터링: services/src/main/java/org/keycloak/protocol/saml/SamlService.java

| 항목 | 값 |
|------|----|
| 대상 저장소 | [keycloak/keycloak](https://github.com/keycloak/keycloak) |
| 별 | 37743 |
| 언어 | Java |
| 유형 | REFACTOR |
| 등급 | MEDIUM |
| 신호 | LARGE_FILE |
| 근거 | <https://github.com/keycloak/keycloak/blob/main/services/src/main/java/org/keycloak/protocol/saml/SamlService.java> |

## 설명

`services/src/main/java/org/keycloak/protocol/saml/SamlService.java` 가 약 83KB 로 매우 큼. 단일 책임/가독성 관점에서 모듈·클래스 분할이나 함수 추출 리팩터링 기여 기회.
