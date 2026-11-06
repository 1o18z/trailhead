package dev.enzi.trailhead.discovery

import dev.enzi.trailhead.config.TrailheadProperties
import dev.enzi.trailhead.github.GitHubClient
import dev.enzi.trailhead.github.Repo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** 기여할 만한 레포를 발굴한다: 내 언어 + 인기/활발 + (옵션) 기여 친화. */
@Service
class RepoDiscoveryService(
    private val client: GitHubClient,
    private val props: TrailheadProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun discover(): List<Repo> {
        val cfg = props.discovery
        // 언어별로 별 많은 순 레포를 모아 중복 제거
        val pool = cfg.languages.flatMap { lang ->
            client.searchRepos(lang, cfg.minStars, cfg.maxStars, cfg.pushedWithinDays, cfg.maxRepos)
        }.distinctBy { it.fullName }

        // 기여 친화(CONTRIBUTING) 필터는 레포당 API 를 쓰므로, maxRepos 채울 때까지만 검사
        val selected = ArrayList<Repo>(cfg.maxRepos)
        for (repo in pool.sortedByDescending { it.stars }) {
            if (selected.size >= cfg.maxRepos) break
            val (owner, name) = repo.ownerAndName() ?: continue
            if (cfg.requireContributing && !client.hasContributing(owner, name)) continue
            selected += repo
        }
        log.info("레포 발굴: 풀 {}개 → 선정 {}개", pool.size, selected.size)
        return selected
    }
}
