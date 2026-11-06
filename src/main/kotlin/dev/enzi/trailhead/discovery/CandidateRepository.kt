package dev.enzi.trailhead.discovery

import org.springframework.data.jpa.repository.JpaRepository

interface CandidateRepository : JpaRepository<Candidate, String> {
    fun findAllByOrderByScoreDesc(): List<Candidate>
    fun findByStatusNotOrderByScoreDesc(status: CandidateStatus): List<Candidate>
}
