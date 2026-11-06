package dev.enzi.trailhead.web

import dev.enzi.trailhead.discovery.Candidate
import dev.enzi.trailhead.discovery.CandidateRepository
import dev.enzi.trailhead.discovery.DiscoveryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ApiController(
    private val repository: CandidateRepository,
    private val discoveryService: DiscoveryService,
) {

    @PostMapping("/discover")
    fun discover(): Map<String, Any?> {
        val result = discoveryService.discover()
        return mapOf(
            "scanned" to result.scanned,
            "reportPath" to result.reportPath,
            "candidates" to result.candidates.map { it.toView() },
        )
    }

    @GetMapping("/candidates")
    fun candidates(): List<Map<String, Any?>> =
        repository.findAllByOrderByScoreDesc().map { it.toView() }

    private fun Candidate.toView(): Map<String, Any?> = mapOf(
        "score" to score,
        "status" to status.name,
        "repo" to repoFullName,
        "issueNumber" to issueNumber,
        "title" to title,
        "issueUrl" to issueUrl,
        "repoUrl" to repoUrl,
        "language" to language,
        "matchedLabel" to matchedLabel,
        "stars" to stars,
        "openPrCount" to openPrCount,
        "closedUnmergedPrCount" to closedUnmergedPrCount,
        "mergedPrCount" to mergedPrCount,
        "reasons" to reasons,
    )
}
