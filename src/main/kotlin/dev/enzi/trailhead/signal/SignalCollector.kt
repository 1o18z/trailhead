package dev.enzi.trailhead.signal

import dev.enzi.trailhead.github.Repo

/** 레포 하나에서 특정 종류의 기여 신호를 수집하는 수집기 */
interface SignalCollector {
    /** 설정에서 꺼져 있으면 false */
    fun enabled(): Boolean

    /** 사람이 읽을 수집기 이름 (로그용) */
    fun name(): String

    fun collect(repo: Repo): List<RawSignal>
}
