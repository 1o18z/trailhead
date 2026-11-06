package dev.enzi.trailhead.pr

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import dev.enzi.trailhead.ai.ChangeGenerator
import dev.enzi.trailhead.config.TrailheadProperties
import dev.enzi.trailhead.github.GitHubClient
import dev.enzi.trailhead.proposal.Proposal
import dev.enzi.trailhead.proposal.ProposalType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** 승인 전에 미리 만들어 둔 실제 변경 (fork 브랜치에 커밋 완료). */
data class Contribution(
    /** upstream PR 의 head = "forkOwner:branch" */
    val forkHead: String,
    val filePath: String,
    val summary: String,
    /** 대상 레포 기준 비교 뷰 (GitHub 이 diff 렌더) */
    val compareUrl: String,
    /** 후보 PR 본문에 넣을 unified diff (과대 시 잘림) */
    val diff: String,
)

/**
 * 제안을 실제 코드 변경으로 구현한다 (Phase B, 승인 전 단계).
 * 대상 레포 파일을 읽어 Claude 로 수정 → 대상 레포를 fork → fork 브랜치에 커밋.
 * 아직은 **단일 파일 국소 수정(BUG_FIX/REFACTOR)** 만 자동 구현한다.
 */
@Service
class ContributionService(
    private val client: GitHubClient,
    private val generator: ChangeGenerator,
    private val props: TrailheadProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun enabled(): Boolean = generator.enabled()

    fun implement(p: Proposal, branch: String): Contribution? {
        if (!generator.enabled()) return null
        val path = p.filePath ?: return null
        if (p.type != ProposalType.BUG_FIX && p.type != ProposalType.REFACTOR) {
            log.info("자동 구현 대상 아님(유형 {}): {}", p.type, p.repoFullName)
            return null
        }
        val (owner, name) = p.repoFullName.split("/", limit = 2).takeIf { it.size == 2 } ?: return null

        // 1) 대상 파일 원본 확보
        val original = client.getFileContentAndSha(owner, name, path)?.first ?: run {
            log.warn("대상 파일 조회 실패: {}:{}", p.repoFullName, path); return null
        }

        // 2) Claude 로 변경 생성 (불확신이면 null)
        val change = generator.generate(p.repoFullName, path, original, p.title) ?: return null
        if (change.newContent.trimEnd() == original.trimEnd()) {
            log.info("AI 변경이 원본과 동일 — 생략: {}:{}", p.repoFullName, path); return null
        }

        // 3) 대상 레포 fork + 준비 대기
        val fork = client.forkRepo(owner, name) ?: return null
        val forkOwner = fork.ownerAndName()?.first ?: return null
        val defaultBranch = fork.defaultBranch
        if (!client.waitForForkReady(forkOwner, name, defaultBranch)) return null

        // 4) fork 기본 브랜치 head 에서 작업 브랜치 생성
        val forkHeadSha = client.getHeadSha(forkOwner, name, defaultBranch) ?: return null
        if (!client.createBranch(forkOwner, name, branch, forkHeadSha)) return null

        // 5) 변경을 fork 브랜치에 커밋 (기존 파일이라 blob sha 필요)
        val sha = client.getFileContentAndSha(forkOwner, name, path)?.second
        val commitMsg = "${commitSubject(p)}\n\n${change.summary}"
        if (!client.putFile(forkOwner, name, path, branch, commitMsg, change.newContent, sha)) return null

        val compareUrl = "https://github.com/$owner/$name/compare/$defaultBranch...$forkOwner:$branch"
        log.info("변경 커밋 완료: {}:{} @ {}:{}", p.repoFullName, path, forkOwner, branch)
        return Contribution(
            forkHead = "$forkOwner:$branch",
            filePath = path,
            summary = change.summary,
            compareUrl = compareUrl,
            diff = unifiedDiff(path, original, change.newContent),
        )
    }

    private fun commitSubject(p: Proposal): String {
        val verb = if (p.type == ProposalType.BUG_FIX) "fix" else "refactor"
        return "$verb: ${p.title.take(60)}"
    }

    private fun unifiedDiff(path: String, old: String, new: String): String {
        val oldLines = old.lines()
        val patch = DiffUtils.diff(oldLines, new.lines())
        val unified = UnifiedDiffUtils.generateUnifiedDiff("a/$path", "b/$path", oldLines, patch, 3)
        val text = unified.joinToString("\n")
        return if (text.length > MAX_DIFF_CHARS) text.take(MAX_DIFF_CHARS) + "\n… (diff 생략, 전체는 compare 링크 참고)" else text
    }

    companion object {
        private const val MAX_DIFF_CHARS = 12000
    }
}
