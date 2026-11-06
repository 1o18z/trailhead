package dev.enzi.trailhead.discovery

import dev.enzi.trailhead.config.TrailheadProperties
import dev.enzi.trailhead.github.GitHubClient
import dev.enzi.trailhead.github.IssueItem
import dev.enzi.trailhead.github.RepoInfo
import dev.enzi.trailhead.report.ReportWriter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

data class DiscoveryResult(
    val scanned: Int,
    val candidates: List<Candidate>,
    val reportPath: String?,
)

@Service
class DiscoveryService(
    private val client: GitHubClient,
    private val scoring: ScoringService,
    private val repository: CandidateRepository,
    private val reportWriter: ReportWriter,
    private val props: TrailheadProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun discover(): DiscoveryResult {
        val cfg = props.discovery
        log.info("탐색 시작: languages={}, labels={}", cfg.languages, cfg.labels)

        // 1) 언어 × 라벨로 이슈 수집 (라벨은 검색에 쓴 것을 함께 기억)
        val collected = LinkedHashMap<String, Pair<IssueItem, String>>() // issueUrl -> (issue, label)
        for (language in cfg.languages) {
            for (label in cfg.labels) {
                val items = client.searchIssues(language, label, cfg.updatedWithinDays, cfg.perQueryLimit)
                log.info("  [{} / {}] {}건", language, label, items.size)
                for (item in items) {
                    collected.putIfAbsent(item.htmlUrl, item to label)
                }
            }
        }
        log.info("고유 이슈 {}건 수집, 상세 분석 시작", collected.size)

        // 2) 각 이슈 분석 (repo 정보는 캐시)
        val repoCache = HashMap<String, RepoInfo?>()
        val results = mutableListOf<Candidate>()
        for ((_, pair) in collected) {
            val (issue, label) = pair
            val repoFullName = issue.repoFullName()
            val repo = repoCache.getOrPut(repoFullName) { client.getRepo(repoFullName) }

            val prs = client.getCrossReferencedPrs(repoFullName, issue.number)
            val merged = prs.count { it.pullRequest?.mergedAt != null }
            val open = prs.count { it.state == "open" && it.pullRequest?.mergedAt == null }
            val closedUnmerged = prs.count { it.state == "closed" && it.pullRequest?.mergedAt == null }
            val signals = PrSignals(open, closedUnmerged, merged)

            val language = repo?.language ?: inferLanguage(cfg.languages, issue)
            val eval = scoring.evaluate(repo, label, issue.comments, signals)

            val candidate = (repository.findById(issue.htmlUrl).orElse(null) ?: Candidate()).apply {
                issueUrl = issue.htmlUrl
                this.repoFullName = repoFullName
                repoUrl = repo?.htmlUrl ?: "https://github.com/$repoFullName"
                issueNumber = issue.number
                title = issue.title.take(500)
                this.language = language
                matchedLabel = label
                stars = repo?.stars ?: 0
                repoPushedAt = repo?.pushedAt
                comments = issue.comments
                openPrCount = open
                closedUnmergedPrCount = closedUnmerged
                mergedPrCount = merged
                score = eval.score
                status = eval.status
                reasons = eval.reasons
                lastSeenAt = Instant.now()
            }
            results.add(candidate)
        }

        repository.saveAll(results)
        val ranked = results.sortedByDescending { it.score }

        // 3) 콘솔 + 리포트 파일
        printToConsole(ranked)
        val reportPath = reportWriter.write(ranked)

        log.info("탐색 완료: {}건 분석, 리포트={}", ranked.size, reportPath)
        return DiscoveryResult(scanned = ranked.size, candidates = ranked, reportPath = reportPath)
    }

    private fun inferLanguage(configured: List<String>, issue: IssueItem): String =
        configured.firstOrNull() ?: "unknown"

    private fun printToConsole(ranked: List<Candidate>) {
        val top = ranked.filter { it.status != CandidateStatus.SKIP }.take(15)
        val sb = StringBuilder("\n===== trailhead 기여 후보 TOP ${top.size} =====\n")
        top.forEachIndexed { i, c ->
            sb.append("%2d. [%3d] %-6s %s#%d  %s\n".format(i + 1, c.score, c.status, c.repoFullName, c.issueNumber, c.title.take(70)))
        }
        sb.append("================================\n")
        log.info(sb.toString())
    }
}
