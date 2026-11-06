package dev.enzi.trailhead.proposal

import dev.enzi.trailhead.config.TrailheadProperties
import dev.enzi.trailhead.discovery.RepoDiscoveryService
import dev.enzi.trailhead.report.ReportWriter
import dev.enzi.trailhead.signal.RawSignal
import dev.enzi.trailhead.signal.SignalCollector
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

data class ScanResult(
    val reposScanned: Int,
    val rawSignals: Int,
    val droppedLow: Int,
    val proposals: List<Proposal>,
    val reportPath: String?,
)

@Service
class ProposalService(
    private val repoDiscovery: RepoDiscoveryService,
    private val collectors: List<SignalCollector>,
    private val repository: ProposalRepository,
    private val reportWriter: ReportWriter,
    private val props: TrailheadProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun scan(): ScanResult {
        val minTier = parseTier(props.grading.minTier)
        val active = collectors.filter { it.enabled() }
        log.info("스캔 시작: 수집기 {}, 최소등급 {}", active.map { it.name() }, minTier)

        // 1) 레포 발굴
        val repos = repoDiscovery.discover()

        // 2) 레포별 신호 수집
        val raw = ArrayList<RawSignal>()
        for (repo in repos) {
            for (collector in active) {
                val signals = collector.collect(repo)
                if (signals.isNotEmpty()) {
                    log.info("  [{}] {} → {}건", repo.fullName, collector.name(), signals.size)
                }
                raw += signals
            }
        }

        // 3) 품질 게이트: LOW(억지 제안) 제거
        val (kept, dropped) = raw.partition { it.tier.atLeast(minTier) }
        log.info("신호 {}건 중 {}건 통과, {}건 LOW 제거", raw.size, kept.size, dropped.size)

        // 4) 제안으로 변환 + upsert (중복 키 기준)
        val proposals = kept.distinctBy { it.dedupKey }.map { toProposal(it) }
        repository.saveAll(proposals)

        val ranked = repository.findAllByOrderByTierAscStarsDesc()

        // 5) 콘솔 + 리포트
        printToConsole(ranked)
        val reportPath = reportWriter.write(ranked)

        return ScanResult(
            reposScanned = repos.size,
            rawSignals = raw.size,
            droppedLow = dropped.size,
            proposals = ranked,
            reportPath = reportPath,
        )
    }

    private fun toProposal(s: RawSignal): Proposal {
        val existing = repository.findById(s.dedupKey).orElse(null)
        return (existing ?: Proposal()).apply {
            id = s.dedupKey
            repoFullName = s.repo.fullName
            repoUrl = s.repo.htmlUrl
            language = s.repo.language ?: ""
            stars = s.repo.stars
            type = s.proposalType
            tier = s.tier
            signalType = s.signalType
            title = s.title.take(500)
            detail = s.detail
            evidenceUrl = s.evidenceUrl
            filePath = s.filePath
            lastSeenAt = Instant.now()
            // status, discoveredAt 은 기존 값 유지 (신규면 기본값)
        }
    }

    private fun parseTier(value: String): Tier =
        runCatching { Tier.valueOf(value.trim().uppercase()) }.getOrDefault(Tier.MEDIUM)

    private fun printToConsole(ranked: List<Proposal>) {
        val top = ranked.take(15)
        val sb = StringBuilder("\n===== trailhead 기여거리 제안 TOP ${top.size} =====\n")
        top.forEachIndexed { i, p ->
            sb.append("%2d. [%-6s] %-9s %s  — %s\n".format(i + 1, p.tier, p.type, p.repoFullName, p.title.take(60)))
        }
        sb.append("================================\n")
        log.info(sb.toString())
    }
}
