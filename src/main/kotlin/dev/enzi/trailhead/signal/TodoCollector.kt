package dev.enzi.trailhead.signal

import dev.enzi.trailhead.config.TrailheadProperties
import dev.enzi.trailhead.github.GitHubClient
import dev.enzi.trailhead.github.Repo
import dev.enzi.trailhead.proposal.ProposalType
import dev.enzi.trailhead.proposal.SignalType
import dev.enzi.trailhead.proposal.Tier
import org.springframework.stereotype.Component

/**
 * 코드 내 마커(FIXME/HACK/TODO/XXX)를 찾는다.
 * FIXME/HACK 는 "여긴 잘못/임시다" 라는 명시 신호라 MEDIUM, 맥락 약한 TODO/XXX 는 LOW(게이트에서 버림).
 */
@Component
class TodoCollector(
    private val client: GitHubClient,
    private val props: TrailheadProperties,
) : SignalCollector {

    override fun enabled() = props.signals.todoEnabled
    override fun name() = "todo-marker"

    override fun collect(repo: Repo): List<RawSignal> {
        val perMarker = (props.signals.maxPerRepo / props.signals.todoMarkers.size.coerceAtLeast(1)).coerceAtLeast(1)
        return props.signals.todoMarkers.flatMap { marker ->
            client.searchCode(repo.fullName, marker, perMarker)
                // 데이터/설정 파일(.acl, .txt 등)의 우연한 매치 제외 — 코드 주석만 노림
                .filter { isSourceFile(it.path) }
                .map { hit ->
                val tier = tierFor(marker)
                RawSignal(
                    repo = repo,
                    signalType = SignalType.TODO_MARKER,
                    proposalType = if (marker == "FIXME") ProposalType.BUG_FIX else ProposalType.REFACTOR,
                    tier = tier,
                    title = "$marker 해소: ${hit.path}",
                    detail = "`${hit.path}` 에 `$marker` 표시가 있음. 코드가 명시적으로 수정/정리 필요를 남긴 지점.",
                    evidenceUrl = hit.htmlUrl,
                    dedupKey = "${repo.fullName}:TODO_MARKER:$marker:${hit.path}",
                )
            }
        }
    }

    /** 명시적 결함 표시는 MEDIUM, 막연한 TODO/XXX 는 LOW → 품질 게이트에서 제거 */
    private fun tierFor(marker: String): Tier = when (marker.uppercase()) {
        "FIXME", "HACK" -> Tier.MEDIUM
        else -> Tier.LOW
    }

    private fun isSourceFile(path: String): Boolean {
        val lower = path.lowercase()
        return SOURCE_EXT.any { lower.endsWith(it) } && NON_SOURCE_HINT.none { lower.contains(it) }
    }

    companion object {
        private val SOURCE_EXT = listOf(".kt", ".java", ".js", ".ts", ".tsx", ".py", ".go", ".rb", ".cs", ".scala")
        private val NON_SOURCE_HINT = listOf("/test/", "/tests/", "generated", ".min.", "/vendor/", "/node_modules/")
    }
}
