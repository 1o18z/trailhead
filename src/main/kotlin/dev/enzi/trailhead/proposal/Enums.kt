package dev.enzi.trailhead.proposal

/** 제안의 품질 등급. LOW 는 품질 게이트에서 버려진다. */
enum class Tier {
    HIGH, MEDIUM, LOW;

    fun atLeast(min: Tier): Boolean = this.ordinal <= min.ordinal
}

/** 기여 유형 */
enum class ProposalType {
    BUG_FIX, REFACTOR, DEPENDENCY
}

/** 제안을 만들어낸 신호의 종류 */
enum class SignalType {
    TODO_MARKER, LARGE_FILE, FAILING_CI, OUTDATED_DEPENDENCY
}

/** 제안의 처리 상태 */
enum class ProposalStatus {
    /** 새로 발견됨 (아직 아무 조치 없음) */
    NEW,

    /** 이 레포에 PR 로 기록됨 (알림 완료) */
    RECORDED,

    /** 내가 승인함 → 구현 대상 */
    APPROVED,

    /** 대상 레포에 PR 올림 */
    SUBMITTED,

    /** 내가 보류/기각 */
    REJECTED,
}
