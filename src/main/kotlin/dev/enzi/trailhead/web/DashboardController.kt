package dev.enzi.trailhead.web

import dev.enzi.trailhead.proposal.ProposalRepository
import dev.enzi.trailhead.proposal.ProposalService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping

@Controller
class DashboardController(
    private val repository: ProposalRepository,
    private val proposalService: ProposalService,
) {

    @GetMapping("/")
    fun dashboard(model: Model): String {
        val all = repository.findAllByOrderByTierAscStarsDesc()
        model.addAttribute("proposals", all)
        model.addAttribute("total", all.size)
        model.addAttribute("high", all.count { it.tier.name == "HIGH" })
        return "dashboard"
    }

    /** 대시보드의 '지금 스캔' 버튼 */
    @PostMapping("/scan")
    fun runScan(): String {
        proposalService.scan()
        return "redirect:/"
    }
}
