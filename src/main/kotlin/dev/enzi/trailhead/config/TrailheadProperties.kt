package dev.enzi.trailhead.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "trailhead")
data class TrailheadProperties(
    val github: GitHub = GitHub(),
    val discovery: Discovery = Discovery(),
    val signals: Signals = Signals(),
    val grading: Grading = Grading(),
    val pr: Pr = Pr(),
    val ai: Ai = Ai(),
) {
    data class GitHub(
        val token: String = "",
        val baseUrl: String = "https://api.github.com",
    )

    /** 기여할 만한 레포를 발굴하는 기준 */
    data class Discovery(
        val languages: List<String> = listOf("kotlin", "java"),
        /** 인기 하한 */
        val minStars: Int = 100,
        /** 인기 상한 (너무 거대한 레포는 기여 난이도·경쟁 ↑) */
        val maxStars: Int = 40000,
        /** 한 번 실행에서 분석할 레포 최대 개수 */
        val maxRepos: Int = 15,
        /** 최근 N일 내 push 된(활발한) 레포만 */
        val pushedWithinDays: Long = 90,
        /** CONTRIBUTING 가이드가 있는 레포만(기여 친화) */
        val requireContributing: Boolean = true,
        val reportDir: String = "./reports",
    )

    /** 레포에서 기여거리를 뽑아낼 신호 수집 설정 */
    data class Signals(
        val todoEnabled: Boolean = true,
        val dependencyEnabled: Boolean = true,
        val codeSmellEnabled: Boolean = true,
        /** 실패 CI/테스트는 외부에서 고칠 수 없는 경우(봇·인프라·플래키)가 대부분이라 기여 조건에서 제외 */
        val ciEnabled: Boolean = false,
        /** 코드 내에서 찾을 마커 */
        val todoMarkers: List<String> = listOf("TODO", "FIXME", "HACK", "XXX"),
        /** 레포당 신호 종류별 최대 수집 개수 (API 절약) */
        val maxPerRepo: Int = 20,
        /** 코드 냄새: 이 바이트 수를 넘는 파일을 '과대 파일'로 */
        val largeFileBytes: Int = 60000,
    )

    /** 상/중/하 등급과 품질 게이트 */
    data class Grading(
        /** 제안으로 올릴 최소 등급: HIGH 또는 MEDIUM. LOW 는 항상 버림 */
        val minTier: String = "MEDIUM",
    )

    /** 신규 제안을 이 레포에 PR 로 기록하는 설정 */
    data class Pr(
        /** PR 발행 활성화 여부 (스캔 모드에서만 켬) */
        val enabled: Boolean = false,
        /** PR 을 올릴 대상 레포 "owner/repo" (이 trailhead 레포) */
        val repo: String = "",
        /** PR 의 base 브랜치 */
        val baseBranch: String = "main",
        /** 한 번 실행에서 만들 신규 PR 최대 개수 */
        val maxPerRun: Int = 10,
        /** 동시에 열려 있을 수 있는 미검토 후보 PR 상한. 이만큼 쌓이면 신규 생성 중단 */
        val maxOpen: Int = 5,
        /** true 면 한 레포당 열린 후보 PR 1개로 제한 (비슷한 제안 난립 방지) */
        val onePerRepo: Boolean = true,
        /** 제안 브랜치 접두사 */
        val branchPrefix: String = "proposal",
        /** 제안 파일을 담을 디렉터리 */
        val candidatesDir: String = "candidates",
    )

    /** 승인된 제안의 실제 코드 변경을 Claude API 로 생성하는 설정 (Phase B) */
    data class Ai(
        /** AI 변경 생성 활성화 여부. API 키(ANTHROPIC_API_KEY)가 있을 때만 켠다 */
        val enabled: Boolean = false,
        /** 사용할 모델. 기본은 최신 Opus */
        val model: String = "claude-opus-5-5",
        /** 추론 깊이: LOW/MEDIUM/HIGH/XHIGH/MAX (코드작업이라 HIGH) */
        val effort: String = "HIGH",
        /** 응답 토큰 상한 */
        val maxTokens: Long = 16000,
        /** 이 바이트 수를 넘는 파일은 AI 생성 생략 (단일 파일 국소수정 스코프 가드) */
        val maxFileBytes: Int = 30000,
    )
}
