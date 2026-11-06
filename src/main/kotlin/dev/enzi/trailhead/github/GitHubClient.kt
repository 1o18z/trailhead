package dev.enzi.trailhead.github

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.LocalDate

@Component
class GitHubClient(private val github: RestClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 열린 이슈를 라벨/언어로 검색한다. 검색 결과에 섞여 들어오는 PR 은 제외한다.
     */
    fun searchIssues(language: String, label: String, updatedWithinDays: Long, limit: Int): List<IssueItem> {
        val since = LocalDate.now().minusDays(updatedWithinDays)
        val q = buildString {
            append("label:\"").append(label).append("\" ")
            append("language:").append(language).append(" ")
            append("state:open is:issue is:public archived:false ")
            append("no:assignee ")
            append("updated:>=").append(since)
        }
        val perPage = limit.coerceIn(1, 100)
        return try {
            val resp = github.get()
                .uri { b ->
                    b.path("/search/issues")
                        .queryParam("q", q)
                        .queryParam("sort", "updated")
                        .queryParam("order", "desc")
                        .queryParam("per_page", perPage)
                        .build()
                }
                .retrieve()
                .body(SearchIssuesResponse::class.java)
            (resp?.items ?: emptyList()).filter { it.pullRequest == null }
        } catch (e: RestClientResponseException) {
            log.warn("searchIssues 실패 [lang={}, label={}]: {} {}", language, label, e.statusCode, e.responseBodyAsString.take(200))
            emptyList()
        }
    }

    fun getRepo(fullName: String): RepoInfo? {
        val parts = fullName.split("/", limit = 2)
        if (parts.size != 2) return null
        return try {
            github.get()
                .uri("/repos/{owner}/{repo}", parts[0], parts[1])
                .retrieve()
                .body(RepoInfo::class.java)
        } catch (e: RestClientResponseException) {
            log.warn("getRepo 실패 [{}]: {}", fullName, e.statusCode)
            null
        }
    }

    /**
     * 이슈 타임라인에서 cross-referenced 된 PR 들을 모은다. (중복/거절 판단용)
     */
    fun getCrossReferencedPrs(fullName: String, issueNumber: Int): List<TimelineIssue> {
        val (owner, repo) = fullName.split("/", limit = 2).let {
            if (it.size == 2) it[0] to it[1] else return emptyList()
        }
        return try {
            val events = github.get()
                .uri { b ->
                    b.path("/repos/{owner}/{repo}/issues/{n}/timeline")
                        .queryParam("per_page", 100)
                        .build(owner, repo, issueNumber)
                }
                .retrieve()
                .body(Array<TimelineEvent>::class.java)
                ?: emptyArray()
            events
                .filter { it.event == "cross-referenced" }
                .mapNotNull { it.source?.issue }
                .filter { it.isPullRequest() }
        } catch (e: RestClientResponseException) {
            log.warn("timeline 조회 실패 [{}#{}]: {}", fullName, issueNumber, e.statusCode)
            emptyList()
        }
    }
}
