package com.devflow.core.tool;

import com.devflow.common.exception.BusinessException;
import com.devflow.common.model.CodeChange;
import com.devflow.common.utils.JsonUtils;
import com.devflow.infra.persistence.entity.Project;
import com.devflow.infra.persistence.entity.Task;
import com.devflow.infra.persistence.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * GitHub API 工具集
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubTools {

    private final ProjectRepository projectRepository;

    @Value("${github.token:}")
    private String githubToken;

    /**
     * 获取 GitHub 客户端（使用全局默认 token）
     */
    private GitHub getGitHubClient() {
        return getGitHubClient(null);
    }

    /**
     * 获取 GitHub 客户端（优先使用项目级 token，fallback 到全局 token）
     */
    private GitHub getGitHubClient(String projectToken) {
        try {
            String effectiveToken = (projectToken != null && !projectToken.isEmpty())
                    ? projectToken : githubToken;
            if (effectiveToken != null && !effectiveToken.isEmpty()) {
                return GitHub.connectUsingOAuth(effectiveToken);
            }
            return GitHub.connectAnonymously();
        } catch (IOException e) {
            throw new BusinessException("Failed to connect to GitHub", e);
        }
    }

    /**
     * 获取 Issue 详情
     */
    public GHIssue getIssue(String repoUrl, int issueNumber) {
        try {
            GitHub github = getGitHubClient();
            GHRepository repo = github.getRepository(extractRepoName(repoUrl));
            return repo.getIssue(issueNumber);
        } catch (IOException e) {
            throw new BusinessException("Failed to fetch issue: " + issueNumber, e);
        }
    }

    /**
     * 获取项目代码结构
     */
    public String fetchProjectStructure(Long projectId) {
        Project project = projectRepository.getById(projectId);
        if (project == null) {
            throw new BusinessException(404, "Project not found: " + projectId);
        }

        try {
            GitHub github = getGitHubClient(project.getGithubToken());
            GHRepository repo = github.getRepository(extractRepoName(project.getRepoUrl()));

            StringBuilder sb = new StringBuilder();
            sb.append("Project: ").append(project.getName()).append("\n");
            sb.append("Language: ").append(repo.getLanguage()).append("\n\n");
            sb.append("Directory Structure:\n");

            String defaultBranch = repo.getDefaultBranch();
            listDirectoryRecursive(repo, "src", defaultBranch, sb, 0);

            return sb.toString();
        } catch (IOException e) {
            log.warn("Failed to fetch project structure, falling back to basic info", e);
            return "Project: " + project.getName() + " (" + project.getLanguage() + "/" + project.getFramework() + ")";
        }
    }

    private void listDirectoryRecursive(GHRepository repo, String path, String branch,
                                         StringBuilder sb, int depth) throws IOException {
        try {
            GHContent content = repo.getFileContent(path, branch);
            if (content.isDirectory()) {
                for (GHContent child : content.listDirectoryContent()) {
                    sb.append("  ".repeat(depth)).append("- ").append(child.getName());
                    if (child.isDirectory()) {
                        sb.append("/");
                    }
                    sb.append("\n");
                    if (child.isDirectory() && depth < 3) {
                        listDirectoryRecursive(repo, child.getPath(), branch, sb, depth + 1);
                    }
                }
            }
        } catch (Exception e) {
            sb.append("  ".repeat(depth)).append("(unable to read directory)\n");
        }
    }

    /**
     * 获取项目代码风格参考
     */
    public String fetchCodeStyle(Long projectId) {
        Project project = projectRepository.getById(projectId);
        if (project == null) {
            throw new BusinessException(404, "Project not found: " + projectId);
        }

        try {
            GitHub github = getGitHubClient(project.getGithubToken());
            GHRepository repo = github.getRepository(extractRepoName(project.getRepoUrl()));
            String defaultBranch = repo.getDefaultBranch();

            StringBuilder sb = new StringBuilder();
            sb.append("Code style reference (extracted from existing code):\n");
            try {
                GHContent content = repo.getFileContent("src", defaultBranch);
                if (content.isDirectory()) {
                    for (GHContent child : content.listDirectoryContent()) {
                        sb.append("- ").append(child.getPath()).append("\n");
                    }
                }
            } catch (Exception e) {
                sb.append("(Cannot get code style details, please follow Spring Boot standard conventions)\n");
            }

            return sb.toString();
        } catch (IOException e) {
            return "Please follow Spring Boot + MyBatis-Plus standard code conventions";
        }
    }

    /**
     * 创建 Pull Request
     * 处理分支已存在的情况：若分支已存在则先删除再创建（或使用已有分支）
     */
    public String createPullRequest(Task task, String codeChanges, String testCode, String reviewReport) {
        Project project = projectRepository.getById(task.getProjectId());
        if (project == null) {
            throw new BusinessException("Project not found: " + task.getProjectId());
        }

        try {
            GitHub github = getGitHubClient(project.getGithubToken());
            GHRepository repo = github.getRepository(extractRepoName(project.getRepoUrl()));
            String defaultBranch = repo.getDefaultBranch();

            // 创建特性分支（处理分支已存在的情况）
            String branchName = "devflow/issue-" + task.getIssueNumber();
            createOrResetBranch(repo, branchName, defaultBranch);

            // 提交代码文件
            CodeChange codeChangeObj = safeParseCodeChange(codeChanges);
            if (codeChangeObj != null && codeChangeObj.getFiles() != null) {
                for (CodeChange.FileContent file : codeChangeObj.getFiles()) {
                    commitFileToBranch(repo, branchName, file, "feat");
                }
            }

            // 提交测试文件
            CodeChange testChangeObj = safeParseCodeChange(testCode);
            if (testChangeObj != null && testChangeObj.getFiles() != null) {
                for (CodeChange.FileContent file : testChangeObj.getFiles()) {
                    commitFileToBranch(repo, branchName, file, "test");
                }
            }

            // 检查是否存在未合并的 PR
            task.setPrBranch(branchName);

            // 尝试创建 PR（如果已存在同分支 PR 则返回已有 PR URL）
            GHPullRequest pr = findExistingPrOrCreate(repo, branchName, defaultBranch, task, reviewReport);
            String prUrl = pr.getHtmlUrl().toString();
            task.setPrUrl(prUrl);

            log.info("PR created/found: {}", prUrl);
            return prUrl;
        } catch (IOException e) {
            throw new BusinessException("Failed to create PR", e);
        }
    }

    /**
     * 创建或重置分支：如果分支已存在则强制重置到主分支的最新提交
     */
    private void createOrResetBranch(GHRepository repo, String branchName, String defaultBranch) throws IOException {
        GHRef mainRef = repo.getRef("heads/" + defaultBranch);
        String mainSha = mainRef.getObject().getSha();

        try {
            GHRef existingRef = repo.getRef("heads/" + branchName);
            log.info("Branch {} already exists, resetting to {} ({})", branchName, defaultBranch, mainSha);
            existingRef.updateTo(mainSha, true);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Not Found")) {
                log.info("Creating new branch: {} from {}", branchName, defaultBranch);
                repo.createRef("refs/heads/" + branchName, mainSha);
            } else {
                throw e;
            }
        }
    }

    /**
     * 查找已有 PR 或创建新 PR
     */
    private GHPullRequest findExistingPrOrCreate(GHRepository repo, String branchName, String defaultBranch,
                                                   Task task, String reviewReport) throws IOException {
        // 检查是否有已存在的 open PR 指向同一分支
        List<GHPullRequest> existingPRs = repo.queryPullRequests()
                .head(repo.getOwnerName() + ":" + branchName)
                .state(GHIssueState.OPEN)
                .list().toList();

        if (!existingPRs.isEmpty()) {
            log.info("Found existing PR for branch {}: {}", branchName, existingPRs.get(0).getHtmlUrl());
            return existingPRs.get(0);
        }

        // 创建新 PR
        String prTitle = "feat: " + task.getIssueTitle() + " (Closes #" + task.getIssueNumber() + ")";
        String prBody = buildPRBody(task, reviewReport);
        return repo.createPullRequest(prTitle, branchName, defaultBranch, prBody);
    }

    /**
     * 将单个文件提交到分支，处理文件已存在的情况
     */
    private void commitFileToBranch(GHRepository repo, String branchName,
                                     CodeChange.FileContent file, String prefix) {
        try {
            String base64Content = Base64.getEncoder().encodeToString(
                    file.getContent().getBytes());
            repo.createContent()
                    .branch(branchName)
                    .path(file.getFilePath())
                    .message(prefix + ": " + file.getFilePath())
                    .content(base64Content)
                    .commit();
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("already exists") || msg.contains("Invalid path")) {
                log.info("File already exists, updating: {}", file.getFilePath());
                try {
                    String base64Content = Base64.getEncoder().encodeToString(
                            file.getContent().getBytes());
                    GHContent existing = repo.getFileContent(file.getFilePath(), branchName);
                    existing.update(base64Content, prefix + "(update): " + file.getFilePath(), branchName);
                } catch (IOException ex2) {
                    log.warn("Failed to update existing file: {}", file.getFilePath(), ex2);
                }
            } else {
                log.warn("Failed to create file {}: {}", file.getFilePath(), msg);
            }
        }
    }

    /**
     * 安全解析 LLM 生成的代码变更 JSON
     */
    private CodeChange safeParseCodeChange(String json) {
        if (json == null || json.isBlank()) {
            log.warn("Code change JSON is null or blank");
            return null;
        }
        try {
            return JsonUtils.fromJson(json, CodeChange.class);
        } catch (Exception e) {
            log.warn("Failed to parse code changes JSON: {}. Attempting recovery...",
                    e.getMessage().length() > 200 ? e.getMessage().substring(0, 200) : e.getMessage());
            return null;
        }
    }

    private String buildPRBody(Task task, String reviewReport) {
        StringBuilder sb = new StringBuilder();
        sb.append("## DevFlow Agent Auto-generated PR\n\n");
        sb.append("### Issue\n").append("#").append(task.getIssueNumber())
                .append(": ").append(task.getIssueTitle()).append("\n\n");
        sb.append("### Requirements\n").append(task.getIssueBody() != null ? task.getIssueBody() : "N/A").append("\n\n");
        sb.append("### Review Report\n").append(reviewReport != null ? reviewReport : "No review report").append("\n\n");
        sb.append("---\n*This PR was automatically generated by DevFlow Agent*");
        return sb.toString();
    }

    private String extractRepoName(String repoUrl) {
        return repoUrl.replace(".git", "").replace("https://github.com/", "");
    }
}