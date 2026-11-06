package dev.enzi.trailhead.discovery

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant

enum class CandidateStatus {
    /** 열린 PR 없음 — 바로 집어도 되는 상태 */
    AVAILABLE,

    /** 이슈를 참조하는 열린 PR 이 있음 — 누군가 이미 작업 중 */
    TAKEN,

    /** 과거에 머지 안 되고 닫힌 PR 이 있음 — 접근이 거절됐을 수 있어 주의 */
    RISKY,

    /** 이미 머지된 PR 이 이슈를 참조 — 해결됐을 가능성, 건너뜀 */
    SKIP,
}

@Entity
@Table(name = "candidate")
class Candidate(
    @Id
    @Column(length = 500)
    var issueUrl: String = "",

    var repoFullName: String = "",
    var repoUrl: String = "",
    var issueNumber: Int = 0,

    @Column(length = 500)
    var title: String = "",

    var language: String = "",
    var matchedLabel: String = "",

    var stars: Int = 0,
    var repoPushedAt: String? = null,
    var comments: Int = 0,

    var openPrCount: Int = 0,
    var closedUnmergedPrCount: Int = 0,
    var mergedPrCount: Int = 0,

    var score: Int = 0,

    @Enumerated(EnumType.STRING)
    var status: CandidateStatus = CandidateStatus.AVAILABLE,

    /** 점수 산정 사유. "|" 로 구분해 저장 */
    @Lob
    @Column(columnDefinition = "CLOB")
    var reasonsJoined: String = "",

    var discoveredAt: Instant = Instant.now(),
    var lastSeenAt: Instant = Instant.now(),
) {
    var reasons: List<String>
        get() = if (reasonsJoined.isBlank()) emptyList() else reasonsJoined.split("|")
        set(value) {
            reasonsJoined = value.joinToString("|")
        }
}
