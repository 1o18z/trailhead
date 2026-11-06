package dev.enzi.trailhead.signal

import dev.enzi.trailhead.github.Repo
import dev.enzi.trailhead.proposal.ProposalType
import dev.enzi.trailhead.proposal.SignalType
import dev.enzi.trailhead.proposal.Tier

/**
 * 레포에서 수집된 "기여거리 한 건"의 원재료.
 * 등급(tier)은 각 수집기가 도메인 지식으로 1차 평가한다.
 */
data class RawSignal(
    val repo: Repo,
    val signalType: SignalType,
    val proposalType: ProposalType,
    val tier: Tier,
    val title: String,
    val detail: String,
    val evidenceUrl: String,
    /** 중복 판정을 위한 안정적 키 (레포 + 신호 + 위치) */
    val dedupKey: String,
    /** 변경 대상 파일 경로. 파일 기반 신호(TODO/과대파일)만 가짐, 그 외 null */
    val filePath: String? = null,
)
