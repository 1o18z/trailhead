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
        val (owner, name) = repo.ownerAndName() ?: return emptyList()
        // 어차피 품질 게이트에서 버려질 LOW 마커(TODO/XXX)는 검색하지 않는다 (API 절약 + rate limit 완화)
        val markers = props.signals.todoMarkers.filter { tierFor(it) != Tier.LOW }
        if (markers.isEmpty()) return emptyList()
        val perMarker = (props.signals.maxPerRepo / markers.size).coerceAtLeast(1)
        return markers.flatMap { marker ->
            client.searchCode(repo.fullName, marker, perMarker)
                // 데이터/설정 파일(.acl, .txt 등)의 우연한 매치 제외 — 코드 주석만 노림
                .filter { isSourceFile(it.path) }
                // 마커 주석을 실제로 읽어, 외부 기여자가 못 고치는 마커(blocked/복사/수사의문)는 버림
                .filter { markerActionable(owner, name, it.path, marker) }
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

    /**
     * 마커 주석을 실제로 읽어 '외부 기여자가 지금 고칠 수 있는가'를 본다.
     * 파일을 못 읽으면(권한·404 등) 보수적으로 통과시켜 기존 동작을 유지한다.
     *  - 통째로 복사해 온 외부 코드(출처 표기 있음) → 제외
     *  - 마커 줄이 전부 blocked(이슈/PR 링크·조건절)·수사의문문이면 → 제외
     */
    private fun markerActionable(owner: String, name: String, path: String, marker: String): Boolean {
        val content = client.getFileContent(owner, name, path) ?: return true
        if (COPIED_HINT.any { content.contains(it, ignoreCase = true) }) return false
        if (isForeignLicensedCopy(owner, content)) return false
        val markerLines = content.lineSequence().filter { it.contains(marker, ignoreCase = true) }.toList()
        if (markerLines.isEmpty()) return true // 검색은 맞았는데 라인 매칭 실패(희귀): 통과
        return markerLines.any { isActionableLine(it) }
    }

    /**
     * 파일 상단 헤더가 '다른 재단의 라이선스'를 달고 있으면 외부에서 통째로 가져온 복사본으로 본다.
     * (예: glide 안의 Apache Harmony BufferedInputStream — ASF 헤더를 달고 있음)
     * 단, 대상 레포가 그 재단 소속이면 자기 코드일 수 있으니 소유자를 제외한다(오탐 방지).
     */
    private fun isForeignLicensedCopy(owner: String, content: String): Boolean {
        val head = content.take(1500).lowercase()
        return FOREIGN_LICENSE.any { (phrase, foundationOwner) ->
            head.contains(phrase) && !owner.equals(foundationOwner, ignoreCase = true)
        }
    }

    /** 이 마커 한 줄이 '지금 고칠 수 있는' 것인지 */
    private fun isActionableLine(line: String): Boolean {
        val l = line.lowercase()
        // 외부 이슈/PR 링크가 걸린 마커 = 그 외부 변경이 풀려야 함(blocked)
        if (ISSUE_LINK.containsMatchIn(line)) return false
        // 조건부·보류 표현("~되면/~된 후에 제거하라") = 외부 기여자가 지금 못 함
        if (BLOCKED_HINT.any { l.contains(it) }) return false
        // 수사 의문문("아직 필요한가?") = 명확한 결함 아님, 고위험 → 제외
        if (l.contains("still needed") || l.contains("still required") ||
            (l.contains("?") && (l.contains("needed") || l.contains("necessary")))
        ) return false
        return true
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

        // 마커에 외부 이슈/PR 링크가 걸림 = 그 변경이 머지돼야 풀리는 blocked 신호
        private val ISSUE_LINK = Regex("""github\.com/[^\s)]+/(?:pull|issues)/\d+""", RegexOption.IGNORE_CASE)

        // 조건부·보류("~되면/~된 뒤에 제거") 표현 — 외부 기여자가 지금 손 못 댐
        private val BLOCKED_HINT = listOf(
            "remove this when", "remove when", "remove once", "remove after",
            "when we use", "once we", "after we ", "until we", "when upgraded", "after upgrade",
            "blocked by", "waiting on", "waiting for", "depends on ", "pending ",
        )

        // 통째로 복사해 온 외부 코드라는 명시적 출처 표기 — 이 레포가 고칠 대상 아님
        private val COPIED_HINT = listOf(
            "copied from", "ported from", "adapted from", "taken from", "originally from",
        )

        // 파일 헤더의 '외부 재단에 저작권을 귀속'시키는 보일러플레이트 → 복사본 신호.
        // (프로젝트가 자기 코드에 흔히 쓰는 일반 라이선스(MIT/EPL 등)는 제외 — 오탐 방지)
        // (phrase, 그 재단의 GitHub owner=예외: 해당 재단 레포면 자기 코드일 수 있음)
        private val FOREIGN_LICENSE = listOf(
            "licensed to the apache software foundation" to "apache",
            "this file is part of the gnu" to "gnu",
        )
    }
}
