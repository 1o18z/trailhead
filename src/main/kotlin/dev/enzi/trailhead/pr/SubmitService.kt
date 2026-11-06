package dev.enzi.trailhead.pr

import dev.enzi.trailhead.config.TrailheadProperties
import dev.enzi.trailhead.github.GitHubClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 승인된 후보 PR(이 trailhead 레포)을 보고 대상 레포에 **정식 upstream PR** 을 올린다 (Phase B).
 *
 * 스캔 런과 제출 런은 DB 가 분리되므로, 제출에 필요한 정보는 후보 PR 본문의
 * 기계판독 메타데이터(trailhead-target/head/title/file)에서만 복원한다.
 * 실제 변경은 이미 fork 브랜치에 커밋돼 있고, 여기서는 그 head 로 PR 만 연다.
 */
@Service
class SubmitService(
    private val client: GitHubClient,
    private val props: TrailheadProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun submit(candidatePrNumber: Int): String? {
        val (owner, repo) = props.pr.repo.split("/", limit = 2).takeIf { it.size == 2 } ?: run {
            log.warn("trailhead.pr.repo 가 owner/repo 형식이 아님: '{}'", props.pr.repo); return null
        }

        val pr = client.getPull(owner, repo, candidatePrNumber) ?: return null
        val body = pr.body ?: run { log.warn("후보 PR #{} 본문 없음", candidatePrNumber); return null }

        val target = meta(body, "trailhead-target")
        val head = meta(body, "trailhead-head")
        if (target == null || head == null) {
            val msg = "이 PR 에는 자동 제출할 변경 메타데이터가 없습니다 (AI 변경이 생성되지 않은 제안)."
            log.warn("후보 PR #{}: {}", candidatePrNumber, msg)
            client.addComment(owner, repo, candidatePrNumber, "⚠️ $msg")
            return null
        }
        val title = meta(body, "trailhead-title") ?: "Contribution via trailhead"
        val file = meta(body, "trailhead-file")

        val (tOwner, tRepo) = target.split("/", limit = 2).takeIf { it.size == 2 } ?: return null
        val base = client.getRepo(tOwner, tRepo)?.defaultBranch ?: "main"

        val url = client.createPull(tOwner, tRepo, title, head, base, upstreamBody(file, owner, repo, candidatePrNumber))

        val comment = if (url != null) "✅ 업스트림 정식 PR 을 올렸습니다: $url"
        else "⚠️ 업스트림 PR 생성 실패 (이미 존재하거나 권한/브랜치 문제일 수 있음 — 워크플로 로그 확인)."
        client.addComment(owner, repo, candidatePrNumber, comment)
        if (url != null) log.info("업스트림 PR 생성: {} ({} <- {})", url, target, head)
        return url
    }

    private fun upstreamBody(file: String?, owner: String, repo: String, candidate: Int): String = buildString {
        append("## Summary\n\n")
        append("This change")
        if (file != null) append(" updates `").append(file).append("`")
        append(" to address an issue surfaced by static review.\n\n")
        append("It was prepared with AI assistance and reviewed by a human before submission. ")
        append("Happy to adjust the approach to fit the project's conventions — feedback welcome.\n\n")
        append("<sub>Prepared via [trailhead](https://github.com/").append(owner).append("/").append(repo)
        append("/pull/").append(candidate).append(").</sub>\n")
    }

    /** 본문에서 `<!-- key: value -->` 값을 뽑는다. */
    private fun meta(body: String, key: String): String? =
        Regex("<!--\\s*$key:\\s*(.*?)\\s*-->").find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
}
