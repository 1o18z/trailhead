package dev.enzi.trailhead.web

import dev.enzi.trailhead.discovery.CandidateRepository
import dev.enzi.trailhead.discovery.CandidateStatus
import dev.enzi.trailhead.discovery.DiscoveryService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping

@Controller
class DashboardController(
    private val repository: CandidateRepository,
    private val discoveryService: DiscoveryService,
) {

    @GetMapping("/")
    fun dashboard(model: Model): String {
        val all = repository.findAllByOrderByScoreDesc()
        model.addAttribute("candidates", all.filter { it.status != CandidateStatus.SKIP })
        model.addAttribute("skippedCount", all.count { it.status == CandidateStatus.SKIP })
        model.addAttribute("total", all.size)
        return "dashboard"
    }

    /** 대시보드의 '지금 탐색' 버튼 */
    @PostMapping("/discover")
    fun runDiscovery(): String {
        discoveryService.discover()
        return "redirect:/"
    }
}
