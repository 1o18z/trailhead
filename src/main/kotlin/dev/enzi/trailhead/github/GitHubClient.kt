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
            .uri { b -> b.path("/repos/{o}/{r}/contents").pathSegment(*path.split("/").toTypedArray()).build(owner, repo) }
            .retrieve()
            .body(ContentResponse::class.java)
        val raw = resp?.content
        if (raw != null && resp.encoding == "base64") String(Base64.getMimeDecoder().decode(raw)) else raw
    } catch (e: RestClientResponseException) {
        null // 404 등: 파일 없음
    }

    // ---- 쓰기 (제안을 이 레포에 PR 로 기록) ----

    /** head 가 {owner}:{branch} 인 PR 이 열림/닫힘 통틀어 존재하는지 (중복 판정, 브랜치 삭제돼도 유효) */
    fun pullExistsForHead(owner: String, repo: String, branch: String): Boolean = try {
        val arr = github.get()
            .uri { b ->
                b.path("/repos/{o}/{r}/pulls")
                    .queryParam("head", "$owner:$branch")
                    .queryParam("state", "all")
                    .build(owner, repo)
            }
            .retrieve()
            .body(Array<PullResponse>::class.java)
        (arr?.size ?: 0) > 0
    } catch (e: RestClientResponseException) {
        log.warn("pullExistsForHead 실패 [{}/{}:{}]: {}", owner, repo, branch, e.statusCode)
        false
    }

    /** base 브랜치 sha 에서 새 브랜치를 만든다. 이미 있으면 true 로 간주. */
    fun createBranch(owner: String, repo: String, branch: String, fromSha: String): Boolean = try {
        github.post()
            .uri("/repos/{o}/{r}/git/refs", owner, repo)
            .body(mapOf("ref" to "refs/heads/$branch", "sha" to fromSha))
            .retrieve()
            .toBodilessEntity()
        true
    } catch (e: RestClientResponseException) {
        if (e.statusCode.value() == 422) true // 이미 존재
        else { log.warn("createBranch 실패 [{}/{}:{}]: {}", owner, repo, branch, e.statusCode); false }
    }

    /** 브랜치에 파일을 생성/수정한다. */
    fun putFile(owner: String, repo: String, path: String, branch: String, message: String, content: String): Boolean = try {
        val b64 = Base64.getEncoder().encodeToString(content.toByteArray())
        github.put()
            .uri { b -> b.path("/repos/{o}/{r}/contents").pathSegment(*path.split("/").toTypedArray()).build(owner, repo) }
            .body(mapOf("message" to message, "content" to b64, "branch" to branch))
            .retrieve()
            .toBodilessEntity()
        true
    } catch (e: RestClientResponseException) {
        log.warn("putFile 실패 [{}/{} {}]: {}", owner, repo, path, e.statusCode)
        false
    }

    /** PR 을 만든다. 성공 시 PR URL. */
    fun createPull(owner: String, repo: String, title: String, head: String, base: String, body: String): String? = try {
        github.post()
            .uri("/repos/{o}/{r}/pulls", owner, repo)
            .body(mapOf("title" to title, "head" to head, "base" to base, "body" to body))
            .retrieve()
            .body(PullResponse::class.java)
            ?.htmlUrl
    } catch (e: RestClientResponseException) {
        log.warn("createPull 실패 [{}/{} head={}]: {} {}", owner, repo, head, e.statusCode, e.responseBodyAsString.take(200))
        null
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
