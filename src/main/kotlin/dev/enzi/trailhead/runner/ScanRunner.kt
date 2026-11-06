package dev.enzi.trailhead.runner

import dev.enzi.trailhead.pr.PrRecordingService
import dev.enzi.trailhead.proposal.ProposalService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 1회 스캔 모드. `--trailhead.mode=scan` 으로 켠다.
 * 스케줄러(GitHub Actions)에서는 웹서버 없이(`spring.main.web-application-type=none`) 실행해
 * 스캔 → 신규 제안 PR 기록 후 프로세스가 종료되도록 한다.
 */
@Component
@ConditionalOnProperty(name = ["trailhead.mode"], havingValue = "scan")
class ScanRunner(
    private val proposalService: ProposalService,
    private val prRecordingService: PrRecordingService,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        log.info("=== trailhead scan 모드 시작 ===")
        val scan = proposalService.scan()
        log.info("스캔: 레포 {}개, 신호 {}건, LOW 제거 {}건, 제안 {}건",
            scan.reposScanned, scan.rawSignals, scan.droppedLow, scan.proposals.size)

        val record = prRecordingService.recordNew(scan.proposals)
        if (record.enabled) {
            log.info("PR 기록: 생성 {}건, 스킵 {}건", record.created, record.skippedExisting)
            record.prUrls.forEach { log.info("  - {}", it) }
        }
        log.info("=== trailhead scan 모드 종료 ===")
    }
}
