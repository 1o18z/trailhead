package dev.enzi.trailhead.discovery

import dev.enzi.trailhead.github.RepoInfo
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

data class PrSignals(
    val openPrCount: Int,
    val closedUnmergedPrCount: Int,
    val mergedPrCount: Int,
)

data class ScoreResult(
    val score: Int,
    val status: CandidateStatus,
    val reasons: List<String>,
)

@Service
class ScoringService {

    fun evaluate(repo: RepoInfo?, matchedLabel: String, comments: Int, pr: PrSignals): ScoreResult {
        val reasons = mutableListOf<String>()
        var score = 50

        // 1) 중복/거절 상태 판정 (가장 중요)
        val status = when {
            pr.mergedPrCount > 0 -> CandidateStatus.SKIP
            pr.openPrCount > 0 -> CandidateStatus.TAKEN
            pr.closedUnmergedPrCount > 0 -> CandidateStatus.RISKY
            else -> CandidateStatus.AVAILABLE
        }
        when (status) {
            CandidateStatus.SKIP -> {
                reasons += "이미 머지된 PR(${pr.mergedPrCount}건)이 이 이슈를 참조 → 해결됐을 가능성"
                score -= 100
            }
            CandidateStatus.TAKEN -> {
                reasons += "열린 PR ${pr.openPrCount}건이 이미 이 이슈를 작업 중 → 중복 위험"
                score -= 40
            }
            CandidateStatus.RISKY -> {
                reasons += "과거 머지 안 된 PR ${pr.closedUnmergedPrCount}건 → 접근이 거절됐을 수 있음(주의)"
                score -= 20
            }
            CandidateStatus.AVAILABLE -> {
                reasons += "이 이슈를 참조하는 열린 PR 없음 → 바로 집을 수 있음"
            }
        }

        // 2) 초심자 친화 라벨
        if (matchedLabel.equals("good first issue", ignoreCase = true)) {
            score += 15
            reasons += "good first issue 라벨 (+15)"
        } else {
            score += 5
            reasons += "$matchedLabel 라벨 (+5)"
        }

        // 3) 저장소 건강도
        if (repo != null) {
            if (repo.archived) {
                score -= 60
                reasons += "저장소가 archived 상태 (-60)"
            }
            if (repo.fork) {
                score -= 10
                reasons += "fork 저장소 (-10)"
            }
            val days = daysSince(repo.pushedAt)
            when {
                days == null -> {}
                days <= 30 -> { score += 15; reasons += "최근 30일 내 활발 (+15)" }
                days <= 90 -> { score += 8; reasons += "최근 90일 내 활동 (+8)" }
                days <= 365 -> { score += 3; reasons += "최근 1년 내 활동 (+3)" }
                else -> { score -= 10; reasons += "1년 넘게 push 없음 (-10)" }
            }
            when {
                repo.stars in 100..20000 -> { score += 10; reasons += "적정 규모(⭐${repo.stars}) (+10)" }
                repo.stars in 20..99 || repo.stars > 20000 -> { score += 5; reasons += "⭐${repo.stars} (+5)" }
                else -> reasons += "⭐${repo.stars} (소규모)"
            }
        } else {
            reasons += "저장소 정보 조회 실패 (건강도 미반영)"
        }

        // 4) 토론량
        when {
            comments == 0 -> { score += 5; reasons += "댓글 0 — 아직 아무도 손 안 댐 (+5)" }
            comments in 1..5 -> { score += 3; reasons += "댓글 $comments — 적당한 맥락 (+3)" }
            comments > 15 -> { score -= 5; reasons += "댓글 $comments — 논쟁/정체 가능 (-5)" }
            else -> reasons += "댓글 $comments"
        }

        return ScoreResult(score.coerceIn(0, 100), status, reasons)
    }

    private fun daysSince(isoTimestamp: String?): Long? {
        if (isoTimestamp.isNullOrBlank()) return null
        return try {
            Duration.between(Instant.parse(isoTimestamp), Instant.now()).toDays()
        } catch (e: Exception) {
            null
        }
    }
}
