package com.example.codemangesystem;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class GitCloner {
    private static final Logger logger = LoggerFactory.getLogger(GitCloner.class);
    private static final String DEFAULT_BRANCH = "main";

    // 將儲存庫複製到本地資料夾，並按照commit時間做分類，並回傳最終的路徑
    // 可能丟出 GitAPIException(Git操作錯誤) 和 IOException(檔案操作錯誤)
    public String cloneRepository(String repoUrl) throws GitAPIException, IOException {
        String repoName = getRepoNameFromUrl(repoUrl);
        String localPath = "src/cloneCode/" + repoName;
        // 先嘗試複製儲存庫至臨時的資料夾 -> 取得commit的時間 -> 移動資料夾至最終路徑 -> 回傳最終路徑
        try (Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(localPath))
                .call()) {
            // 取得最新提交的時間戳
            String timestamp = getCommitTimestamp(localPath);
            String finalPath = "src/cloneCode/" + repoName + "_" + timestamp;

            Path sourcePath = Paths.get(localPath);
            Path targetPath = Paths.get(finalPath);
            Files.move(sourcePath, targetPath);

            logger.info("Repository cloned to: {}", finalPath);
            return finalPath;
        } finally {
            // 最後刪除臨時的資料夾
            deleteDirectory(new File(localPath));
        }
    }

    // 從儲存庫網址中取得專案名稱
    private String getRepoNameFromUrl(String repoUrl) {
        // 將網址透過"/"分開
        String[] parts = repoUrl.split("/");
        // 抓取最後面的專案名稱
        String repoNameWithExtension = parts[parts.length - 1];
        // .git的部分換成 ""
        return repoNameWithExtension.replace(".git", "");
    }

    // 取得最新提交的時間戳
    private String getCommitTimestamp(String localPath) throws IOException {
        // 先將本地儲存庫打開 -> 取得儲存庫物件 -> 取得HEAD的commit ID -> 透過RevWalk取得RevCommit -> 轉換成Instant -> 格式化時間
        try (Git git = Git.open(new File(localPath))) {
            Repository repository = git.getRepository();
            // 取得HEAD的commit ID
            ObjectId headCommitId = repository.resolve(DEFAULT_BRANCH);
            try (RevWalk revWalk = new RevWalk(repository)) {
                //RevCommit 包含了提交的作者、訊息、時間戳等資訊
                RevCommit commit = revWalk.parseCommit(headCommitId);
                // Git 儲存庫中的提交時間戳是 UTC 時間 (從 1970-01-01T00:00:00Z 開始的秒數)，避免掉不同時區的問題
                // 取得commit時間並轉換成Instant(UTC時間)
                Instant commitTime = Instant.ofEpochSecond(commit.getCommitTime());
                //將Instant轉換成Zone系統預設時區的時間，並格式化成yyyyMMdd_HHmmss字串
                return commitTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            }
        }
    }

    private void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        //刪除目錄內的所有檔案
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        //刪除目錄
        boolean judge = directoryToBeDeleted.delete();
        if (!judge) {
            // 刪除失敗，進行錯誤處理
            logger.warn("Failed to delete directory: {}", directoryToBeDeleted.getAbsolutePath());
        }
    }
}
