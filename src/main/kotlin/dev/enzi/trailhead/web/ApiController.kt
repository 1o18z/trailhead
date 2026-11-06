package dev.enzi.trailhead.web

import dev.enzi.trailhead.proposal.Proposal
import dev.enzi.trailhead.proposal.ProposalRepository
import dev.enzi.trailhead.proposal.ProposalService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ApiController(
    private val repository: ProposalRepository,
    private val proposalService: ProposalService,
) {

    @PostMapping("/scan")
    fun scan(): Map<String, Any?> {
        val result = proposalService.scan()
        return mapOf(
            "reposScanned" to result.reposScanned,
            "rawSignals" to result.rawSignals,
            "droppedLow" to result.droppedLow,
            "proposalCount" to result.proposals.size,
            "reportPath" to result.reportPath,
            "proposals" to result.proposals.map { it.toView() },
        )
    }

    @GetMapping("/proposals")
    fun proposals(): List<Map<String, Any?>> =
        repository.findAllByOrderByTierAscStarsDesc().map { it.toView() }

    private fun Proposal.toView(): Map<String, Any?> = mapOf(
        "tier" to tier.name,
        "type" to type.name,
        "signal" to signalType.name,
        "repo" to repoFullName,
        "repoUrl" to repoUrl,
        "stars" to stars,
        "language" to language,
        "title" to title,
        "detail" to detail,
        "evidenceUrl" to evidenceUrl,
        "status" to status.name,
    )
}
