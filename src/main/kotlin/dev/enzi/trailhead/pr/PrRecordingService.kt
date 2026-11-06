package dev.enzi.trailhead.pr

import dev.enzi.trailhead.config.TrailheadProperties
import dev.enzi.trailhead.github.GitHubClient
import dev.enzi.trailhead.proposal.Proposal
import dev.enzi.trailhead.proposal.ProposalRepository
import dev.enzi.trailhead.proposal.ProposalStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class RecordResult(
    val enabled: Boolean,
    val created: Int,
    val skippedExisting: Int,
    val prUrls: List<String>,
)

/**
 * 신규 제안을 이 trailhead 레포에 PR 로 기록(알림)한다.
 * 후보마다 전용 브랜치 + candidates/ 파일 + PR 1개.
 * 중복 판정은 "그 브랜치 head 의 PR 이 (열림+닫힘) 존재하는가" — 브랜치가 삭제돼도 재생성 안 됨.
 */
@Service
class PrRecordingService(
    private val client: GitHubClient,
    private val repository: ProposalRepository,
    private val props: TrailheadProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun recordNew(proposals: List<Proposal>): RecordResult {
        val cfg = props.pr
        if (!cfg.enabled) {
            log.info("PR 기록 비활성화 (trailhead.pr.enabled=false)")
            return RecordResult(enabled = false, created = 0, skippedExisting = 0, prUrls = emptyList())
        }
        val parts = cfg.repo.split("/", limit = 2)
        if (parts.size != 2) {
            log.warn("trailhead.pr.repo 가 owner/repo 형식이 아님: '{}'", cfg.repo)
            return RecordResult(enabled = true, created = 0, skippedExisting = 0, prUrls = emptyList())
        }
        val (owner, repo) = parts
        val baseSha = client.getHeadSha(owner, repo, cfg.baseBranch)
        if (baseSha == null) {
            log.warn("base 브랜치 '{}' head 조회 실패 — PR 기록 중단", cfg.baseBranch)
            return RecordResult(enabled = true, created = 0, skippedExisting = 0, prUrls = emptyList())
        }

        var created = 0
        var skipped = 0
        val urls = ArrayList<String>()

        for (p in proposals) {
            if (created >= cfg.maxPerRun) break
            if (p.status != ProposalStatus.NEW) { skipped++; continue }

            val slug = slug(p.id)
            val branch = "${cfg.branchPrefix}/$slug"

            if (client.pullExistsForHead(owner, repo, branch)) {
                p.status = ProposalStatus.RECORDED
                repository.save(p)
                skipped++
                continue
            }

            if (!client.createBranch(owner, repo, branch, baseSha)) continue
            val path = "${cfg.candidatesDir}/$slug.md"
            if (!client.putFile(owner, repo, path, branch, "trailhead: record ${p.repoFullName} proposal", fileContent(p))) continue
            val url = client.createPull(owner, repo, prTitle(p), branch, cfg.baseBranch, prBody(p))
            if (url != null) {
                p.status = ProposalStatus.RECORDED
                repository.save(p)
                urls += url
                created++
                log.info("PR 생성: {}", url)
            }
        }
        log.info("PR 기록 완료: 생성 {}건, 기존 스킵 {}건", created, skipped)
        return RecordResult(enabled = true, created = created, skippedExisting = skipped, prUrls = urls)
    }

    private fun prTitle(p: Proposal): String =
        "[trailhead] ${p.type} 제안: ${p.repoFullName} — ${p.title.take(60)}"

    private fun prBody(p: Proposal): String = buildString {
        append("## 기여거리 제안\n\n")
        append("- **대상 저장소**: [").append(p.repoFullName).append("](").append(p.repoUrl).append(") ")
        append("(⭐").append(p.stars).append(", ").append(p.language).append(")\n")
        append("- **유형 / 등급**: ").append(p.type).append(" / ").append(p.tier).append("\n")
        append("- **무엇을 기여할지**: ").append(p.title).append("\n")
        append("- **근거**: [").append(p.signalType).append(" 위치 보기](").append(p.evidenceUrl).append(")\n\n")
        append("### 설명\n").append(p.detail).append("\n\n")
        append("---\n")
        append("_trailhead 자동 기록. 승인하면(이 PR 을 머지/라벨) 다음 단계에서 구현·업스트림 PR 로 진행._\n")
        append("<!-- trailhead-id: ").append(p.id).append(" -->\n")
    }

    private fun fileContent(p: Proposal): String = buildString {
        append("# ").append(p.repoFullName).append(" — ").append(p.title).append("\n\n")
        append("| 항목 | 값 |\n|------|----|\n")
        append("| 대상 저장소 | [").append(p.repoFullName).append("](").append(p.repoUrl).append(") |\n")
        append("| 별 | ").append(p.stars).append(" |\n")
        append("| 언어 | ").append(p.language).append(" |\n")
        append("| 유형 | ").append(p.type).append(" |\n")
        append("| 등급 | ").append(p.tier).append(" |\n")
        append("| 신호 | ").append(p.signalType).append(" |\n")
        append("| 근거 | <").append(p.evidenceUrl).append("> |\n\n")
        append("## 설명\n\n").append(p.detail).append("\n")
    }

    /** dedupKey → 브랜치/파일에 쓸 안정적 슬러그 (길이 제한 + 해시로 충돌 방지) */
    private fun slug(id: String): String {
        val base = id.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60).trim('-')
        val hash = Integer.toHexString(id.hashCode()).let { if (it.startsWith("-")) "n" + it.drop(1) else it }
        return "$base-$hash"
    }
}
