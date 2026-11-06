package dev.enzi.trailhead.github

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.LocalDate
import java.util.Base64

@Component
class GitHubClient(private val github: RestClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 언어·인기·활발 기준으로 레포를 발굴한다. */
    fun searchRepos(language: String, minStars: Int, maxStars: Int, pushedWithinDays: Long, limit: Int): List<Repo> {
        val since = LocalDate.now().minusDays(pushedWithinDays)
        val q = "language:$language stars:$minStars..$maxStars pushed:>=$since archived:false fork:false is:public"
        return try {
            github.get()
                .uri { b ->
                    b.path("/search/repositories")
                        .queryParam("q", q)
                        .queryParam("sort", "stars")
                        .queryParam("order", "desc")
                        .queryParam("per_page", limit.coerceIn(1, 100))
                        .build()
                }
                .retrieve()
                .body(RepoSearchResponse::class.java)
                ?.items ?: emptyList()
        } catch (e: RestClientResponseException) {
            log.warn("searchRepos 실패 [{}]: {} {}", language, e.statusCode, e.responseBodyAsString.take(200))
            emptyList()
        }
    }

    /** 기여 친화도 신호: CONTRIBUTING 가이드 존재 여부 */
    fun hasContributing(owner: String, repo: String): Boolean {
        val paths = listOf("CONTRIBUTING.md", ".github/CONTRIBUTING.md", "docs/CONTRIBUTING.md", "CONTRIBUTING")
        return paths.any { getFileContent(owner, repo, it) != null }
    }

    /** 코드 검색으로 TODO/FIXME 같은 마커가 든 파일을 찾는다. (기본 브랜치, 공개 레포) */
    fun searchCode(repoFullName: String, term: String, limit: Int): List<CodeHit> {
        val q = "\"$term\" repo:$repoFullName"
        return try {
            github.get()
                .uri { b ->
                    b.path("/search/code")
                        .queryParam("q", q)
                        .queryParam("per_page", limit.coerceIn(1, 100))
                        .build()
                }
                .retrieve()
                .body(CodeSearchResponse::class.java)
                ?.items ?: emptyList()
        } catch (e: RestClientResponseException) {
            log.warn("searchCode 실패 [{} / {}]: {}", repoFullName, term, e.statusCode)
            emptyList()
        }
    }

    /** 재귀 트리로 레포의 파일 목록과 크기를 가져온다. (코드 냄새용) */
    fun getTree(owner: String, repo: String, branch: String): TreeResponse? = try {
        github.get()
            .uri { b -> b.path("/repos/{o}/{r}/git/trees/{br}").queryParam("recursive", "1").build(owner, repo, branch) }
            .retrieve()
            .body(TreeResponse::class.java)
    } catch (e: RestClientResponseException) {
        log.warn("getTree 실패 [{}/{}@{}]: {}", owner, repo, branch, e.statusCode)
        null
    }

    /** 파일 내용을 디코드해서 돌려준다. 없으면 null. */
    fun getFileContent(owner: String, repo: String, path: String): String? = try {
        val resp = github.get()
            .uri("/repos/{o}/{r}/contents/{p}", owner, repo, path)
            .retrieve()
            .body(ContentResponse::class.java)
        val raw = resp?.content
        if (raw != null && resp.encoding == "base64") String(Base64.getMimeDecoder().decode(raw)) else raw
    } catch (e: RestClientResponseException) {
        null // 404 등: 파일 없음
    }

    /** 기본 브랜치 최신 커밋 SHA */
    fun getHeadSha(owner: String, repo: String, branch: String): String? = try {
        github.get()
            .uri("/repos/{o}/{r}/commits/{br}", owner, repo, branch)
            .retrieve()
            .body(CommitRef::class.java)
            ?.sha
    } catch (e: RestClientResponseException) {
        log.warn("getHeadSha 실패 [{}/{}@{}]: {}", owner, repo, branch, e.statusCode)
        null
    }

    /** 특정 커밋의 체크런(CI) 목록 */
    fun getCheckRuns(owner: String, repo: String, ref: String): List<CheckRun> = try {
        github.get()
            .uri { b -> b.path("/repos/{o}/{r}/commits/{ref}/check-runs").queryParam("per_page", 100).build(owner, repo, ref) }
            .retrieve()
            .body(CheckRunsResponse::class.java)
            ?.checkRuns ?: emptyList()
    } catch (e: RestClientResponseException) {
        log.warn("getCheckRuns 실패 [{}/{}@{}]: {}", owner, repo, ref, e.statusCode)
        emptyList()
    }
}
