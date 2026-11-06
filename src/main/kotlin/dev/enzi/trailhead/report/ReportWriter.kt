package dev.enzi.trailhead.report

import com.fasterxml.jackson.databind.ObjectMapper
import dev.enzi.trailhead.config.TrailheadProperties
import dev.enzi.trailhead.proposal.Proposal
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

    /** 마크다운 + JSON 리포트를 쓰고 마크다운 경로를 반환한다. */
    fun write(proposals: List<Proposal>): String? {
        return try {
            val dir = Path.of(props.discovery.reportDir)
            Files.createDirectories(dir)
            val ts = LocalDateTime.now().format(stamp)

            val md = dir.resolve("trailhead-$ts.md")
            Files.writeString(md, toMarkdown(proposals))

            val json = dir.resolve("trailhead-$ts.json")
            Files.writeString(json, toJson(proposals))

            log.info("리포트 저장: {} / {}", md, json)
            md.toAbsolutePath().toString()
        } catch (e: Exception) {
            log.warn("리포트 저장 실패: {}", e.message)
            null
        }
    }

    private fun toMarkdown(proposals: List<Proposal>): String = buildString {
        append("# trailhead 기여거리 제안\n\n")
        append("생성: ").append(LocalDateTime.now()).append("  ·  총 ").append(proposals.size).append("건\n\n")
        append("| 등급 | 유형 | 저장소 | 제안 | 근거 |\n")
        append("|------|------|--------|------|------|\n")
        proposals.forEach { p ->
            append("| ").append(p.tier)
                .append(" | ").append(p.type)
                .append(" | [").append(p.repoFullName).append("](").append(p.repoUrl).append(") ⭐").append(p.stars)
                .append(" | ").append(escape(p.title))
                .append(" | [link](").append(p.evidenceUrl).append(")")
                .append(" |\n")
        }
        append("\n## 상세\n\n")
        proposals.forEach { p ->
            append("### [").append(p.tier).append("] ").append(escape(p.title)).append("\n")
            append("- 저장소: `").append(p.repoFullName).append("` (⭐").append(p.stars).append(", ").append(p.language).append(")\n")
            append("- 유형: ").append(p.type).append(" · 신호: ").append(p.signalType).append("\n")
            append("- 근거: ").append(p.evidenceUrl).append("\n")
            append("- 설명: ").append(p.detail).append("\n\n")
        }
    }

    private fun toJson(proposals: List<Proposal>): String {
        val payload = proposals.map { p ->
            mapOf(
                "tier" to p.tier.name,
                "type" to p.type.name,
                "signal" to p.signalType.name,
                "repo" to p.repoFullName,
                "repoUrl" to p.repoUrl,
                "stars" to p.stars,
                "language" to p.language,
                "title" to p.title,
                "detail" to p.detail,
                "evidenceUrl" to p.evidenceUrl,
                "status" to p.status.name,
            )
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
    }

    private fun escape(s: String): String = s.replace("|", "\\|").replace("\n", " ")
}
