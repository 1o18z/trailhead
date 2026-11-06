package dev.enzi.trailhead.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/** /search/issues 응답 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchIssuesResponse(
    @JsonProperty("total_count") val totalCount: Int = 0,
    @JsonProperty("incomplete_results") val incompleteResults: Boolean = false,
    val items: List<IssueItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IssueItem(
    val number: Int,
    val title: String,
    val state: String,
    val comments: Int = 0,
    @JsonProperty("html_url") val htmlUrl: String,
    @JsonProperty("repository_url") val repositoryUrl: String,
    @JsonProperty("created_at") val createdAt: String? = null,
    @JsonProperty("updated_at") val updatedAt: String? = null,
    val labels: List<Label> = emptyList(),
    /** 이슈 검색 결과에 PR 이 섞여 들어오면 이 필드가 채워짐 */
    @JsonProperty("pull_request") val pullRequest: Map<String, Any?>? = null,
) {
    /** "https://api.github.com/repos/owner/repo" → "owner/repo" */
    fun repoFullName(): String =
        repositoryUrl.substringAfter("/repos/", "").ifEmpty { "unknown/unknown" }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Label(val name: String = "")

/** /repos/{owner}/{repo} 응답 (필요한 필드만) */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RepoInfo(
    @JsonProperty("full_name") val fullName: String = "",
    @JsonProperty("html_url") val htmlUrl: String = "",
    val language: String? = null,
    @JsonProperty("stargazers_count") val stars: Int = 0,
    @JsonProperty("open_issues_count") val openIssues: Int = 0,
    @JsonProperty("pushed_at") val pushedAt: String? = null,
    val archived: Boolean = false,
    val fork: Boolean = false,
)

/** /repos/{owner}/{repo}/issues/{n}/timeline 의 cross-referenced 이벤트 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TimelineEvent(
    val event: String = "",
    val source: TimelineSource? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TimelineSource(
    val type: String? = null,
    val issue: TimelineIssue? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TimelineIssue(
    val number: Int = 0,
    val title: String = "",
    val state: String = "",
    @JsonProperty("html_url") val htmlUrl: String = "",
    @JsonProperty("pull_request") val pullRequest: PullRequestRef? = null,
) {
    fun isPullRequest(): Boolean = pullRequest != null
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PullRequestRef(
    @JsonProperty("html_url") val htmlUrl: String? = null,
    @JsonProperty("merged_at") val mergedAt: String? = null,
)
