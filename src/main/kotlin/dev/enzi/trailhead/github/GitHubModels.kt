package dev.enzi.trailhead.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// ---- 레포 검색 / 정보 ----

@JsonIgnoreProperties(ignoreUnknown = true)
data class RepoSearchResponse(
    @JsonProperty("total_count") val totalCount: Int = 0,
    val items: List<Repo> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Repo(
    @JsonProperty("full_name") val fullName: String = "",
    @JsonProperty("html_url") val htmlUrl: String = "",
    val language: String? = null,
    @JsonProperty("stargazers_count") val stars: Int = 0,
    @JsonProperty("open_issues_count") val openIssues: Int = 0,
    @JsonProperty("pushed_at") val pushedAt: String? = null,
    @JsonProperty("default_branch") val defaultBranch: String = "main",
    val archived: Boolean = false,
    val fork: Boolean = false,
) {
    fun ownerAndName(): Pair<String, String>? =
        fullName.split("/", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
}

// ---- 코드 검색 (TODO/FIXME 등) ----

@JsonIgnoreProperties(ignoreUnknown = true)
data class CodeSearchResponse(
    @JsonProperty("total_count") val totalCount: Int = 0,
    val items: List<CodeHit> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CodeHit(
    val path: String = "",
    @JsonProperty("html_url") val htmlUrl: String = "",
)

// ---- Git 트리 (코드 냄새: 과대 파일) ----

@JsonIgnoreProperties(ignoreUnknown = true)
data class TreeResponse(
    val tree: List<TreeEntry> = emptyList(),
    val truncated: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TreeEntry(
    val path: String = "",
    val type: String = "",
    val size: Long? = null,
)

// ---- 파일 내용 (의존성 매니페스트) ----

@JsonIgnoreProperties(ignoreUnknown = true)
data class ContentResponse(
    val path: String = "",
    val sha: String? = null,
    val encoding: String? = null,
    val content: String? = null,
    @JsonProperty("html_url") val htmlUrl: String? = null,
)

// ---- 커밋 / CI 상태 ----

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommitRef(
    val sha: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CheckRunsResponse(
    @JsonProperty("total_count") val totalCount: Int = 0,
    @JsonProperty("check_runs") val checkRuns: List<CheckRun> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CheckRun(
    val name: String = "",
    val status: String = "",
    val conclusion: String? = null,
    @JsonProperty("html_url") val htmlUrl: String? = null,
)

// ---- PR 생성 / 조회 ----

@JsonIgnoreProperties(ignoreUnknown = true)
data class PullResponse(
    val number: Int = 0,
    @JsonProperty("html_url") val htmlUrl: String = "",
    val body: String? = null,
    val head: PullRef? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PullRef(
    val ref: String = "",
)
