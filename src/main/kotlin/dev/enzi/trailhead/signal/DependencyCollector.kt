package dev.enzi.trailhead.signal

import dev.enzi.trailhead.config.TrailheadProperties
import dev.enzi.trailhead.github.GitHubClient
import dev.enzi.trailhead.github.Repo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 오래된/취약 의존성 수집기 (자리만 잡아둠).
 *
 * 정직하게: "오래됨/취약함" 을 믿을 만하게 판정하려면 외부 레지스트리(Maven Central, npm)의
 * 최신 버전 또는 GitHub Advisory 데이터와 대조해야 한다. 그 연동 전까지는 억지로 LOW 제안을
 * 만들지 않기 위해 아무 신호도 내지 않는다. (매니페스트 존재 여부만 로깅)
 */
@Component
class DependencyCollector(
    private val client: GitHubClient,
    private val props: TrailheadProperties,
) : SignalCollector {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun enabled() = props.signals.dependencyEnabled
    override fun name() = "dependency"

    override fun collect(repo: Repo): List<RawSignal> {
        val (owner, name) = repo.ownerAndName() ?: return emptyList()
        val manifest = MANIFESTS.firstOrNull { client.getFileContent(owner, name, it) != null }
        if (manifest != null) {
            log.debug("[{}] 매니페스트 발견: {} (버전 비교 연동 전이라 제안 미생성)", repo.fullName, manifest)
        }
        // TODO(phase-a.2): 레지스트리/Advisory 대조로 outdated·vulnerable 판정 후 MEDIUM/HIGH 신호 생성
        return emptyList()
    }

    companion object {
        private val MANIFESTS = listOf(
            "build.gradle", "build.gradle.kts", "pom.xml",
            "package.json", "requirements.txt", "go.mod",
        )
    }
}
