package dev.enzi.trailhead.ai

/**
 * Claude 가 생성한 단일 파일 변경.
 * @param summary PR 본문에 쓸 한두 문장 설명 (무엇을·왜 바꿨는지)
 * @param newContent 수정된 파일 전체 내용 (그대로 커밋)
 */
data class GeneratedChange(
    val summary: String,
    val newContent: String,
)
