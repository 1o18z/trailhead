package dev.enzi.trailhead.signal

import dev.enzi.trailhead.config.TrailheadProperties
import dev.enzi.trailhead.github.GitHubClient
import dev.enzi.trailhead.github.Repo
import dev.enzi.trailhead.proposal.ProposalType
import dev.enzi.trailhead.proposal.SignalType
import dev.enzi.trailhead.proposal.Tier
import org.springframework.stereotype.Component

/** 지나치게 큰 소스 파일을 리팩터링(분할) 후보로 본다. (MEDIUM) */
@Component
class CodeSmellCollector(
    private val client: GitHubClient,
    private val props: TrailheadProperties,
) : SignalCollector {

    override fun enabled() = props.signals.codeSmellEnabled
    override fun name() = "code-smell"

    override fun collect(repo: Repo): List<RawSignal> {
        val (owner, name) = repo.ownerAndName() ?: return emptyList()
        val tree = client.getTree(owner, name, repo.defaultBranch) ?: return emptyList()
        val threshold = props.signals.largeFileBytes

        return tree.tree
            .asSequence()
            .filter { it.type == "blob" && (it.size ?: 0) > threshold }
            .filter { isSourceFile(it.path) }
            .sortedByDescending { it.size ?: 0 }
            .take(props.signals.maxPerRepo)
            .map { entry ->
                val kb = (entry.size ?: 0) / 1024
                RawSignal(
                    repo = repo,
                    signalType = SignalType.LARGE_FILE,
                    proposalType = ProposalType.REFACTOR,
                    tier = Tier.MEDIUM,
                    title = "과대 파일 분할 리팩터링: ${entry.path}",
                    detail = "`${entry.path}` 가 약 ${kb}KB 로 매우 큼. 단일 책임/가독성 관점에서 " +
                        "모듈·클래스 분할이나 함수 추출 리팩터링 기여 기회.",
                    evidenceUrl = "${repo.htmlUrl}/blob/${repo.defaultBranch}/${entry.path}",
                    dedupKey = "${repo.fullName}:LARGE_FILE:${entry.path}",
                )
            }
            .toList()
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
