package dev.enzi.trailhead.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import dev.enzi.trailhead.config.TrailheadProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 승인된 제안을 실제 코드 변경으로 만든다 (Phase B).
 * 대상 파일 내용 + 신호(무엇을·왜) 를 Claude 에 주고 **최소·자기완결적** 수정을 받는다.
 * 모델이 확신하지 못하면 변경을 만들지 않는다(null) → 쓰레기 PR 방지.
 *
 * 출력 규약(파싱 견고성):
 *   확신 시   -> `<<<SUMMARY>>>\n한두문장\n<<<FILE>>>\n파일전체`
 *   불확신 시 -> `NO_CHANGE` 한 줄
 */
@Component
class ChangeGenerator(private val props: TrailheadProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    // 키 없거나 비활성화면 null. SDK 가 ANTHROPIC_API_KEY 를 환경에서 읽는다.
    private val client: AnthropicClient? by lazy {
        if (!props.ai.enabled) return@lazy null
        runCatching { AnthropicOkHttpClient.fromEnv() }
            .onFailure { log.warn("Anthropic 클라이언트 초기화 실패 (ANTHROPIC_API_KEY 확인): {}", it.message) }
            .getOrNull()
    }

    fun enabled(): Boolean = props.ai.enabled

    /**
     * @param instruction 신호에서 온 "무엇을·왜" (예: "FIXME 해소: null 체크 누락 수정")
     * @return 생성된 변경, 또는 null(비활성화/과대파일/모델 불확신/오류)
     */
    fun generate(repoFullName: String, path: String, fileContent: String, instruction: String): GeneratedChange? {
        val c = client ?: return null

        val bytes = fileContent.toByteArray().size
        if (bytes > props.ai.maxFileBytes) {
            log.info("파일 과대로 AI 생성 생략: {}:{} ({}B > {}B)", repoFullName, path, bytes, props.ai.maxFileBytes)
            return null
        }

        val params = MessageCreateParams.builder()
            .model(props.ai.model)
            .maxTokens(props.ai.maxTokens)
            .outputConfig(OutputConfig.builder().effort(effort()).build())
            .system(SYSTEM_PROMPT)
            .addUserMessage(userPrompt(repoFullName, path, instruction, fileContent))
            .build()

        val raw = runCatching {
            c.messages().create(params)
                .content()
                .mapNotNull { it.text().orElse(null)?.text() }
                .joinToString("")
                .trim()
        }.onFailure { log.warn("AI 변경 생성 실패 [{}:{}]: {}", repoFullName, path, it.message) }
            .getOrNull() ?: return null

        return parse(raw, repoFullName, path)
    }

    private fun parse(raw: String, repoFullName: String, path: String): GeneratedChange? {
        if (raw.isBlank() || raw.trimEnd() == NO_CHANGE || raw.startsWith(NO_CHANGE)) {
            log.info("AI 가 확신 못해 변경 없음: {}:{}", repoFullName, path)
            return null
        }
        val fileIdx = raw.indexOf(FILE_MARKER)
        if (fileIdx < 0) {
            log.warn("AI 응답 형식 불일치(FILE 마커 없음): {}:{}", repoFullName, path)
            return null
        }
        val summary = raw.substring(0, fileIdx)
            .removePrefix(SUMMARY_MARKER)
            .trim()
            .ifBlank { "Automated fix for: $path" }
        val content = raw.substring(fileIdx + FILE_MARKER.length).trimStart('\n')
        if (content.isBlank()) {
            log.warn("AI 가 빈 파일을 생성: {}:{}", repoFullName, path)
            return null
        }
        return GeneratedChange(summary = summary, newContent = content)
    }

    private fun effort(): OutputConfig.Effort = when (props.ai.effort.uppercase()) {
        "LOW" -> OutputConfig.Effort.LOW
        "MEDIUM" -> OutputConfig.Effort.MEDIUM
        "XHIGH" -> OutputConfig.Effort.XHIGH
        "MAX" -> OutputConfig.Effort.MAX
        else -> OutputConfig.Effort.HIGH
    }

    private fun userPrompt(repo: String, path: String, instruction: String, fileContent: String): String = """
        Repository: $repo
        File: $path

        Task: $instruction

        Below is the current, complete content of the file. Produce a minimal, correct,
        self-contained fix for the task. Change only what the task requires; do not reformat
        or touch unrelated code. If you cannot be highly confident the change is correct from
        this single file alone, reply with exactly NO_CHANGE.

        --- FILE START ---
        $fileContent
        --- FILE END ---
    """.trimIndent()

    companion object {
        private const val SUMMARY_MARKER = "<<<SUMMARY>>>"
        private const val FILE_MARKER = "<<<FILE>>>"
        private const val NO_CHANGE = "NO_CHANGE"

        private val SYSTEM_PROMPT = """
            You are a careful open-source contributor. You make the smallest correct change that
            resolves the given task, touching a single file, and never reformat or alter unrelated code.

            Respond in exactly one of two forms and nothing else:
            1. If you are highly confident of a correct, self-contained fix:
               $SUMMARY_MARKER
               <one or two sentences describing what you changed and why>
               $FILE_MARKER
               <the COMPLETE modified file content>
            2. If you are not highly confident, or the fix needs context beyond this file,
               or there is nothing to fix: reply with exactly:
               $NO_CHANGE

            Never wrap the file in markdown code fences. Never add commentary outside these forms.
        """.trimIndent()
    }
}
