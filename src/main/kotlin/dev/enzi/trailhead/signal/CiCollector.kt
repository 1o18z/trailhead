package dev.enzi.trailhead.signal

import dev.enzi.trailhead.config.TrailheadProperties
import dev.enzi.trailhead.github.GitHubClient
import dev.enzi.trailhead.github.Repo
import dev.enzi.trailhead.proposal.ProposalType
import dev.enzi.trailhead.proposal.SignalType
import dev.enzi.trailhead.proposal.Tier
import org.springframework.stereotype.Component

/** 기본 브랜치 최신 커밋의 CI 가 깨져 있으면 버그 수정 기회로 본다. (HIGH) */
@Component
class CiCollector(
    private val client: GitHubClient,
    private val props: TrailheadProperties,
) : SignalCollector {

    override fun enabled() = props.signals.ciEnabled
    override fun name() = "failing-ci"

    override fun collect(repo: Repo): List<RawSignal> {
        val (owner, name) = repo.ownerAndName() ?: return emptyList()
        val sha = client.getHeadSha(owner, name, repo.defaultBranch) ?: return emptyList()
        val failing = client.getCheckRuns(owner, name, sha)
            .filter { it.status == "completed" && it.conclusion in FAILING }
            .take(props.signals.maxPerRepo)

        return failing.map { run ->
            RawSignal(
                repo = repo,
                signalType = SignalType.FAILING_CI,
                proposalType = ProposalType.BUG_FIX,
                tier = Tier.HIGH,
                title = "실패 중인 CI 체크 '${run.name}' 복구",
                detail = "기본 브랜치(커밋 ${sha.take(7)})에서 체크 '${run.name}' 이 ${run.conclusion} 상태. " +
                    "회귀(regression)나 깨진 테스트일 가능성이 높아 버그 수정 기여 기회.",
                evidenceUrl = run.htmlUrl ?: repo.htmlUrl,
                dedupKey = "${repo.fullName}:FAILING_CI:${run.name}",
            )
        }
    }

    companion object {
        private val FAILING = setOf("failure", "timed_out")
    }
}
