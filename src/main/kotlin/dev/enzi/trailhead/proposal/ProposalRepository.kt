package dev.enzi.trailhead.proposal

import org.springframework.data.jpa.repository.JpaRepository

interface ProposalRepository : JpaRepository<Proposal, String> {
    /** 등급 좋은 순(HIGH 먼저), 같은 등급이면 별 많은 순 */
    fun findAllByOrderByTierAscStarsDesc(): List<Proposal>
}
