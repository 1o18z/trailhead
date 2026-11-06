package dev.enzi.trailhead.proposal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant

/** 레포에서 발굴된 기여거리 제안 한 건. */
@Entity
@Table(name = "proposal")
class Proposal(
    /** 안정적 중복 키 (= RawSignal.dedupKey) */
    @Id
    @Column(length = 600)
    var id: String = "",

    var repoFullName: String = "",
    @Column(length = 400)
    var repoUrl: String = "",
    var language: String = "",
    var stars: Int = 0,

    @Enumerated(EnumType.STRING)
    var type: ProposalType = ProposalType.REFACTOR,

    @Enumerated(EnumType.STRING)
    var tier: Tier = Tier.MEDIUM,

    @Enumerated(EnumType.STRING)
    var signalType: SignalType = SignalType.TODO_MARKER,

    @Column(length = 500)
    var title: String = "",

    @Lob
    @Column(columnDefinition = "CLOB")
    var detail: String = "",

    @Column(length = 800)
    var evidenceUrl: String = "",

    @Enumerated(EnumType.STRING)
    var status: ProposalStatus = ProposalStatus.NEW,

    var discoveredAt: Instant = Instant.now(),
    var lastSeenAt: Instant = Instant.now(),
)
