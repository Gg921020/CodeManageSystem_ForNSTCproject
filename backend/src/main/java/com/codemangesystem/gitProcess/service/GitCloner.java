package com.codemangesystem.gitProcess.service;

import com.codemangesystem.gitProcess.model_Data.Files;
import com.codemangesystem.gitProcess.model_Git.GitResult;
import com.codemangesystem.gitProcess.model_Git.GitStatus;
import com.codemangesystem.gitProcess.model_Repo.RepoINFO;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 處理有關 Git clone 的操作
 */
// TODO: 遇上分支上的問題
@Slf4j
@Service
public class GitCloner {
    // clone 存放的檔案位置
    private static final String CLONE_LOCAL_BASE_PATH = "src/cloneCode/";

    private final GitDiffAnalyzer gitDiffAnalyzer;
    private final GitPuller gitPuller;

    @Autowired
    public GitCloner(GitDiffAnalyzer gitDiffAnalyzer, GitPuller gitPuller) {
        this.gitDiffAnalyzer = gitDiffAnalyzer;
        this.gitPuller = gitPuller;
    }

    // TODO: 使用者 GitHub 的權限

    /**
     * 判斷儲存庫是否需要 clone 到本地資料夾，並回傳最終儲存庫存放的路徑
     */
    public GitResult cloneRepository(String repoUrl, String commitId) throws GitAPIException, IOException {
        RepoINFO repoINFO = RepoINFO.builder()
                                    .repoName(GitFunction.getRepoNameFromUrl(repoUrl))
                                    .localPath(CLONE_LOCAL_BASE_PATH + GitFunction.getRepoNameFromUrl(repoUrl))
                                    .build();

        log.info("當前 repoINFO path : {}  name : {}", repoINFO.localPath, repoINFO.repoName);
        try {
            // 如果本地資料夾已經存在， pull 更新本地端資料並且直接回傳 GitResult
            if (GitFunction.isCloned(repoINFO.localPath)) {
                log.info("Repository already exists at: {}", repoINFO.localPath);
                return gitPuller.renewLocalRepository(repoINFO);
            }

            log.info("Cloning to {}", repoUrl);

            CloneCommand command = Git.cloneRepository()
                                      .setURI(repoUrl)
                                      .setDirectory(new File(repoINFO.localPath));
            // 未來會用到的使用者資訊加入
            // UsernamePasswordCredentialsProvider user = new UsernamePasswordCredentialsProvider(login, password);
            // clone.setCredentialsProvider(user);
            // clone.call()

            // 將資料 clone 下來，try 達到 close
            // 只是要透過 Git 物件將資料 clone 下來
            // clone 成功接續將資料分類存入資料庫內
            try (Git git = command.call()) {

                // 當有指定的 commitId
                if (!commitId.equals("HEAD")) {
                    checkToCommitId(git, commitId);
                }

                log.info("成功 clone: {}", repoINFO.localPath);
                log.info("嘗試分類 -> gitDiffAnalyzer");

                // 執行分析專案
                List<Files> analyzedFiles = gitDiffAnalyzer.analyzeAllCommits(repoINFO.localPath);

                log.info("成功將資料分類完成");

                // 這不代表是錯誤，可能是專案非 Java 檔案
                if (analyzedFiles == null || analyzedFiles.isEmpty()) {
                    log.warn("No files were analyzed in the repository: {}", repoINFO.localPath);
                    return GitResult.builder()
                                    .status(GitStatus.ANALYSIS_FAILED)
                                    .message("No files were analyzed in the repository: " + repoINFO.localPath
                                            + " 可能是沒有 method 可以分類")
                                    .build();
                }

                return GitResult.builder()
                                .status(GitStatus.CLONE_SUCCESS)
                                .message("成功將資料分類完成")
                                .build();
            }
        } catch (GitAPIException | RevisionSyntaxException | IOException e) {
            log.error("Failed clone to {}", repoUrl, e);
            return GitResult.builder()
                            .status(GitStatus.CLONE_FAILED)
                            .message("Failed to clone " + e)
                            .build();
        }
    }

    // 切換到指定的 commitId，遇上問題會直接拋出例外
    public void checkToCommitId(Git git, String commitId) throws RevisionSyntaxException, IOException, GitAPIException {
        ObjectId specifyCommit = git.getRepository()
                                    .resolve(commitId);
        // 指定的 commitId 不存在
        if (specifyCommit == null) {
            log.error("Commit {} not found in repository", commitId);
            throw new IllegalArgumentException("指定的 commitId 不存在");
        }

        git.checkout()
           .setName(specifyCommit.getName())
           .call();
        log.info("成功 checked out commit: {}", commitId);
    }
}
