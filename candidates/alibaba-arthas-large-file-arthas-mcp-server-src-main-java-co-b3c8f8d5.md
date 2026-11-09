# alibaba/arthas — 과대 파일 분할 리팩터링: arthas-mcp-server/src/main/java/com/taobao/arthas/mcp/server/protocol/spec/McpSchema.java

| 항목 | 값 |
|------|----|
| 대상 저장소 | [alibaba/arthas](https://github.com/alibaba/arthas) |
| 별 | 37612 |
| 언어 | Java |
| 유형 | REFACTOR |
| 등급 | MEDIUM |
| 신호 | LARGE_FILE |
| 근거 | <https://github.com/alibaba/arthas/blob/master/arthas-mcp-server/src/main/java/com/taobao/arthas/mcp/server/protocol/spec/McpSchema.java> |

## 설명

`arthas-mcp-server/src/main/java/com/taobao/arthas/mcp/server/protocol/spec/McpSchema.java` 가 약 83KB 로 매우 큼. 단일 책임/가독성 관점에서 모듈·클래스 분할이나 함수 추출 리팩터링 기여 기회.
