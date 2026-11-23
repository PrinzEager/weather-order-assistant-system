# weather-order-assistant-system：GitHub Actions CI 第一次設定

> 範圍：GitHub Actions 進行 Java 21 + Maven 測試、JAR 打包、Docker Image 建置。**不登入 AWS、不連接 EC2、不部署或上傳 Docker Image 到外部 Registry**。

## 0. 專案比對

此流程依照上傳版本的實際檔案建立：

- `pom.xml`：Java 21，Spring Boot 4.1.1，Maven Wrapper。
- `src/test/java/.../WeatherOrderAssistantSystemApplicationTests.java`：`@SpringBootTest` 會啟動 Spring Context。
- `src/main/resources/application.properties`：必須有 `DB_USERNAME`、`DB_PASSWORD`、`CWA_API_KEY`；預設使用 Flyway、MySQL。
- `Dockerfile`：執行 `COPY target/weather-order-assistant-system-0.0.1-SNAPSHOT.jar /app/app.jar`。
- 現有 `.gitignore` 不會忽略拼錯的 `.env.exapmle`，**請先處理**，尤其日後檔案若填入真實密碼不可推送到 GitHub。

## 1. 複製補丁到專案

解壓縮 CI 補丁，將 `.github/workflows/ci.yml` 放在專案根目錄既有 `.github` 資料夾之下，注意 Windows 檔案總管需顯示隱藏項目。不要刪除原有 `.github/modernize` 等資料。

如果你本機尚未保留上一階段本機 Docker 補丁，先確認以下三個檔案都在與 `pom.xml` 相同的目錄：`Dockerfile`、`.dockerignore`、`compose.yaml`。本次只讀取 `Dockerfile` 和 `.dockerignore`，不使用 `.env.docker`、不啟動本機 Compose。

**請在既有 `.gitignore` 補上以下一行**（不必覆蓋整份既有設定）：

```gitignore
.env.exapmle
```

有 `.env.example`、`.env.docker.example` 等模板時，確認它們完全沒有真實密碼或 API Key，再提交。

## 2. 推送到 GitHub 前先檢查

在 Windows PowerShell 專案根目錄：

```powershell
git status --short
git remote -v
git branch --show-current
```

如果尚未有 Git Repository：

```powershell
git init
git branch -M main
```

若 `git remote -v` 沒有顯示 `origin`，**先在 GitHub 建立一個空白 Repository**，再將下方 URL 改成自己實際的 Repository 地址（不要照抄 `<...>`）：

```powershell
git remote add origin https://github.com/你的帳號/你的Repository名稱.git
```

**先檢查保密檔案是否被追蹤**：

```powershell
git status --short
git ls-files .env .env.docker .env.exapmle
```

`git ls-files` 正常應沒有列出這些私密檔案。若已被 Git 追蹤，請用 `git rm --cached -- 檔案名稱` 停止追蹤（不刪除本機檔案）。若敏感資料已經推送到遠端，還需更換相應密碼/API Key；只新增 `.gitignore` 並不能消除已公開的歷史。

完成檢查後，先新增本次 CI 設定及必要的 Dockerfile。**不要使用 `git add -f` 強制提交環境檔案**。

若專案尚未上傳 GitHub，可採取：

```powershell
git add .
git status --short
git commit -m "ci: add Maven MySQL Docker validation"
git push -u origin main
```

若原本已經是 Git Repository，請依現有分支與 origin 設定操作，不要重複 `git init` 或覆蓋現有內容；如果主分支不是 `main`，請把 `ci.yml` 的觸發分支調整為實際名稱。

## 3. 到 GitHub 驗收

1. 開啟你的 Repository。
2. 點選 **Actions**。
3. 點選左側 **Weather Order Assistant - CI**。
4. 點開最新一次執行的紀錄。
5. 確認 Maven test、Verify JAR、Build Docker image、Upload JAR artifact 均成功。
6. 在成功的 workflow run 詳細頁面，找到 **Artifacts → weather-order-assistant-jar**，可下載 JAR ZIP（預設保留 3 天）。

只會在 `main` 分支 Push、對 `main` 提出 Pull Request，或手動執行時觸發。沒有設定 EC2 部署。

## 4. 常見錯誤

| 錯誤位置 | 常見原因 | 先做什麼 |
|---|---|---|
| Maven：database connection | MySQL service 尚未 ready、環境變數不符 | 檢查 MySQL service logs / `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` |
| Maven：Flyway validation | Migration SQL / schema 和 CI MySQL 不相容 | 檢查失敗的 Migration 檔案、版本與例外訊息 |
| Maven：`CWA_API_KEY` | 缺少必要的 Weather API 設定 | 確認 workflow 的 `CWA_API_KEY` 測試占位值存在；不要填入正式 Key |
| Docker：`COPY ...jar` | 沒有產生正確 JAR、檔名不符、`.dockerignore` 排除了 JAR | 檢查 Maven 打包紀錄與 `target` 檔名 |
| Actions 未啟動 | workflow 不在 `.github/workflows`，或分支不是 main | 檢查提交路徑和 `.yml` 的觸發分支 |

CI 的自動化測試並不等於已驗證 Docker 容器內的完整登入流程；這部分已在本機手動驗收。此 CI 會測試專案與產生 Image，但不會在 GitHub Runner 上啟動完整網站。

## 5. 費用提醒

- 不會使用你的 EC2、EBS 或 AWS 金鑰，因此本流程不增加 AWS 部署資源。
- GitHub **公開 Repository 使用標準 GitHub-hosted runner** 通常不收 Actions 運算費；**私人 Repository** 則需受方案包含的運算與儲存額度限制。
- CI 設定 `timeout-minutes: 20`、`concurrency.cancel-in-progress: true`、Artifact 保存 3 天，是為了降低不必要的運算與儲存使用量；不代表絕對零費用。
- 若不希望每次 Push 自動跑，把 `ci.yml` 的 `push`、`pull_request` 兩個事件區塊拿掉，只留下 `workflow_dispatch`，改成手動執行即可。
- 在 GitHub Settings / Billing & licensing 中確認額度與支出上限後，再大量使用。

## 6. 本階段驗收完成後才進入 CD

未來要部署 EC2 時，再獨立加入 deployment workflow。正式 CD 需討論 Image Registry、憑證、資料庫備份、環境變數、HTTPS、回滾與額度；目前不修改 EC2。
