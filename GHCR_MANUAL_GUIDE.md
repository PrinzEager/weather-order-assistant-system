# Weather Order Assistant — GHCR 手動發布與驗收指南

## 本次變更範圍

- **只替換 `.github/workflows/ci.yml`**：保留已通過驗收的 `test-package-docker`，新增 `publish-ghcr` 工作。
- 保留 `push`、`pull_request` 自動 CI；**只有在 `main` 手動執行 `Run workflow` 時才發布 GHCR Image**。
- CI 必須先完成 MySQL 8.4 測試、Maven JAR、Docker Image 建置、JAR Artifact 上傳，GHCR 發布才有機會執行。
- 發布使用「同一次 workflow」的 JAR Artifact，**不會重新跑 Maven**。
- 只會寫入 GitHub Container Registry，不登入或修改 EC2、AWS。

## 第 1 步：安裝補丁

將 ZIP 解壓縮後的 `.github/workflows/ci.yml` **覆蓋專案原有**同名檔案。覆蓋前建議先用 Git 檢查 diff；本補丁是以你上一階段 CI 成功的版本為基礎產生。如果你自行改過原檔，請改為只合併末尾的 `publish-ghcr` 工作，不要覆蓋其餘修改。

Windows PowerShell（專案根目錄）：

```powershell
git diff -- .github/workflows/ci.yml
git add .github/workflows/ci.yml
git commit -m "ci: add manual GHCR publishing after successful tests"
git push origin main
```

這次 `git push` 只會再次觸發原本的 CI，**不會自動將 Image 發布到 GHCR**，因為新增工作的條件是 `workflow_dispatch` 且為 `main`。

## 第 2 步：手動發布

1. GitHub Repository → **Actions** → **Weather Order Assistant - CI**。
2. 點擊右上的 **Run workflow**。
3. 選 **Branch: main**，再按綠色 **Run workflow**。
4. 查看兩個工作：
   - `Maven test + JAR + Docker` → 必須成功。
   - `Publish image to GHCR (manual only)` → 必須成功。
5. 在 `Publish image to GHCR` 的最後一步會看到 `GHCR image published and verified: ghcr.io/owner/repo:sha-...`。
6. 在 GitHub 帳號個人頁面／Repository 連結的 **Packages** 找到剛建立的 Container package（套件建立時預設 private）。

如 `publish-ghcr` 顯示 skipped，請確認本次是手動啟動 (`workflow_dispatch`) 而不是一般 push，並且分支為 `main`。如顯示 `permission_denied`，請先檢查該 Job 的 `permissions: packages: write`，以及 Repo / Package Actions 權限設定。

## 第 3 步：本機拉取驗收（可在 GHCR 發布成功後執行）

GHCR Private 套件需要授權。**GitHub Actions 發布不需要自行申請 PAT**；但若要在自己的 Windows Docker Desktop 拉取 private Image，通常需產生一組 **PAT (classic)**，僅給 `read:packages`，並在本機登入 GHCR。不要把 PAT 貼到聊天室、Commit、環境檔案或 Actions logs。

```powershell
# 將 YOUR_USERNAME 換成 GitHub 帳號。
# 出現 Password 提示時，貼上僅有 read:packages 權限的 PAT (classic)。
docker login ghcr.io -u YOUR_USERNAME

# 把 IMAGE_REFERENCE 換成 Actions 最後一步輸出的完整 Image，例如：
# ghcr.io/owner/repo:sha-40位commitsha
docker pull IMAGE_REFERENCE

docker image inspect IMAGE_REFERENCE
```

如果只是先確認 Registry 發布成功，**可以先只做 GitHub Actions 中的 `Verify image can be pulled from GHCR`**，等到有需要在本機操作時再申請 PAT，這樣更簡單。

### 可選：查看 Image 內容

```powershell
docker image ls ghcr.io/*
```

**不要直接改寫目前本機 `compose.yaml` 或覆蓋正在運行的 `weather-order-assistant:local`。** 本次僅做 Registry 拉取驗證；本機已驗收的 Spring Boot + MySQL 環境仍保持原樣。

## 版本及權限

- Tags：`latest` 與 `sha-<完整 git commit SHA>`；正式 CD 建議固定 SHA Tag，不要用會被覆蓋的 `latest`。
- 上傳：僅用 Actions 內建 `GITHUB_TOKEN`，權限縮到發布工作：`contents: read`、`packages: write`。
- GHCR 新建立的 Package 預設 private；可在 Package settings 確認，不要為了方便拉取而隨意公開內含的應用程式。
- Registry Image 只包含 `Dockerfile` 所 COPY 的 JAR，不會包含 `.env.docker`；但仍應檢查程式碼與 resources **未硬編碼憑證或 API Key**。

## 額度與費用

- 完全不使用 AWS 資源；EC2 保持不動。
- GitHub Docs 目前將 GHCR Container Registry 的容器 Image 儲存及頻寬列為免費，但政策可能調整。GitHub Actions Runner（尤其 private Repo）仍受 GitHub 方案計費與額度限制。
- 為了控制使用量，本次先採 **手動發布** 而不是每次 Push 自動發布。
- 建議在 GitHub → Settings → Billing 檢查當前方案與額度。

## 為什麼先不建立自動 CD？

先確認 GHCR Image 可上傳、可用 SHA 找到、可成功 Pull，再做後面的 EC2 拉取部署和 GitHub Actions CD。目前不需要修改 EC2 規格、Swap、安全群組或 Docker Compose。
