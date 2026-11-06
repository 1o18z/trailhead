package dev.enzi.trailhead.report

import com.fasterxml.jackson.databind.ObjectMapper
import dev.enzi.trailhead.config.TrailheadProperties
import dev.enzi.trailhead.discovery.Candidate
import dev.enzi.trailhead.discovery.CandidateStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class ReportWriter(
    private val props: TrailheadProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** 마크다운 + JSON 리포트를 쓰고, 마크다운 경로를 반환한다. */
    fun write(ranked: List<Candidate>): String? {
        return try {
            val dir = Path.of(props.discovery.reportDir)
            Files.createDirectories(dir)
            val ts = LocalDateTime.now().format(stamp)

            val mdPath = dir.resolve("trailhead-$ts.md")
            Files.writeString(mdPath, toMarkdown(ranked))

            val jsonPath = dir.resolve("trailhead-$ts.json")
            Files.writeString(jsonPath, toJson(ranked))

            log.info("리포트 저장: {} / {}", mdPath, jsonPath)
            mdPath.toAbsolutePath().toString()
        } catch (e: Exception) {
            log.warn("리포트 저장 실패: {}", e.message)
            null
        }
    }

    private fun toMarkdown(ranked: List<Candidate>): String = buildString {
        append("# trailhead 기여 후보 리포트\n\n")
        append("생성: ").append(LocalDateTime.now()).append("  ·  총 ").append(ranked.size).append("건\n\n")

        val actionable = ranked.filter { it.status != CandidateStatus.SKIP }
        append("## 추천 후보 (").append(actionable.size).append("건)\n\n")
        append("| # | 점수 | 상태 | 저장소 | 이슈 | 라벨 | ⭐ |\n")
        append("|---|------|------|--------|------|------|----|\n")
        actionable.forEachIndexed { i, c ->
            append("| ").append(i + 1)
                .append(" | ").append(c.score)
                .append(" | ").append(statusBadge(c.status))
                .append(" | ").append(c.repoFullName)
                .append(" | [#").append(c.issueNumber).append("](").append(c.issueUrl).append(") ")
                .append(escape(c.title.take(60)))
                .append(" | ").append(c.matchedLabel)
                .append(" | ").append(c.stars)
                .append(" |\n")
        }

        append("\n## 상세\n\n")
        actionable.forEach { c ->
            append("### ").append(statusBadge(c.status)).append(" ")
            append("[").append(escape(c.title)).append("](").append(c.issueUrl).append(")\n")
            append("- 저장소: `").append(c.repoFullName).append("` (⭐").append(c.stars).append(")\n")
            append("- 점수: **").append(c.score).append("** · 언어: ").append(c.language)
                .append(" · PR(열림/거절/머지): ")
                .append(c.openPrCount).append("/").append(c.closedUnmergedPrCount).append("/").append(c.mergedPrCount).append("\n")
            append("- 사유:\n")
            c.reasons.forEach { append("  - ").append(it).append("\n") }
            append("\n")
        }

        val skipped = ranked.filter { it.status == CandidateStatus.SKIP }
        if (skipped.isNotEmpty()) {
            append("## 제외됨 (이미 해결/머지, ").append(skipped.size).append("건)\n\n")
            skipped.forEach { append("- ").append(it.repoFullName).append("#").append(it.issueNumber).append(" ").append(escape(it.title.take(70))).append("\n") }
        }
    }

    private fun toJson(ranked: List<Candidate>): String {
        val payload = ranked.map { c ->
            mapOf(
                "score" to c.score,
                "status" to c.status.name,
                "repo" to c.repoFullName,
                "issueNumber" to c.issueNumber,
                "title" to c.title,
                "issueUrl" to c.issueUrl,
                "language" to c.language,
                "matchedLabel" to c.matchedLabel,
                "stars" to c.stars,
                "openPrCount" to c.openPrCount,
                "closedUnmergedPrCount" to c.closedUnmergedPrCount,
                "mergedPrCount" to c.mergedPrCount,
                "reasons" to c.reasons,
            )
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
    }

    private fun statusBadge(s: CandidateStatus): String = when (s) {
        CandidateStatus.AVAILABLE -> "🟢 available"
        CandidateStatus.TAKEN -> "🟡 taken"
        CandidateStatus.RISKY -> "🟠 risky"
        CandidateStatus.SKIP -> "⚪ skip"
    }

    private fun escape(s: String): String = s.replace("|", "\\|").replace("\n", " ")
}
