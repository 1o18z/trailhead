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
        // 어차피 품질 게이트에서 버려질 LOW 마커(TODO/XXX)는 검색하지 않는다 (API 절약 + rate limit 완화)
        val markers = props.signals.todoMarkers.filter { tierFor(it) != Tier.LOW }
        if (markers.isEmpty()) return emptyList()
        val perMarker = (props.signals.maxPerRepo / markers.size).coerceAtLeast(1)
        return markers.flatMap { marker ->
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
                    filePath = hit.path,
                )
            }
        }
    }

    /** 명시적 결함 표시는 MEDIUM, 막연한 TODO/XXX 는 LOW → 품질 게이트에서 제거 */
    private fun tierFor(marker: String): Tier = when (marker.uppercase()) {
        "FIXME", "HACK" -> Tier.MEDIUM
        else -> Tier.LOW
    }

    /** 우리가 손댈 수 있는 '로직 소스'인가 — 테스트·생성물·vendored(번들된 남의 코드)는 제외 */
    private fun isSourceFile(path: String): Boolean {
        val lower = path.lowercase()
        if (SOURCE_EXT.none { lower.endsWith(it) }) return false
        if (NON_SOURCE_HINT.any { lower.contains(it) }) return false
        // 버전 박힌 파일명(sockjs-0.3.4.js 등)은 거의 항상 번들된 서드파티 라이브러리 → 제외
        val baseName = lower.substringAfterLast('/')
        if (VERSIONED_FILE.containsMatchIn(baseName)) return false
        return true
    }

    companion object {
        private val SOURCE_EXT = listOf(".kt", ".java", ".js", ".ts", ".tsx", ".py", ".go", ".rb", ".cs", ".scala")

        // 손댈 '로직'이 아닌 것들: 테스트/벤더/서드파티/생성물/번들/데이터바인딩 등
        private val NON_SOURCE_HINT = listOf(
            "/test/", "/tests/", "generated", ".min.", ".bundle.", "/vendor/", "/node_modules/",
            "/third_party/", "/third-party/", "/svg/", "/build/", "/dist/", "/deps/",
            "databinding", "/proto/", "_pb.", ".pb.", "/fixtures/", "/testdata/", "/assets/",
        )

        // 파일명에 `-0.3.4` / `-v1.2` 같은 버전이 박힌 건 번들 라이브러리 신호
        private val VERSIONED_FILE = Regex("""-v?\d+\.\d+(\.\d+)*""", RegexOption.IGNORE_CASE)
    }
}
