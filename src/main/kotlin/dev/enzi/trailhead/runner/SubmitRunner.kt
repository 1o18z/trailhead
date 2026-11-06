package dev.enzi.trailhead.runner

import dev.enzi.trailhead.pr.SubmitService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 1회 제출 모드. `--trailhead.mode=submit --trailhead.submit.pr-number=<후보 PR 번호>` 로 켠다.
 * 승인 감지 워크플로가 후보 PR 번호를 넘겨 호출 → 대상 레포에 정식 upstream PR 을 올린다.
 */
@Component
@ConditionalOnProperty(name = ["trailhead.mode"], havingValue = "submit")
class SubmitRunner(
    private val submitService: SubmitService,
    @org.springframework.beans.factory.annotation.Value("\${trailhead.submit.pr-number:0}")
    private val prNumber: Int,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (prNumber <= 0) {
            log.warn("제출 모드인데 trailhead.submit.pr-number 가 없습니다 — 중단")
            return
        }
        log.info("=== trailhead submit 모드 시작 (후보 PR #{}) ===", prNumber)
        val url = submitService.submit(prNumber)
        if (url != null) log.info("업스트림 PR: {}", url) else log.warn("업스트림 PR 생성 안 됨")
        log.info("=== trailhead submit 모드 종료 ===")
    }
}
